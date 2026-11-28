package org.xvm.lsp

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.AppenderBase
import org.slf4j.LoggerFactory

/** Runs [body] while [lines] gets each line that the server's loggers log at INFO or above, as it is logged. */
fun <T> logged(
    lines: MutableList<String>,
    body: () -> T,
): T {
    val logger = LoggerFactory.getLogger("org.xvm.lsp") as Logger
    val appender =
        object : AppenderBase<ILoggingEvent>() {
            override fun append(event: ILoggingEvent) {
                lines += event.formattedMessage
            }
        }.apply { start() }
    logger.addAppender(appender)
    try {
        return body()
    } finally {
        logger.detachAppender(appender)
        appender.stop()
    }
}
