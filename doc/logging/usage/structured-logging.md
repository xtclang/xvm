# Structured logging in the logging POC

> **Audience:** anyone shipping Ecstasy code that needs to emit JSON or talk to a structured log aggregator.

This document explains structured logging as a requirement, then shows how the same
event looks in Java SLF4J/Logback, Go `log/slog`, Ecstasy `lib_logging`, and Ecstasy
`lib_slogging`.

"Structured" means a log event carries typed key/value fields in addition to a
human-readable message. Downstream systems can query fields directly instead of
regexing text.

## Fast path

The logging proposal treats structured fields as a hard requirement, not an optional
formatter trick:

- `lib_logging` carries structured fields as `LogEvent.keyValues`, request context as
  `MDC`, and categories as `Marker`; `JsonLogSink` renders those fields directly.
- `lib_slogging` carries the same information as `Attr` values; `JSONHandler` renders
  attrs and nested groups directly.
- Both designs keep the human message stable and put machine-readable fields beside it,
  so cloud logging, search, alerting, and audit pipelines do not need to parse text.

The examples immediately below show the same payment event in Java SLF4J, Go `slog`,
Ecstasy `lib_logging`, and Ecstasy `lib_slogging`. The later sections explain the
implementation layers and migration rationale.

## Why structured logging is mandatory now

A modern logging framework is not just a better `print`. It feeds systems such as
Cloud Logging, CloudWatch, Azure Monitor, OpenSearch, Splunk, Grafana Loki,
OpenTelemetry collectors, alert rules, audit pipelines, and incident dashboards. Those
systems need stable fields.

Unstructured text is still useful for a human reading a terminal:

```text
payment processed p_123 amount=4200 currency=EUR merchant=m_42
```

It is weak as production telemetry. Every consumer has to rediscover the schema by
parsing the message. A harmless wording change breaks dashboards and alerts.

A structured event keeps the human message stable and carries machine-readable fields:

```json
{
  "time": "2026-04-29T11:23:45.012Z",
  "level": "INFO",
  "logger": "com.example.PaymentService",
  "message": "payment processed",
  "paymentId": "p_123",
  "amount": 4200,
  "currency": "EUR",
  "merchantId": "m_42",
  "mdc": {"requestId": "r_abc"}
}
```

That is queryable: `paymentId="p_123"`, `amount > 1000`, `merchantId="m_42"`,
`requestId="r_abc"`, `level >= ERROR`. Any XDK logging design that cannot carry this
without parsing message text is not production-ready.

## Same event in the four APIs

### Java + SLF4J 2.x

```java
log.atInfo()
   .setMessage("payment processed")
   .addKeyValue("paymentId", payment.id())
   .addKeyValue("amount", payment.amount())
   .addKeyValue("currency", payment.currency())
   .addKeyValue("merchantId", merchant.id())
   .log();
```

This requires a Logback encoder/appender that reads SLF4J 2.x key/value pairs, for
example a JSON/logstash/cloud encoder. A plain PatternLayout usually renders only the
message unless configured to include key/value data.

### Go + `log/slog`

```go
logger.Info("payment processed",
    "paymentId", payment.ID,
    "amount", payment.Amount,
    "currency", payment.Currency,
    "merchantId", merchant.ID)
```

Go's built-in `JSONHandler` renders attrs as JSON fields. `With(...)` carries
request-wide fields:

```go
requestLog := logger.With("requestId", req.ID)
requestLog.Info("payment processed", "paymentId", payment.ID)
```

### Ecstasy + `lib_logging`

```ecstasy
logger.atInfo()
      .setMessage("payment processed")
      .addKeyValue("paymentId",  payment.id)
      .addKeyValue("amount",     payment.amount)
      .addKeyValue("currency",   payment.currency)
      .addKeyValue("merchantId", merchant.id)
      .log();
```

Context that applies to many events lives in MDC:

```ecstasy
@Inject MDC mdc;
mdc.put("requestId", request.id);

logger.atInfo()
      .addKeyValue("paymentId", payment.id)
      .log("payment processed");
```

`JsonLogSink` renders MDC and key/value pairs as structured fields.

### Ecstasy + `lib_slogging`

```ecstasy
logger.info("payment processed", [
        Attr.of("paymentId",  payment.id),
        Attr.of("amount",     payment.amount),
        Attr.of("currency",   payment.currency),
        Attr.of("merchantId", merchant.id),
]);
```

Request-wide fields are attached by deriving a logger:

```ecstasy
Logger requestLog = logger.with([Attr.of("requestId", request.id)]);
requestLog.info("payment processed", [Attr.of("paymentId", payment.id)]);
```

