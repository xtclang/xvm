# Log markers

> **Audience:** anyone evaluating `lib_logging` who has not used SLF4J/Logback
> markers in production. Read this if you see `Marker` in the API and wonder
> what it earns.
>
> **Length-conscious read:** the [bottom line](#bottom-line) tells you the
> outcome in two paragraphs. The rest is supporting detail.

## What a marker is

A **log marker** is a *named tag* attached to a log record that gives sinks a
first-class "category" to filter on, separate from the message text and the
severity level. SLF4J introduced the concept in 2005; the same idea exists in
Log4j 2 (`Marker`) and is conceptually similar to `MDC` keys in Logback config
or "properties" in log4net — but markers travel *with the event* whereas MDC
travels *with the thread*.

Concrete use cases — all from production deployments using SLF4J/Logback:

- **`AUDIT`** — route every event tagged with this marker to an *append-only*
  audit log that satisfies SOX / PCI-DSS / HIPAA retention rules. The same
  logger writes ordinary events to a normal file; the audit log only sees
  marker-tagged events.
- **`SECURITY`** / **`SECURITY.BREACH`** — forward security-relevant events to
  a SIEM (Splunk, Elastic, Datadog) on a separate channel, optionally with
  stricter encryption / redaction filters in front.
- **`SLOW_QUERY`** / **`PERFORMANCE`** — emit perf-suspect events to an APM
  pipeline (New Relic, AppDynamics) without the noise of regular debug logs.
- **`BILLING`** / **`CONFIDENTIAL`** — route financial-impact events to a
  system with longer retention and tighter access controls.
- **`PUBLIC_API_REQUEST`** — tee API-boundary events to a separate access log
  used for traffic analysis and rate-limit tuning.

Markers also support a *parent/child DAG*: in SLF4J,
`SECURITY.contains(BREACH)` returns true if `BREACH` was added as a child of
`SECURITY`. A filter configured on `SECURITY` therefore catches any descendant
marker without needing to know their names. This is the marker model's only
real edge over a plain `is_security: true` attribute — and almost no
production code uses it.

## Who actually uses markers

Inside the JVM ecosystem, marker support is widespread:

- **Logback** (the dominant SLF4J binding): markers are first-class.
  `TurboFilter` and `EvaluatorFilter` can route events by marker. Most JSON
  encoders emit the marker name as a structured field. Used in **almost every
  Java enterprise app** built between roughly 2010 and 2020.
- **Log4j 2**: `MarkerManager` interns markers; `MarkerFilter` accepts/denies
  by marker name. Used heavily in Apache projects (Kafka, Cassandra, Solr) and
  in legacy enterprise codebases.
- **SLF4J 2.x** itself: `Logger.atInfo().addMarker(...)` is the canonical
  fluent builder usage, and `LoggingEvent.getMarkers(): List<Marker>` is the
  v2 wire format.
- **Apache Camel, Spring Boot, JBoss / WildFly, ActiveMQ, Hazelcast** — all
  surface marker-based filtering in their default Logback / Log4j configs.

Outside the JVM, markers are deliberately absent:

- **Go's `log/slog`** rejects markers. Categorisation is just
  `slog.Bool("audit", true)` or `slog.String("category", "security")`.
- **Python's `logging`** has no markers. Categories live in the logger name
  hierarchy (`security.breach`) and in `extra={...}` dict attrs.
- **.NET's `Microsoft.Extensions.Logging`** has no markers. Categories are
  logger names; structured fields go in the `state` object.
- **Rust's `tracing`** has no markers. The `target` field on each event
  approximates the same idea; otherwise `field::display(...)` attributes.

The pattern: **markers are a JVM-ecosystem feature**, born of SLF4J, used
widely *inside* the JVM, and deliberately excluded from every well-known
modern logger designed since 2015.

## What is the actual payoff

Three things, in decreasing order of how often they justify the API surface:

1. **Backend routing.** A sink looks at the marker and decides where the event
   goes (audit log vs. main log vs. SIEM). This is how 90% of production
   marker usage works in practice. It is the *only* real pull for keeping
   markers.
2. **Marker DAG queries.** `SECURITY.contains(BREACH)` and `marker.iterator`
   walking parent references. Mostly unused outside of niche audit frameworks.
3. **Marker as a typed-tag** — a way to say "this is the audit log line"
   without spelling out a `"category"` string. Same as a string-keyed boolean
   attr; the "type-ness" is a thin gain.

Backend routing is real, but it is the same operation as filtering on
`attrs["category"] == "AUDIT"`. The marker concept is *one type to learn* and
gives you a slightly cheaper filter (interned reference identity vs string
equality). Whether that's worth a separate type in the API depends on whether
the audience reflexively reaches for `MarkerFactory.getMarker("AUDIT")` (JVM
veterans: yes; everyone else: no).

## Marker patterns side-by-side

The cases below cover what marker users actually do in production. Each shows
the SLF4J idiom (`lib_logging`) and the slog equivalent (`lib_slogging`).

### Single category tag — "this is an audit event"

```ecstasy
// lib_logging
logger.atInfo()
      .addMarker(MarkerFactory.getMarker("AUDIT"))
      .log("user signed in");
```

```ecstasy
// lib_slogging
logger.info("user signed in", [Attr.of("audit", True)]);
// or, if you prefer string-tag style:
logger.info("user signed in", [Attr.of("category", "AUDIT")]);
```

Cloud-side rendering (GCP Cloud Logging, JSON):

```jsonc
// from lib_logging via the Logback Cloud Logging appender:
{ "severity": "INFO", "message": "user signed in", "labels": { "AUDIT": "true" } }

// from lib_slogging via slog's JSONHandler:
{ "level": "INFO",    "msg": "user signed in", "audit": true }
```

The SLF4J adapter shoves markers into `labels` (a string-only flat map). The
slog version emits the boolean directly into `jsonPayload`. **Both are
queryable in Cloud Logging.** A query for "all audit events" is
`labels.AUDIT="true"` in the SLF4J case and `jsonPayload.audit=true` in the
slog case — same operational outcome, different cell in the JSON.

### Multiple markers on one event

```ecstasy
// lib_logging — fluent builder accumulates
Marker AUDIT    = MarkerFactory.getMarker("AUDIT");
Marker SECURITY = MarkerFactory.getMarker("SECURITY");

logger.atInfo()
      .addMarker(AUDIT)
      .addMarker(SECURITY)
      .log("login attempt");
```

```ecstasy
// lib_slogging — just two attrs
logger.info("login attempt", [
        Attr.of("audit",    True),
        Attr.of("security", True),
]);
```

Multi-marker is what motivated W-1 in [`../open-questions.md`](../open-questions.md).
In slog the question doesn't arise — repeated attrs are the only shape.

### Hierarchical relationships — "BREACH is a kind of SECURITY"

This is the one case where markers genuinely add something attributes don't.
SLF4J markers can have child references; a filter on `SECURITY` catches every
descendant marker without enumerating them.

```ecstasy
// lib_logging — DAG via Marker.add
Marker SECURITY = MarkerFactory.getMarker("SECURITY");
Marker BREACH   = MarkerFactory.getMarker("BREACH");
SECURITY.add(BREACH);

logger.atError().addMarker(BREACH).log("token leak detected");

// any filter configured to match SECURITY *also* matches BREACH:
assert SECURITY.contains(BREACH);
```

```ecstasy
// lib_slogging — emit both names; the filter does prefix matching
logger.error("token leak detected", [
        Attr.of("category",       "SECURITY.BREACH"),
        Attr.of("parentCategory", "SECURITY"),         // optional, helps simple filters
]);
```

**This is the strongest argument for keeping markers in `lib_logging`** — the
DAG model is genuinely more expressive than the slog equivalent. **It is also
the marker feature production code rarely uses.** Most operational filters are
`category == "SECURITY.*"` glob matches at the cloud-product level, not
transitive containment queries inside the application.

### Sink/handler-side routing — "send audit events to a separate file"

```ecstasy
// lib_logging — a sink that filters on event.markers
const AuditFilteringLogSink(LogSink delegate, String requiredMarkerName)
        implements LogSink {
    @Override
    Boolean isEnabled(String loggerName, Level level, Marker? marker = Null) =
            marker?.containsName(requiredMarkerName) ?: False;
    @Override
    void log(LogEvent event) {
        for (Marker m : event.markers) {
            if (m.containsName(requiredMarkerName)) {
                delegate.log(event);
                return;
            }
        }
    }
}

new AuditFilteringLogSink(new FileLogSink("/var/log/audit.log"), "AUDIT");
```

```ecstasy
// lib_slogging — a handler that filters on attrs
const AuditFilteringHandler(Handler delegate, String requiredKey)
        implements Handler {
    @Override
    Boolean enabled(Level level) = delegate.enabled(level);
    @Override
    void handle(Record record) {
        for (Attr a : record.attrs) {
            if (a.key == requiredKey && a.value == True) {
                delegate.handle(record);
                return;
            }
        }
    }
    @Override Handler withAttrs(Attr[] attrs) = this;
    @Override Handler withGroup(String name)  = this;
}

new AuditFilteringHandler(new FileHandler("/var/log/audit.log"), "audit");
```

Both implementations are about the same length. The slog one is slightly
simpler (no nested `containsName` walk on the marker DAG). The SLF4J one is
more powerful (matches transitively).

### Programmatic categorisation from a dynamic source

When the category isn't known at compile time (e.g. it's read from a request
header):

