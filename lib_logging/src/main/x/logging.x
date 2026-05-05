/**
 * Corresponds, as a whole module, to the `slf4j-api` jar in the Java ecosystem — plus a
 * minimum viable binding (analogous to `slf4j-simple`) so the library is usable out of
 * the box. The base module also includes the production building blocks that normally
 * sit behind an SLF4J facade: async forwarding, multi-sink fanout, hierarchical logger
 * thresholds, and JSON-Lines rendering with redaction knobs. A future
 * `lib_logging_logback` module can add external configuration-file loading on top of
 * those primitives.
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
 * Per-name loggers are derived from the injected logger:
 *
 *      Logger payments = logger.named("com.example.payments");
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
 *      - [JsonLogSink]           — JSON-Lines sink rendered by `lib_json`
 *      - [CompositeLogSink]      — Logback-style multi-appender fanout
 *      - [HierarchicalLogSink]   — Logback-style longest-prefix level routing
 *      - [AsyncLogSink]          — bounded async wrapper for slow sinks
 *
 * A future `lib_logging_logback` module is expected to ship configuration-file loading,
 * richer appenders, layouts, filters, and hot reload — see
 * `doc/logging/future/logback-integration.md`.
 *
 * For official SLF4J API links mapped back to these Ecstasy types, see
 * `doc/logging/api-cross-reference.md`.
 */
module logging.xtclang.org {
    package json import json.xtclang.org;

    /**
     * Lazy message supplier used by [Logger] and [LoggingEventBuilder].
     *
     * The logging implementation invokes this function only after the active [LogSink]
     * accepts the event's level (and primary marker). This mirrors Java `Supplier<String>`
     * logging APIs and Kotlin logging blocks, where expensive message construction stays
     * behind the level check.
     */
    typedef function String() as MessageSupplier;

    /**
     * Lazy structured value supplier used by the fluent builder for positional `{}` arguments
     * and structured key/value pairs.
     *
     * The supplier is resolved immediately before [LogEvent] construction, never for a disabled
     * event. The returned value must obey the same service-boundary rules as any other log
     * argument or structured value.
     */
    typedef function Object() as ObjectSupplier;
}
