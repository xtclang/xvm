/**
 * Logback-style multi-appender sink.
 *
 * `CompositeLogSink` fans out each enabled event to every delegate sink that accepts
 * the event's logger/level/primary-marker tuple. This is the Ecstasy equivalent of
 * attaching multiple Logback appenders to one logger.
 */
const CompositeLogSink(LogSink[] sinks)
        implements LogSink {

    @Override
    Boolean isEnabled(String loggerName, Level level, Marker? marker = Null) {
        for (LogSink sink : sinks) {
            if (sink.isEnabled(loggerName, level, marker)) {
                return True;
            }
        }
        return False;
    }

    @Override
    void log(LogEvent event) {
        for (LogSink sink : sinks) {
            if (sink.isEnabled(event.loggerName, event.level, event.marker)) {
                sink.log(event);
            }
        }
    }
}
