package org.xvm.lsp.server

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.AppenderBase
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.slf4j.LoggerFactory
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Attaches a temporary trace appender and detaches it on close to isolate captured JSON events. [onEntry] takes each
 * event as it is logged, on the thread that logs it. With [details], the trace is at DEBUG until close, as a test run's
 * is, and has the events' details.
 */
internal class TraceRecording(
    details: Boolean = false,
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

    private val level = logger.level

    init {
        if (details) logger.level = Level.DEBUG
        logger.addAppender(appender)
    }

    override fun close() {
        logger.detachAppender(appender)
        appender.stop()
        logger.level = level
    }
}
