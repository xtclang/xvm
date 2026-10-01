package org.xvm.lsp.server

import org.eclipse.lsp4j.services.LanguageClient
import org.slf4j.LoggerFactory
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit.SECONDS
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** One outstanding refresh per provider; changes during a reply produce one follow-up refresh. */
internal class ClientRefresh(
    private val client: () -> LanguageClient?,
) : AutoCloseable {
    enum class Feature {
        DIAGNOSTICS,
        TOKENS,
        INLAYS,
        LENSES,
        FOLDING,
    }

    private val enabled = AtomicReference<Set<Feature>>(emptySet())
    private val ready = AtomicBoolean()
    private val closed = AtomicBoolean()
    private val slots = Feature.entries.associateWith { Slot(it) }

    fun configure(features: Set<Feature>) {
        enabled.set(features.toSet())
    }

    fun initialized() {
        ready.set(true)
    }

    fun request(vararg features: Feature) {
        if (!ready.get() || closed.get()) return
        features.filter { it in enabled.get() }.forEach { slots.getValue(it).request() }
    }

    private inner class Slot(
        private val feature: Feature,
    ) {
        private val dirty = AtomicBoolean()
        private val running = AtomicBoolean()

        fun request() {
            dirty.set(true)
            if (!running.compareAndSet(false, true)) return
            // Scheduling, not transport, is the only operation performed under compiler locks.
            CompletableFuture.runAsync {
                dirty.set(false)
                val work =
                    if (closed.get()) {
                        CompletableFuture.completedFuture(null)
                    } else {
                        runCatching {
                            client()?.let {
                                when (feature) {
                                    Feature.DIAGNOSTICS -> it.refreshDiagnostics()
                                    Feature.TOKENS -> it.refreshSemanticTokens()
                                    Feature.INLAYS -> it.refreshInlayHints()
                                    Feature.LENSES -> it.refreshCodeLenses()
                                    Feature.FOLDING -> it.refreshFoldingRanges()
                                }
                            } ?: CompletableFuture.completedFuture(null)
                        }.getOrElse { CompletableFuture.failedFuture(it) }
                    }
                work.orTimeout(10, SECONDS).whenComplete { _, failure ->
                    if (failure != null && !closed.get()) {
                        logger.debug("{} refresh failed", feature, failure)
                    }
                    running.set(false)
                    // Either this callback or a concurrent request wins the next CAS. Neither
                    // clears dirty until its worker starts, so a change cannot disappear.
                    if (dirty.get() && !closed.get()) request()
                }
            }
        }
    }

    override fun close() {
        closed.set(true)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ClientRefresh::class.java)
    }
}
