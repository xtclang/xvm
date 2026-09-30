package org.xtclang.idea.lsp

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VFileProperty
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.NewVirtualFile
import com.intellij.util.concurrency.AppExecutorUtil
import com.redhat.devtools.lsp4ij.JSONUtils
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import org.eclipse.lsp4j.DidChangeWatchedFilesRegistrationOptions
import org.eclipse.lsp4j.Registration

/** One connection owns its watch leases; duplicate registrations share a native watch. */
internal class CompilerRootWatches(
    private val watch: (Set<Path>) -> AutoCloseable,
    private val refresh: (Set<Path>) -> CompletableFuture<Void>,
) : AutoCloseable {
    private data class State(
        val registrations: Map<String, Set<Path>> = emptyMap(),
        val lease: AutoCloseable = AutoCloseable {},
        val closed: Boolean = false,
    ) {
        val roots = registrations.values.flatten().toSet()
    }

    private var state = State()
    private val refreshing = AtomicBoolean()

    @Synchronized
    fun replace(added: Map<String, Set<Path>>, removed: Set<String> = emptySet()) {
        if (state.closed) return
        val registrations = (state.registrations - removed) + added
        val roots = registrations.values.flatten().toSet()
        val lease = if (roots == state.roots) state.lease else watch(roots)
        if (lease !== state.lease) state.lease.close()
        state = State(registrations, lease)
    }

    fun refresh() {
        val roots = synchronized(this) { if (state.closed) emptySet() else state.roots }
        if (roots.isEmpty() || !refreshing.compareAndSet(false, true)) return
        try {
            refresh(roots).whenComplete { _, _ -> refreshing.set(false) }
        } catch (failure: RuntimeException) {
            refreshing.set(false)
            throw failure
        }
    }

    @Synchronized
    override fun close() {
        state.lease.close()
        state = State(closed = true)
    }

    companion object {
        /** Only explicit compiler roots, never a workspace-wide wildcard or arbitrary URI. */
        fun roots(registration: Registration): Set<Path> {
            if (
                registration.method != "workspace/didChangeWatchedFiles" ||
                    !registration.id.startsWith("xtc-resources-")
            )
                return emptySet()
            val options =
                JSONUtils.toModel(
                    registration.registerOptions,
                    DidChangeWatchedFilesRegistrationOptions::class.java,
                )
            return options.watchers
                .mapNotNull { watcher ->
                    val pattern = watcher.globPattern
                    runCatching {
                        if (pattern.isRight) {
                            val base = pattern.right.baseUri
                            Path.of(URI(if (base.isLeft) base.left.uri else base.right))
                        } else {
                            pattern.left
                                .takeIf { it.endsWith("/**/*") }
                                ?.removeSuffix("/**/*")
                                ?.let(Path::of)
                                ?.takeIf(Path::isAbsolute)
                        }
                    }
                        .getOrNull()
                }
                .toSet()
        }
    }
}

/**
 * Native watching marks VFS entries dirty but does not refresh while the IDE stays focused. Refresh
 * just these compiler roots on the shared scheduler. No new thread or OS watcher is created per
 * root. Unknown directories must be loaded for VFS to emit child create events.
 */
// TODO LSP4IJ: dynamic watcher registrations need owned VFS roots and refresh while focused.
// Remove this bridge when upstream covers unknown/missing external roots and disposal (X124).
internal class CompilerVfsWatches : Disposable {
    private val files = LocalFileSystem.getInstance()
    private val roots =
        CompilerRootWatches(
            watch = { paths ->
                val parents = paths.mapNotNull { path ->
                    generateSequence(path.parent) { it.parent }
                        .firstOrNull(Files::isDirectory)
                        ?.toString()
                }
                val requests =
                    files.replaceWatchedRoots(emptySet(), paths.map(Path::toString), parents)
                AutoCloseable { files.removeWatchedRoots(requests) }
            },
            refresh = ::refresh,
        )
    private val timer =
        AppExecutorUtil.getAppScheduledExecutorService()
            .scheduleWithFixedDelay(
                {
                    runCatching(roots::refresh).onFailure {
                        logger<CompilerVfsWatches>().warn("Cannot refresh compiler paths", it)
                    }
                },
                0,
                2,
                TimeUnit.SECONDS,
            )

    fun register(registrations: List<Registration>) {
        roots.replace(
            registrations
                .associate { it.id to CompilerRootWatches.roots(it) }
                .filterValues { it.isNotEmpty() }
        )
        roots.refresh()
    }

    fun unregister(ids: Set<String>) = roots.replace(emptyMap(), ids)

    private fun refresh(paths: Set<Path>): CompletableFuture<Void> {
        val directories = paths.mapNotNull { path ->
            // Missing generated roots are observed through their nearest existing parent. Never
            // recurse into that ancestor (which can be a large unrelated directory).
            val existing = generateSequence(path) { it.parent }.firstOrNull(Files::isDirectory)
            existing?.let(files::refreshAndFindFileByNioFile)?.let { it to (existing == path) }
        }
        ReadAction.runBlocking<RuntimeException> {
            directories.forEach { (file, recursive) ->
                load(file, recursive)
                // A still-missing nested root can have a different nearest ancestor next time.
                // Probe only that directory's children until the recursive root exists.
                if (!recursive) (file as? NewVirtualFile)?.markDirty()
            }
        }
        val result = CompletableFuture<Void>()
        val groups = directories.groupBy({ it.second }, { it.first })
        CompletableFuture.allOf(
                *groups
                    .map { (recursive, group) ->
                        CompletableFuture<Void>().also { done ->
                            files.refreshFiles(group, true, recursive) { done.complete(null) }
                        }
                    }
                    .toTypedArray()
            )
            .whenComplete { _, error ->
                if (error == null) result.complete(null) else result.completeExceptionally(error)
            }
        return result
    }

    private fun load(file: VirtualFile, recursive: Boolean) {
        if (!file.isValid || !file.isDirectory || file.`is`(VFileProperty.SYMLINK)) return
        val children = file.children
        if (recursive) children.filter { it.isDirectory }.forEach { load(it, true) }
    }

    override fun dispose() {
        timer.cancel(false)
        roots.close()
    }
}
