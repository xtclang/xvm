/**
 * Corresponds to `org.slf4j.event.LoggingEvent` (SLF4J) and
 * `ch.qos.logback.classic.spi.ILoggingEvent` / `LoggingEvent` (Logback).
 *
 * An immutable record of a single log call. Sinks receive `LogEvent` instances; layouts and
 * appenders can read every field freely without worrying about caller-side mutation.
 *
 * The `mdcSnapshot` is captured at the moment of the
 * log call so that asynchronous sinks see the context as it was when the event was raised, not
 * as it is when the event is finally written.
 */
const LogEvent(
        String              loggerName,
        Level               level,
        String              message,
        Time                timestamp,
        // The markers attached to this event. Empty when no markers were supplied. The
        // per-level `Logger.info(..., marker=X)` API supplies at most one; the fluent
        // `LoggingEventBuilder.addMarker(...)` accumulates many. Mirrors SLF4J 2.x's
        // `LoggingEvent.getMarkers(): List<Marker>`.
        Marker[]            markers     = [],
        Exception?          exception   = Null,
        Object[]            arguments   = [],
        Map<String, String> mdcSnapshot = [],
        String              threadName  = "",
        // Optional source metadata. The default `(Null, -1)` means "not captured".
        // `Logger.logAt(...)` populates these fields explicitly; future compiler/runtime
        // call-site capture should lower into that same API instead of changing sinks.
        String?             sourceFile  = Null,
        Int                 sourceLine  = -1,
        // Structured key/value pairs accumulated through `LoggingEventBuilder.addKeyValue`.
        // Mirrors SLF4J 2.x's `KeyValuePair` list on `LoggingEvent`. Sinks that don't care
        // about structured payloads can ignore this field; sinks that do (JSON layouts,
        // logback's structured-arguments encoder, future `lib_logging_logback`) read it
        // alongside `message`.
        Map<String, Object> keyValues   = [],
        ) {

    /**
     * Convenience accessor for the first marker, or `Null` when none. Sinks that only
     * surface a single category line (`[marker=NAME]`) can read this without
     * iterating; richer sinks should iterate [markers] directly. Mirrors SLF4J 1.x's
     * `LoggingEvent.getMarker()` for one-marker callers.
     */
    @RO Marker? marker.get() {
        return markers.empty ? Null : markers[0];
    }
}
