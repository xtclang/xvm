package org.xvm.lsp.server

import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.CompletableFuture
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
internal class ResourceFileWatchers {
    private val queue =
        AtomicReference(CompletableFuture.completedFuture(emptyMap<Watch, String>()))

    fun update(
        client: LanguageClient,
        roots: Set<String>,
        relativePatterns: Boolean = true,
    ): CompletableFuture<Map<String, String>> {
        val result = CompletableFuture<Map<Watch, String>>()
        val previous = queue.getAndSet(result)
        previous
            .thenComposeAsync { active ->
                val next =
                    roots
                        .map { Watch.forRoot(it, relativePatterns) }
                        .associateWith {
                            active[it] ?: "xtc-resources-${UUID.randomUUID()}"
                        }
                val added = next.filterKeys { it !in active }
                val removed = active.filterKeys { it !in next }
                val register =
                    if (added.isEmpty()) CompletableFuture.completedFuture(null)
                    else {
                        attempt {
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
                            attempt {
                                client.unregisterCapability(
                                    UnregistrationParams(
                                        removed.values.map {
                                            Unregistration(
                                                it,
                                                "workspace/didChangeWatchedFiles",
                                            )
                                        }
                                    )
                                )
                            }
                                .handle { _, removalFailure ->
                                    if (removalFailure == null) next
                                    else {
                                        logger.warn(
                                            "Resource watcher removal failed; retry on the next update",
                                            removalFailure,
                                        )
                                        active + added
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
