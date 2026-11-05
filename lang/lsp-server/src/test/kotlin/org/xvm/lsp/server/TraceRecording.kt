package org.xvm.lsp.server

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.AppenderBase
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.slf4j.LoggerFactory
import java.util.concurrent.CopyOnWriteArrayList

/** Attaches a temporary trace appender and detaches it on close to isolate captured JSON events. */
internal class TraceRecording : AutoCloseable {
    val entries = CopyOnWriteArrayList<JsonObject>()
    private val logger = LoggerFactory.getLogger("org.xvm.lsp.trace") as Logger
    private val appender =
        object : AppenderBase<ILoggingEvent>() {
            override fun append(event: ILoggingEvent) {
                entries += JsonParser.parseString(event.formattedMessage).asJsonObject
            }
        }.apply { start() }

    init {
        logger.addAppender(appender)
    }

    override fun close() {
        logger.detachAppender(appender)
        appender.stop()
    }
}
