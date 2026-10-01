package org.xvm.lsp.server

import org.eclipse.lsp4j.jsonrpc.MessageConsumer
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.jsonrpc.messages.NotificationMessage
import org.eclipse.lsp4j.jsonrpc.messages.RequestMessage
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode
import org.eclipse.lsp4j.jsonrpc.messages.ResponseMessage
import java.util.concurrent.atomic.AtomicReference

/** Enforces the wire lifecycle without restricting direct embedding/test use of server services. */
internal class ProtocolLifecycle {
    private enum class Phase {
        NEW,
        INITIALIZING,
        READY,
        RUNNING,
        SHUTDOWN,
    }

    private data class State(
        val phase: Phase,
        val initialization: Either<String, Number>? = null,
    )

    private val state = AtomicReference(State(Phase.NEW))
    private val output = AtomicReference<MessageConsumer>()

    fun wrap(
        next: MessageConsumer,
        received: Boolean,
    ): MessageConsumer {
        val consumer =
            MessageConsumer { message ->
                if (!received) {
                    if (message is ResponseMessage) {
                        val previous = state.get()
                        if (
                            previous.phase == Phase.INITIALIZING &&
                            previous.initialization == message.rawId
                        ) {
                            state.compareAndSet(
                                previous,
                                State(if (message.error == null) Phase.READY else Phase.NEW),
                            )
                        }
                    }
                    next.consume(message)
                } else {
                    when (message) {
                        is RequestMessage -> {
                            val previous = state.get()
                            val permitted =
                                when {
                                    previous.phase == Phase.NEW && message.method == "initialize" -> {
                                        state.compareAndSet(
                                            previous,
                                            State(Phase.INITIALIZING, message.rawId),
                                        )
                                    }

                                    previous.phase == Phase.READY || previous.phase == Phase.RUNNING -> {
                                        message.method != "initialize" &&
                                            (
                                                message.method != "shutdown" ||
                                                    state.compareAndSet(previous, State(Phase.SHUTDOWN))
                                            )
                                    }

                                    else -> {
                                        false
                                    }
                                }
                            if (permitted) {
                                next.consume(message)
                            } else {
                                output
                                    .get()
                                    .consume(
                                        ResponseMessage().apply {
                                            rawId = message.rawId
                                            error =
                                                if (
                                                    previous.phase == Phase.NEW ||
                                                    previous.phase == Phase.INITIALIZING
                                                ) {
                                                    ResponseError(
                                                        ResponseErrorCode.ServerNotInitialized,
                                                        "Server is not initialized",
                                                        null,
                                                    )
                                                } else {
                                                    ResponseError(
                                                        ResponseErrorCode.InvalidRequest,
                                                        "Request is invalid in the current server lifecycle",
                                                        null,
                                                    )
                                                }
                                        },
                                    )
                            }
                        }

                        is NotificationMessage -> {
                            val previous = state.get()
                            if (message.method == "exit") {
                                next.consume(message)
                            } else if (message.method == "initialized") {
                                if (
                                    previous.phase == Phase.READY &&
                                    state.compareAndSet(previous, State(Phase.RUNNING))
                                ) {
                                    next.consume(message)
                                }
                            } else if (previous.phase == Phase.READY || previous.phase == Phase.RUNNING) {
                                next.consume(message)
                            }
                        }

                        else -> {
                            next.consume(
                                message,
                            )
                        } // Replies must still release outstanding client requests.
                    }
                }
            }
        if (!received) output.set(consumer)
        return consumer
    }
}
