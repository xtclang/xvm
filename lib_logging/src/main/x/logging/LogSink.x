/**
 * Corresponds conceptually to a Logback `Appender`: a per-destination backend with its
 * own level/filter decision. This is not part of pure SLF4J; it is the backend boundary
 * that a real SLF4J deployment would normally get from Logback, Log4j 2, or another
 * implementation.
 *
 * Mapping multiple `LogSink`s onto one logger (Logback's "appender attached to logger"
 * model) is the job of [CompositeLogSink].
 *
 * The Service-Provider Interface for logging backends. A `LogSink` is the only thing a
 * `Logger` ever talks to; everything else (level checks, message formatting, marker
 * filtering, MDC capture) is implemented above the sink and is sink-agnostic.
 *
 * # Why this is the API/impl boundary
 *
 * Anything _above_ this interface is the public, stable API that user code depends on.
 * Anything _below_ this interface is replaceable: `ConsoleLogSink` for the default
 * platform-controlled console output, `MemoryLogSink` in tests, [JsonLogSink] for
 * structured JSON-Lines output, [CompositeLogSink] and [HierarchicalLogSink] for
 * Logback-style backend policy, or a future file/network/cloud sink. All of those are
 * interchangeable behind this interface.
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
 * See `doc/logging/usage/custom-sinks.md` for worked examples.
 *
 * # Choosing between `const` and `service` for an implementation
 *
 * `BasicLogger` is a `const` and references its sink through a `LogSink` field. That
 * means every concrete implementation must be `Passable` — i.e. either `immutable`
 * (`const`) or a `service`. Pick one of the two:
 *
 *   - `const`  — for stateless forwarders / pure adapters whose configuration is fixed
 *                at construction. Examples in this library: [NoopLogSink],
 *                [ConsoleLogSink]. Cheap to construct; methods run on the caller's fiber.
 *   - `service` — for sinks that carry mutable state shared across fibers: event
 *                buffers, hit counters, file/socket writers, async worker queues.
 *                Examples: [MemoryLogSink], [HierarchicalLogSink], [AsyncLogSink],
 *                and a future `FileLogSink`.
 *
 * The full rule, with reference examples from the platform/xunit codebases (e.g.
 * `service ConsoleExecutionListener`, `service ErrorLog`), is in
 * `doc/logging/design/design.md` under "Sink type: `const` vs `service`".
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
