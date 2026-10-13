package org.xvm.lsp.server

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.AppenderBase
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.eclipse.lsp4j.WorkspaceSymbolParams
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.xvm.lsp.adapter.Adapter
import org.xvm.lsp.adapter.mock.MockAdapter
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletionException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit.SECONDS

/** Internal query failures need a local stack trace; ordinary cancellation and protocol rejections do not. */
class QueryFailureLoggingTest {
    @Test
    fun `unexpected backend failures retain their original exception in the server log`() {
        val failure = IllegalArgumentException("Negative source position")
        val errors = queryErrors(CompletionException(failure))
        assertThat(errors).hasSize(1)
        val event = errors.single()
        assertThat(event.formattedMessage).contains("workspace/symbol", "request failed")
        assertThat(event.throwableProxy.className).isEqualTo(failure.javaClass.name)
        assertThat(event.throwableProxy.message).isEqualTo(failure.message)
        assertThat(event.throwableProxy.stackTraceElementProxyArray).isNotEmpty()
    }

    @Test
    fun `cancellation and stale content produce no internal error log`() {
        assertThat(queryErrors(CancellationException("Canceled by editor"))).isEmpty()
        assertThat(
            queryErrors(ResponseErrorException(ResponseError(ResponseErrorCode.ContentModified, "Document changed", null))),
        ).isEmpty()
    }

    private fun queryErrors(failure: RuntimeException): List<ILoggingEvent> {
        val events = CopyOnWriteArrayList<ILoggingEvent>()
        val finished = CountDownLatch(1)
        val logger = LoggerFactory.getLogger(XtcTextDocumentService::class.java) as Logger
        val appender =
            object : AppenderBase<ILoggingEvent>() {
                override fun append(event: ILoggingEvent) {
                    events += event
                    if (event.formattedMessage.startsWith("workspace/symbol:") && " in " in event.formattedMessage) {
                        finished.countDown()
                    }
                }
            }.apply { start() }
        val adapter =
            object : Adapter by MockAdapter() {
                override fun findWorkspaceSymbols(query: String): Nothing = throw failure
            }
        logger.addAppender(appender)
        try {
            XtcLanguageServer(adapter).use { server ->
                assertThatThrownBy { server.workspaceService.symbol(WorkspaceSymbolParams("Value")).get(5, SECONDS) }
                    .isInstanceOf(Exception::class.java)
                assertThat(finished.await(5, SECONDS)).isTrue()
            }
            return events.filter { it.level == Level.ERROR }
        } finally {
            logger.detachAppender(appender)
            appender.stop()
        }
    }
}