```ecstasy
// lib_logging
String  catName = request.header("x-log-category") ?: "DEFAULT";
Marker  cat     = MarkerFactory.getMarker(catName);   // interned by name
logger.atInfo().addMarker(cat).log("event");
```

```ecstasy
// lib_slogging
String catName = request.header("x-log-category") ?: "DEFAULT";
logger.info("event", [Attr.of("category", catName)]);
```

The marker version interns by name, so repeated requests for `"AUDIT"` get
the same `Marker` instance — slightly cheaper to compare against in a filter.
The attribute version allocates a new `String` value each time. For most
production workloads this difference is too small to measure.

## Summary table

| Concern | `lib_logging` (markers) | `lib_slogging` (attrs) |
|---|---|---|
| Single category | `addMarker(M)` | `Attr.of("k", v)` |
| Multiple categories | `addMarker(M1); addMarker(M2)` | `[Attr.of(...), Attr.of(...)]` |
| Hierarchical (DAG) categories | `parent.add(child); marker.contains(other)` | dotted naming + prefix filter |
| Sink-side routing | `marker?.containsName(X)` | `attr.key == X && attr.value == True` |
| Dynamic/runtime categorisation | `MarkerFactory.getMarker(name)` (interned) | `Attr.of("category", name)` |
| Identity comparison cost | reference equality on interned Marker | string equality on `key`/`value` |
| Cloud-side rendering | usually emitted as `labels.{name}` | usually emitted as a top-level JSON field |
| Familiar to Java/JVM teams | yes | no |
| Familiar to Go / cloud-native teams | no | yes |
| Forces a separate API concept | yes | no |

## Bottom line

For an **SLF4J-shaped library**, markers are part of the contract. Production
Logback configs filter on markers; existing code uses `MarkerFactory`; the
fluent builder's `.addMarker(...)` is the standard idiom. Dropping it forces
JVM users to rewrite their mental model, which is exactly what an SLF4J-shaped
library is trying to avoid. **`lib_logging` keeps multi-marker support.**

For a **slog-shaped library**, importing markers would be the wrong move.
slog's single-concept attribute model is the half of its design that *is*
worth copying — markers are a separate type for what is otherwise just a
string-keyed flag, and the Go ecosystem deliberately voted against them.
**`lib_slogging` ships without markers.** Categorisation is
`Attr.of("category", "AUDIT")` and DAG queries are simulated by either dotted
naming + prefix-match filters in the handler, or by emitting both `category`
and `parentCategory` attrs.

If the comparison surfaces "we want markers in the slog-shaped library," that
would be a strong signal the slog design is *not* the right fit and the SLF4J
shape should win — because at that point the simplification motivating slog
has been undone.

---

Previous: [`../lib-logging-vs-lib-slogging.md`](../lib-logging-vs-lib-slogging.md) | Up: [`../README.md`](../README.md)
