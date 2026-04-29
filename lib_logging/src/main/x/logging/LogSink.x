/**
 * Corresponds, conceptually, to two things in the SLF4J/Logback world:
 *   - `org.slf4j.spi.SLF4JServiceProvider` — the binding/provider boundary you implement
 *     to plug a backend into SLF4J.
 *   - `ch.qos.logback.core.Appender` — the per-destination output sink in Logback.
 *
 * `LogSink` is more like the Logback `Appender`: a single emission target with its own
 * level filter. Mapping multiple `LogSink`s onto one logger (Logback's "appender attached
 * to logger" model) is the job of a future composite sink — see
 * `doc/logging/LOGBACK_INTEGRATION.md`.
 *
 * The Service-Provider Interface for logging backends. A `LogSink` is the only thing a
 * `Logger` ever talks to; everything else (level checks, message formatting, marker
 * filtering, MDC capture) is implemented above the sink and is sink-agnostic.
 *
 * # Why this is the API/impl boundary
 *
 * Anything _above_ this interface is the public, stable API that user code depends on.
 * Anything _below_ this interface is replaceable: `ConsoleLogSink` for the default
 * platform-controlled console output, `MemoryLogSink` in tests, a future `LogbackLogSink`
 * for configuration-driven file/network appenders, a native sink wrapping `slf4j`+`logback`
 * via the JIT bridge — all of those are interchangeable behind this interface.
 *
 * # Implementing a custom sink
 *
 * The contract is small:
 *   - `isEnabled` is the cheap fast-path. It must return promptly and may be called once per
 *     log statement. Implementations doing per-logger configuration (e.g. logback) consult
 *     their own configuration tree here.
 *   - `log` receives a fully-formed `LogEvent`. The message has already had `{}` placeholders
 *     substituted; the `mdcSnapshot` has already been captured.
 *
 * See `docs/CUSTOM_SINKS.md` for a worked example.
 */
interface LogSink {

    /**
     * Cheap level check. Should be safe to call on every log statement. May consult marker-
     * specific or logger-specific configuration to decide.
     *
     * @param loggerName  the name of the logger asking
     * @param level       the level of the message about to be emitted
     * @param marker      an optional marker the caller has attached
     */
    Boolean isEnabled(String loggerName, Level level, Marker? marker = Null);

    /**
     * Emit the supplied event. Must not throw under normal conditions; sinks that experience
     * an internal failure (disk full, remote endpoint unreachable) are expected to degrade
     * gracefully — typically by writing to standard error — rather than propagate.
     */
    void log(LogEvent event);
}
