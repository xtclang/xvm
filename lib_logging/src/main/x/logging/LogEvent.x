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
        Marker?             marker      = Null,
        Exception?          exception   = Null,
        Object[]            arguments   = [],
        Map<String, String> mdcSnapshot = [],
        String              threadName  = "",
        );
