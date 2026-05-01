/**
 * Corresponds, as a whole module, to the `slf4j-api` jar in the Java ecosystem — plus a
 * minimum viable binding (analogous to `slf4j-simple`) so the library is usable out of
 * the box. A future `lib_logging_logback` module would correspond to `logback-classic`
 * (configuration-driven, multi-appender, hierarchical logger config).
 *
 * Ecstasy logging library.
 *
 * The logging library is intentionally shaped to be _instantly familiar_ to anyone who has used
 * SLF4J 2.x in the Java ecosystem: named loggers, level checks, parameterized messages with `{}`
 * placeholders, exception attachment, markers, MDC, and the SLF4J 2.x fluent event builder API.
 *
 * The primary entry point is injection:
 *
 *      @Inject Logger logger;
 *
 * or, naming the logger explicitly:
 *
 *      @Inject("com.example.thing") Logger logger;
 *
 * Acquiring a logger without injection is also supported via `LoggerFactory`:
 *
 *      Logger logger = LoggerFactory.getLogger("com.example.thing");
 *
 * # API / Implementation boundary
 *
 * The public API consists of:
 *      - [Logger]                — the user-facing facade
 *      - [Level]                 — log severities
 *      - [Marker], [MarkerFactory]
 *      - [MDC]                   — mapped diagnostic context
 *      - [LogEvent]              — an immutable record of a single log call
 *      - [LogSink]               — the SPI that backends implement
 *      - [LoggingEventBuilder]   — SLF4J 2.x style fluent API
 *      - [LoggerFactory]         — non-injection acquisition path
 *
 * The implementation side of that boundary contains:
 *      - [ConsoleLogSink]        — default sink, writes through `@Inject Console`
 *      - [NoopLogSink]           — drops every event
 *      - [MemoryLogSink]         — captures events in memory; useful in tests
 *
 * A future `lib_logging_logback` module is expected to ship a configuration-driven sink with
 * appenders, layouts, filters, and per-logger thresholds — see `docs/logback-integration.md`.
 */
module logging.xtclang.org {
}
