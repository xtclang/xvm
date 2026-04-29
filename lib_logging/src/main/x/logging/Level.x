/**
 * Corresponds to `org.slf4j.event.Level` (SLF4J 2.x), `ch.qos.logback.classic.Level`,
 * `org.apache.logging.log4j.Level` (Log4j 2), `java.util.logging.Level` (JUL — different
 * names but the same idea).
 *
 * The severity levels used by the logging API. Identical in spirit to SLF4J / Logback / Log4j2:
 * `Trace` is the most verbose, `Error` is the most severe. `Off` is reserved for sink-side
 * configuration (i.e. "log nothing for this category") and is never used as the level of an
 * actual `LogEvent`.
 *
 * The numeric `severity` field is exposed so sinks can do cheap threshold comparisons:
 *
 *      if (event.level.severity >= threshold.severity) {
 *          // emit
 *      }
 */
enum Level(Int severity) {
    Trace(10),
    Debug(20),
    Info (30),
    Warn (40),
    Error(50),
    Off  (Int.MaxValue);

    /**
     * True iff an event at this level should be emitted given the supplied `threshold`.
     */
    Boolean enabledAtThreshold(Level threshold) {
        return this.severity >= threshold.severity;
    }
}
