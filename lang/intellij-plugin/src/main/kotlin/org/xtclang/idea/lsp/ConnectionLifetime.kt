package org.xtclang.idea.lsp

import com.redhat.devtools.lsp4ij.server.CannotStartProcessException
import java.util.concurrent.atomic.AtomicReference

/** One provider owns one process. LSP4IJ creates a fresh provider for every restart. */
internal class ConnectionLifetime(
    private val startProcess: () -> Unit,
    private val stopProcess: () -> Unit,
) {
    private enum class State {
        NEW,
        STARTED,
        STOPPED,
    }

    private val state = AtomicReference(State.NEW)

    fun start() {
        synchronized(state) {
            when (state.get()) {
                State.STOPPED -> {
                    throw CannotStartProcessException(
                        "Ecstasy LSP connection was stopped before startup completed"
                    )
                }

                State.STARTED -> {
                    return
                }

                State.NEW -> {
                    state.set(State.STARTED)
                    try {
                        startProcess()
                    } catch (e: Exception) {
                        stop()
                        throw e
                    }
                }
            }
        }
    }

    fun stop() {
        synchronized(state) {
            if (state.getAndSet(State.STOPPED) != State.STOPPED) stopProcess()
        }
    }
}
