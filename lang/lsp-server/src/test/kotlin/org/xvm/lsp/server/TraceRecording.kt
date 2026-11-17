package org.xvm.lsp.server

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.AppenderBase
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.slf4j.LoggerFactory
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Attaches a temporary trace appender and detaches it on close to isolate captured JSON events. [onEntry] takes each
 * event as it is logged, on the thread that logs it.
 */
internal class TraceRecording(
    private val onEntry: (JsonObject) -> Unit = {},
) : AutoCloseable {
    val entries = CopyOnWriteArrayList<JsonObject>()
    private val logger = LoggerFactory.getLogger("org.xvm.lsp.trace") as Logger
    private val appender =
        object : AppenderBase<ILoggingEvent>() {
            override fun append(event: ILoggingEvent) {
                val entry = JsonParser.parseString(event.formattedMessage).asJsonObject
                entries += entry
                onEntry(entry)
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