`JSONHandler` renders attrs as JSON fields and preserves nested `Attr.group(...)`
values.

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
        String              loggerName,
        Level               level,
        String              message,
        Time                timestamp,
        Marker[]            markers     = [],
        Exception?          exception   = Null,
        Object[]            arguments   = [],
        Map<String, String> mdcSnapshot = [],
        String              threadName  = "",
        String?             sourceFile  = Null,
        Int                 sourceLine  = -1,
        Map<String, Object> keyValues   = [],
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

## How `lib_slogging` maps onto this

The slog-shaped library collapses message arguments, markers, and per-event key/value
pairs into one concept: `Attr`.

### Layer 1 — call site

```ecstasy
logger.info("payment processed", [
        Attr.of("paymentId",  payment.id),
        Attr.of("amount",     payment.amount),
        Attr.of("currency",   payment.currency),
        Attr.of("merchantId", merchant.id),
]);
```

For request-scoped fields, derive a logger once:

```ecstasy
Logger requestLog = logger.with([
        Attr.of("requestId", request.id),
        Attr.of("tenant",    request.tenant),
]);
```

Groups are explicit nested attrs:

```ecstasy
requestLog.info("payment processed", [
        Attr.group("payment", [
                Attr.of("id",       payment.id),
                Attr.of("amount",   payment.amount),
                Attr.of("currency", payment.currency),
        ]),
]);
```

### Layer 2 — event data model

`Record` carries the completed message and `Attr[]`:

```ecstasy
const Record(
        Time       time,
        Level      level,
        String     message,
        Attr[]     attrs      = [],
        Exception? exception  = Null,
        String?    sourceFile = Null,
        Int        sourceLine = -1,
        String     threadName = "",
        );
```

There is no separate marker channel and no separate message-argument channel. That is
the point of the slog design.

### Layer 3 — handler

`JSONHandler` renders attrs directly as JSON fields, preserving groups as nested JSON
objects and applying `HandlerOptions` redaction:

```ecstasy
Handler handler = new JSONHandler(new HandlerOptions(
        Level.Info,
        ["authorization", "password"]));
Logger logger = new Logger(handler);
```

This is conceptually closer to Go's built-in `slog.NewJSONHandler` than to SLF4J's
facade-plus-encoder split.

## What about `MDC` vs key/value pairs?

SLF4J has _two_ structured-data channels:

- **MDC** — process-/fiber-local, set by surrounding code, carried on every event in the
  scope. Used for context that doesn't change per call: request ID, tenant, user.
- **Key/value pairs** — per-call, attached to one specific event. Used for facts about
  *this* specific log entry: payment ID, amount, target.

`lib_logging` keeps the same split. `MDC` is its own service; `LoggingEventBuilder.addKeyValue`
is per-event. A structured sink should render both — typically MDC under an `"mdc"`
sub-object and KV pairs at the top level, matching what `LogstashEncoder` does.

`lib_slogging` keeps the slog shape: request-scoped data is normally attached by
deriving a logger with `with(attrs)`, and per-event data is passed as attrs on the
specific log call. `LoggerContext` is available only when framework code needs
implicit propagation.

## Migration story for SLF4J users adopting `lib_logging`

The smallest possible change to a structured-logging-heavy codebase moving from SLF4J to
`lib_logging` is _none_: `atInfo()`/`addKeyValue`/`log()` is identical. Only the import
changes, and the LogSink choice on the deployment side.

The bigger story — encoder/appender wiring, log aggregator integration — is handled by
the structured sink (`JsonLogSink` in the simple case, a future configured backend in
the complex case). See `../future/logback-integration.md`.

## Migration story for Go slog users adopting `lib_slogging`

The conceptual migration is also direct:

| Go slog | Ecstasy `lib_slogging` |
|---|---|
| `logger.Info("payment processed", "paymentId", id)` | `logger.info("payment processed", [Attr.of("paymentId", id)])` |
| `logger.With("requestId", id)` | `logger.with([Attr.of("requestId", id)])` |
| `logger.WithGroup("payment")` | `logger.withGroup("payment")` |
| `slog.Group("payment", "id", id)` | `Attr.group("payment", [Attr.of("id", id)])` |
| `slog.NewJSONHandler(w, opts)` | `new JSONHandler(new HandlerOptions(...))` |

The main Ecstasy difference is that attrs are explicit `Attr` values, not alternating
`key, value` arguments. That avoids Go's malformed-argument case and keeps the record
typed before it reaches the handler.

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

Previous: [`injected-logger-example.md`](injected-logger-example.md) | Next: [`lazy-logging.md`](lazy-logging.md) → | Up: [`../README.md`](../README.md)
