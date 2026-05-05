# Structured logging in `lib_logging`

This document explains how structured logging works in SLF4J 2.x, and how the same idea
maps onto `lib_logging`. "Structured" here means: events that carry typed key/value pairs
in addition to (or instead of) a free-form message, so that downstream consumers (log
aggregators, dashboards, alerting) can query on those fields without regexing the text.

## How SLF4J 2.x does structured logging

SLF4J 1.x had no structured-logging story at the API level. You either:

- abused the message text (`log.info("event=login user={} ip={}", user, ip)`), which works
  but every consumer has to parse it back out; or
- depended directly on `logback-classic` and added structured fields through
  `org.slf4j.Marker` subclasses or a custom `Encoder`.

SLF4J 2.x added **first-class key/value pairs** via the fluent `LoggingEventBuilder`.
The relevant types:

| Type | Role |
|---|---|
| `org.slf4j.spi.LoggingEventBuilder` | Builder returned by `logger.atInfo()` / `atLevel(...)`. Provides `addKeyValue(String, Object)`. |
| `org.slf4j.event.KeyValuePair` | Tiny value type holding `(String key, Object value)`. |
| `org.slf4j.spi.LoggingEventAware` | Marker interface for sinks that want the raw `LoggingEvent` (including KV pairs) instead of just the formatted message. |
| `org.slf4j.event.LoggingEvent.getKeyValuePairs()` | Sink-side accessor. |

A typical SLF4J 2.x structured log call:

```java
log.atInfo()
   .setMessage("payment processed")
   .addKeyValue("paymentId", payment.id())
   .addKeyValue("amount", payment.amount())
   .addKeyValue("currency", payment.currency())
   .addKeyValue("merchantId", merchant.id())
   .log();
```

A Logback encoder that understands KV pairs (e.g. `LogstashEncoder` from the
`logstash-logback-encoder` library) emits this as JSON:

```json
{
  "@timestamp": "2026-04-29T11:23:45.012Z",
  "level": "INFO",
  "logger": "com.example.PaymentService",
  "message": "payment processed",
  "paymentId": "p_123",
  "amount": 4200,
  "currency": "EUR",
  "merchantId": "m_42"
}
```

The PatternLayout-based encoders (the default `ch.qos.logback.classic.encoder.PatternLayoutEncoder`)
*don't* surface KV pairs — they only see the formatted message. So in practice, structured
logging in the SLF4J/Logback world means: **caller emits with `addKeyValue`, plus a
KV-aware encoder on the sink side**.

## Three layers in the SLF4J approach

It's worth pulling these apart because the same three layers exist in `lib_logging`:

1. **The call-site API** — how the caller expresses "this event carries these fields".
   In SLF4J 2.x: `LoggingEventBuilder.addKeyValue`. KV pairs are typed `Object`, not
   `String`, so the caller doesn't have to pre-format.

2. **The event data model** — how the event carries the fields between the call site
   and the sink. In SLF4J: `LoggingEvent.getKeyValuePairs()` returning `List<KeyValuePair>`.

3. **The sink/encoder** — what actually renders or forwards the structured data. In
   SLF4J/Logback: a KV-aware `Encoder` (LogstashEncoder, GelfEncoder, etc.) or a custom
   `Appender` that consumes `getKeyValuePairs()` directly.

Most SLF4J users only see layer 1. Library authors and ops engineers see all three.

## How `lib_logging` maps onto this

The same three layers, with the same separation of concerns:

### Layer 1 — call site

`LoggingEventBuilder` already has `addKeyValue(String key, Object value)`, mirroring SLF4J:

```ecstasy
logger.atInfo()
      .setMessage("payment processed")
      .addKeyValue("paymentId",  payment.id)
      .addKeyValue("amount",     payment.amount)
      .addKeyValue("currency",   payment.currency)
      .addKeyValue("merchantId", merchant.id)
      .log();
```

This is the public, stable API — caller code committed to this signature today will not
break when richer sinks arrive.

### Layer 2 — event data model

The `LogEvent` const carries a `keyValues` field analogous to SLF4J's
`getKeyValuePairs()`:

```ecstasy
const LogEvent(
        String                        loggerName,
        Level                         level,
        String                        message,
        Marker[]                      markers     = [],
        Exception?                    exception   = Null,
        Object[]                      arguments   = [],
        Map<String, Object>           keyValues   = [],
        Map<String, String>           mdcSnapshot = [],
        String                        threadName  = "",
        Time                          timestamp,
        );
```

`BasicEventBuilder` accumulates KV pairs in a local `ListMap`, freezes a snapshot, and
passes them through when `log()` materializes the event. Duplicate keys currently use
"last value wins" semantics; the tests pin that behavior down.

### Layer 3 — sink

`LogSink` already gets the whole `LogEvent`, so a sink that wants to render KV pairs
just reads `event.keyValues`. The default `ConsoleLogSink` is intentionally simple: it
appends KV pairs as `{key=value, key=value}` after the message, in the same spirit as
Logback's simple text layouts. Structured sinks can render the same fields as real JSON.

The shipped `JsonLogSink` renders every event as a single JSON object per line:

```json
{"timestamp":"2026-04-29T11:23:45.012Z","level":"INFO","logger":"com.example.PaymentService","message":"payment processed","paymentId":"p_123","amount":4200,"currency":"EUR","merchantId":"m_42","mdc":{"requestId":"r_abc"}}
```

That's a separate `LogSink`, and the runtime chooses whether it's the active sink.
Caller code is unchanged.

### Configuring `JsonLogSink`

```ecstasy
LogSink sink = new JsonLogSink(new JsonLogSinkOptions(
        Info,
        ["authorization", "password"],
        "***",
        True, True, True, True,
        "time", "level", "logger", "message",
        "mdc", "markers", "exception", "source"));
```

`JsonLogSink` renders through `lib_json`, preserves MDC/markers/key-values/source, and
redacts configured keys before output.

## What about `MDC` vs key/value pairs?

SLF4J has _two_ structured-data channels:

- **MDC** — process-/fiber-local, set by surrounding code, carried on every event in the
  scope. Used for context that doesn't change per call: request ID, tenant, user.
- **Key/value pairs** — per-call, attached to one specific event. Used for facts about
  *this* specific log entry: payment ID, amount, target.

`lib_logging` keeps the same split. `MDC` is its own service; `LoggingEventBuilder.addKeyValue`
is per-event. A structured sink should render both — typically MDC under an `"mdc"` sub-object
and KV pairs at the top level, matching what `LogstashEncoder` does.

## Migration story for SLF4J users adopting `lib_logging`

The smallest possible change to a structured-logging-heavy codebase moving from SLF4J to
`lib_logging` is _none_: `atInfo()`/`addKeyValue`/`log()` is identical. Only the import
changes, and the LogSink choice on the deployment side.

The bigger story — encoder/appender wiring, log aggregator integration — is handled by
the structured sink (`JsonLogSink` in the simple case, a future configured backend in
the complex case). See `../future/logback-integration.md`.

## What to build first vs. later

For the next production-oriented cut, the right order is:

1. Decide whether duplicate structured keys should remain "last value wins" (`Map`) or
   preserve duplicates (`KeyValuePair[]`, closer to SLF4J 2.x).
2. Add cloud-oriented layouts that map MDC, markers, and key/value pairs to the field
   names expected by GCP / AWS / Azure shippers.

Everything beyond that — typed value formatting, schema validation, OpenTelemetry
integration, log-correlation IDs propagated across services — is sink-side. The API
is already shaped to support it without changes.


---

_See also [../README.md](../README.md) for the full doc index and reading paths._
