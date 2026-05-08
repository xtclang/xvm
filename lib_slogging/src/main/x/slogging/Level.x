/**
 * Corresponds to [Open Telemetry Logging data model Severity fields]
 * (https://opentelemetry.io/docs/specs/otel/logs/data-model/#severity-fields)
 *
 * Severity is a plain integer; well-known constants are named (`DEBUG=5`, `INFO=9`, `WARN=13`,
 * `ERROR=17`) but callers may construct intermediate or beyond-canonical levels. Smaller numerical
 * values correspond to less severe events (such as debug events), larger numerical values
 * correspond to more severe events (such as errors and critical events).
 *
 * The level model is an open integer line. The canonical four levels are spaced four apart so
 * callers can interject custom levels (for example, adding `Notice` between `Info` and `Warn`,
 * `Critical` past `Error`) without colliding with a library's choices.
 *
 *      Level NOTICE = new Level(10, "NOTICE");
 *      Level CRITICAL = new Level(18, "CRITICAL");
 *      logger.log(NOTICE, "user signed in");
 *
 * The four canonical names are exposed as `static` constants on this type. Comparisons go through
 * `severity` directly.
 *
 *      if (level.severity >= threshold.severity) { ... }
 *
 * @param severity  numerical value of the severity
 * @param label     human-readable label for the severity
 */
const Level(Int severity, String label)
        implements Orderable, Hashable {
    /**
     * A debugging level log event.
     */
    static Level Debug = new Level( 5, "DEBUG");

    /**
     * An informational level log event.
     */
    static Level Info  = new Level( 9, "INFO");

    /**
     * A warning level log event.
     */
    static Level Warn  = new Level(13, "WARN");

    /**
     * An error level log event.
     */
    static Level Error = new Level(17, "ERROR");

    /**
     * `True` iff a record at this level should be emitted given the supplied
     * `threshold`. Matches the SLF4J library's [logging.Level.enabledAtThreshold] for
     * caller convenience.
     */
    Boolean enabledAtThreshold(Level threshold) {
        return severity >= threshold.severity;
    }

    /**
     * Natural ordering is severity ordering.
     */
    @Override
    static <CompileType extends Level> Ordered compare(CompileType a, CompileType b)
            = a.severity <=> b.severity;

    @Override
    static <CompileType extends Level> Boolean equals(CompileType value1, CompileType value2)
            = value1.severity == value2.severity;

    @Override
    static <CompileType extends Level> Int hashCode(CompileType value)
            = value.severity.hashCode();

    @Override
    Int estimateStringLength() = label.size;

    @Override
    Appender<Char> appendTo(Appender<Char> buf) = label.appendTo(buf);
}
