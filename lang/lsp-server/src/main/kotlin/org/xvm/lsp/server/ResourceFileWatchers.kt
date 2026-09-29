package org.xvm.lsp.server

import java.net.URI
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
        AtomicReference(CompletableFuture.completedFuture(emptyMap<String, String>()))

    fun update(
        client: LanguageClient,
        roots: Set<String>,
        relativePatterns: Boolean = true,
    ): CompletableFuture<Map<String, String>> {
        val result = CompletableFuture<Map<String, String>>()
        val previous = queue.getAndSet(result)
        previous
            .thenComposeAsync { active ->
                val next = roots.associateWith {
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
                                    added.map { (root, id) ->
                                        Registration(
                                            id,
                                            "workspace/didChangeWatchedFiles",
                                            DidChangeWatchedFilesRegistrationOptions(
                                                listOf(
                                                    FileSystemWatcher(
                                                        if (relativePatterns)
                                                            Either.forRight(
                                                                RelativePattern(
                                                                    Either.forRight(root),
                                                                    "**/*",
                                                                )
                                                            )
                                                        else
                                                            Either.forLeft(
                                                                URI.create(root).path.trimEnd('/') +
                                                                    "/**/*"
                                                            ),
                                                        WatchKind.Create +
                                                            WatchKind.Change +
                                                            WatchKind.Delete,
                                                    )
                                                )
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
        return result
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
