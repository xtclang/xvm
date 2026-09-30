package org.xvm.lsp.server

import java.io.Closeable
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit.SECONDS
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import org.eclipse.lsp4j.DidChangeWatchedFilesRegistrationOptions
import org.eclipse.lsp4j.FileSystemWatcher
import org.eclipse.lsp4j.Registration
import org.eclipse.lsp4j.RegistrationParams
import org.eclipse.lsp4j.RelativePattern
import org.eclipse.lsp4j.Unregistration
import org.eclipse.lsp4j.UnregistrationParams
import org.eclipse.lsp4j.WatchKind
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.services.LanguageClient
import org.slf4j.LoggerFactory

/** Serialize registration changes so a delayed removal cannot retire a newer root subscription. */
internal class ResourceFileWatchers(
    private val deadline: () -> CompletableFuture<Void> = {
        CompletableFuture<Void>().completeOnTimeout(null, 10, SECONDS)
    }
) : Closeable {
    private val closed = AtomicBoolean()
    private val pending = ConcurrentHashMap.newKeySet<CompletableFuture<Void>>()
    // Unacknowledged removals remain retryable, but their IDs are never reusable as active watches.
    private val retired = ConcurrentHashMap<String, LanguageClient>()
    private val queue =
        AtomicReference(CompletableFuture.completedFuture(emptyMap<Watch, String>()))

    fun update(
        client: LanguageClient,
        roots: Set<String>,
        relativePatterns: Boolean = true,
    ): CompletableFuture<Map<String, String>> {
        if (closed.get()) return CompletableFuture.completedFuture(emptyMap())
        val result = CompletableFuture<Map<Watch, String>>()
        val previous = queue.getAndSet(result)
        previous
            .thenComposeAsync { active ->
                if (closed.get())
                    return@thenComposeAsync CompletableFuture.completedFuture(emptyMap())
                val next =
                    roots
                        .map { Watch.forRoot(it, relativePatterns) }
                        .associateWith {
                            active[it] ?: "xtc-resources-${UUID.randomUUID()}"
                        }
                val added = next.filterKeys { it !in active }
                val removed =
                    (active.filterKeys { it !in next }.values +
                            retired.entries.filter { it.value === client }.map { it.key })
                        .distinct()
                val register =
                    if (added.isEmpty()) CompletableFuture.completedFuture(null)
                    else {
                        awaitReply(onLateSuccess = { unregister(client, added.values) }) {
                            client.registerCapability(
                                RegistrationParams(
                                    added.map { (watch, id) ->
                                        Registration(
                                            id,
                                            "workspace/didChangeWatchedFiles",
                                            DidChangeWatchedFilesRegistrationOptions(
                                                watch.patterns()
                                            ),
                                        )
                                    }
                                )
                            )
                        }
                    }
                register
                    .handle { _, failure ->
                        if (failure != null) {
                            logger.warn(
                                "Resource watcher registration failed; retaining previous roots",
                                failure,
                            )
                            CompletableFuture.completedFuture(active)
                        } else if (removed.isEmpty()) CompletableFuture.completedFuture(next)
                        else
                            awaitReply { unregister(client, removed) }
                                .handle { _, removalFailure ->
                                    if (removalFailure == null) next
                                    else {
                                        logger.warn(
                                            "Resource watcher removal was not acknowledged; retiring its registration IDs",
                                            removalFailure,
                                        )
                                        // An eventual successful removal must never remove an ID
                                        // reused by a later update, even for the same root.
                                        next
                                    }
                                }
                    }
                    .thenCompose { it }
            }
            .whenComplete { current, failure ->
                if (failure == null) result.complete(current)
                else {
                    logger.warn("Resource watcher update failed", failure)
                    // Keep the queue usable even if a client implementation throws synchronously.
                    result.complete(previous.getNow(emptyMap()))
                }
            }
        return result.thenApply { active -> active.entries.associate { it.key.root to it.value } }
    }

    /** Bound each reply without cancelling the original RPC: it can still succeed on the client. */
    private fun awaitReply(
        onLateSuccess: () -> Unit = {},
        action: () -> CompletableFuture<Void>,
    ): CompletableFuture<Void> {
        val result = CompletableFuture<Void>()
        pending.add(result)
        result.whenComplete { _, _ -> pending.remove(result) }
        if (closed.get()) {
            result.completeExceptionally(CancellationException("Resource watchers closed"))
            return result
        }
        val timer = deadline()
        timer.thenRun {
            result.completeExceptionally(TimeoutException("Resource watcher reply timed out"))
        }
        result.whenComplete { _, _ -> timer.cancel(false) }
        attempt(action).whenComplete { _, failure ->
            if (failure != null) result.completeExceptionally(failure)
            else if (!result.complete(null)) onLateSuccess()
        }
        return result
    }

    private fun unregister(
        client: LanguageClient,
        ids: Collection<String>,
    ): CompletableFuture<Void> {
        ids.forEach { retired[it] = client }
        val reply = attempt {
            client.unregisterCapability(
                UnregistrationParams(
                    ids.map {
                        Unregistration(it, "workspace/didChangeWatchedFiles")
                    }
                )
            )
        }
        reply.whenComplete { _, failure ->
            if (failure == null) ids.forEach { retired.remove(it, client) }
        }
        return reply
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            retired.clear()
            pending.forEach {
                it.completeExceptionally(CancellationException("Resource watchers closed"))
            }
        }
    }

    /** Keep the recursive root plus a flat watch that survives root removal and creation. */
    private data class Watch(
        val root: String,
        val relative: Boolean,
        val ancestor: String?,
        val child: String?,
    ) {
        fun patterns(): List<FileSystemWatcher> = buildList {
            add(pattern(root, "**/*"))
            if (ancestor != null && child != null) add(pattern(ancestor, child))
        }

        private fun pattern(base: String, glob: String) =
            FileSystemWatcher(
                if (relative) Either.forRight(RelativePattern(Either.forRight(base), glob))
                else Either.forLeft(URI.create(base).path.trimEnd('/') + "/" + glob),
                WatchKind.Create + WatchKind.Change + WatchKind.Delete,
            )

        companion object {
            fun forRoot(root: String, relative: Boolean): Watch {
                val path = Path.of(URI.create(root))
                val ancestor =
                    generateSequence(path.parent) { it.parent }.firstOrNull(Files::isDirectory)
                // Watch only the first missing child, never the ancestor's entire subtree. A
                // membership event updates this plan as generated parent directories appear.
                val child =
                    ancestor
                        ?.relativize(path)
                        ?.getName(0)
                        ?.toString()
                        ?.map { character ->
                            when (character) {
                                '*',
                                '?',
                                '[',
                                ']',
                                '{',
                                '}' -> "[$character]"
                                else -> character.toString()
                            }
                        }
                        ?.joinToString("")
                return Watch(root, relative, ancestor?.toUri()?.toString(), child)
            }
        }
    }

    private companion object {
        val logger = LoggerFactory.getLogger(ResourceFileWatchers::class.java)

        fun attempt(action: () -> CompletableFuture<Void>): CompletableFuture<Void> =
            try {
                action()
            } catch (failure: RuntimeException) {
                CompletableFuture.failedFuture(failure)
            }
    }
}
