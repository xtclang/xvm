/**
 * Corresponds to `org.slf4j.helpers.NOPLogger` (and the binding `slf4j-nop`) plus Logback's
 * `ch.qos.logback.core.helpers.NOPAppender`. A sink that intentionally produces no output.
 *
 * A sink that silently drops every event. Useful when:
 *   - a library wants to default to "no output" unless the embedding application opts in;
 *   - a test wants to suppress noise without rewriting calling code;
 *   - benchmarks want to measure caller-side overhead minus sink cost.
 *
 * `isEnabled` always returns False so callers that respect the level check can skip
 * building arguments and snapshotting MDC entirely.
 */
service NoopLogSink
        implements LogSink {

    @Override
    Boolean isEnabled(String loggerName, Level level, Marker? marker = Null) {
        return False;
    }

    @Override
    void log(LogEvent event) {
        // intentionally empty
    }
}
