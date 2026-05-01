# `lib_logging` vs `lib_slogging` — design comparison

Two parallel XDK logging libraries, both modelling the same end-to-end functionality
but each shaped after a different prior-art tradition:

- **`lib_logging`** — modelled on SLF4J 2.x + Logback. Familiar to anyone with
  Java/Kotlin/Scala experience. Mature ecosystem semantics: `Logger.info("hello {}",
  arg)`, `MDC.put`, `MarkerFactory.getMarker`, fluent `.atInfo()` builder, sink/appender
  split, severity-named levels.
- **`lib_slogging`** — modelled on Go 1.21's `log/slog`. The most modern "well-known"
  structured logger. Attribute-first API: `logger.Info("hello", "key", value)` or
  `logger.LogAttrs(...)`, derived loggers via `With(...)`, no MDC, no markers,
  extensible integer levels, group nesting.

Both libraries target the same set of features end-to-end so a reviewer can pick
either, drop it into an Ecstasy program, and get the equivalent operational
capability. The point of carrying two implementations is not to ship both forever —
it's to let the XTC language designers and prospective users see the two designs
side-by-side, in real Ecstasy, and tell us which one fits the language better.

This document:

1. shows the same example written in both APIs;
2. compares the two designs across nine concrete axes;
3. analyses fit with Ecstasy idioms (`const` / `service` / `SharedContext` / fluent
   builders);
4. lists the reviewer questions that motivate the experiment;
5. records a tentative recommendation, knowing it may shift after review.

> Status note: `lib_slogging` is currently a skeleton (interfaces + a TextHandler stub).
> The SLF4J-shaped library is roughly feature-complete (47 unit tests passing). See
> `open-questions.md` for the explicit tracking list of items still missing on each
> side; we do not ask reviewers to compare designs until both libraries reach the same
> waterline.

---

## 1. Same scenario in both APIs

A nontrivial example: an HTTP request handler logs request entry, attaches a
correlation id and user id to a context that propagates into a child fiber, and
records a structured event with an exception cause.

### SLF4J-shaped (`lib_logging`)

```ecstasy
import logging.Logger;
import logging.MDC;
import logging.MarkerFactory;

@Inject Logger logger;
@Inject MDC    mdc;

void handle(Request req) {
    mdc.put("requestId", req.id);
    mdc.put("user",      req.userId);
    try {
        logger.info("processing {} {}", [req.method, req.path]);
        process(req);
    } catch (Exception e) {
        logger.atError()
              .setMessage("request failed")
              .addMarker(MarkerFactory.getMarker("AUDIT"))
              .addKeyValue("status", 500)
              .setCause(e)
              .log();
    } finally {
        mdc.clear();
    }
}
```

### slog-shaped (`lib_slogging`)

```ecstasy
import slogging.Logger;
import slogging.Attr;

@Inject Logger logger;

void handle(Request req) {
    Logger reqLog = logger.with([
        Attr.of("requestId", req.id),
        Attr.of("user",      req.userId),
    ]);
    try {
        reqLog.info("processing", [
            Attr.of("method", req.method),
            Attr.of("path",   req.path),
        ]);
        process(req);
    } catch (Exception e) {
        reqLog.error("request failed", [
            Attr.of("audit",  True),
            Attr.of("status", 500),
            Attr.of("err",    e),
        ]);
    }
}
```

Both produce equivalent structured output. The shapes diverge in how
*correlation* (per-request id) and *categorisation* (audit channel) are expressed.

---

## 2. Per-axis comparison

| Axis | `lib_logging` (SLF4J 2.x) | `lib_slogging` (Go slog) |
|---|---|---|
| **Levels** | Five named: `Trace`, `Debug`, `Info`, `Warn`, `Error` (+ `Off`). Closed enum. | Integer `Level(severity)` with named constants `Debug=-4`, `Info=0`, `Warn=4`, `Error=8`. Open: callers can define `Level(2, "NOTICE")`. |
| **Context propagation** | Per-fiber `MDC` (`SharedContext`-backed map). Implicit — every emission snapshots it. | Explicit derived loggers via `Logger.with(attrs)`; no thread-local. Optionally a fiber-scoped logger via `SharedContext<Logger>`. |
| **Structured data** | `Marker` for category, `addKeyValue("k", v)` for KV pairs, `arguments` for `{}` substitution slots. Three concepts. | One concept: `Attr(key, value)`. Attributes are the first-class carrier. |
| **Message** | Templated: `info("user {} did {}", [name, action])`. SLF4J-style `{}` placeholders. | Free-form string + attrs separately. No interpolation. |
| **Sinks / handlers** | `LogSink.isEnabled(name, level, marker)`, `LogSink.log(event)`. Two methods. | `Handler.enabled(level)`, `Handler.handle(record)`, `Handler.withAttrs(attrs)`, `Handler.withGroup(name)`. Four methods — handler can pre-resolve attrs at derivation time. |
| **Logger naming** | Hierarchical names: `logger.named("payments")`, `payments.named("stripe")` → `"payments.stripe"`. SLF4J idiom. | Loggers are uniform; categorisation lives in attrs (`"component" -> "payments.stripe"`). |
| **Source location** | Not captured by default. | `Record.pc`/`Record.source` capture is opt-in but a first-class concern. |
| **Async / batching** | Wrapper sink (`AsyncLogSink`, future) drains a bounded queue. | Wrapper handler — same shape. |
| **Familiarity bench** | Java/Kotlin/Scala. Anyone who has done structured logging on the JVM. | Go (1.21+). Increasingly the modern go-to in cloud-native code. |

---

## 3. Per-axis analysis

### 3.1 Levels — closed enum vs open integer

SLF4J's five levels are an industry consensus, easy to pattern-match on, and remove a
class of "what's the right level for this?" questions because the choice is small.
The cost: introducing `Notice`, `Critical`, or `Audit` requires either reinterpreting
an existing level or adding a new one to the enum (which is a breaking change).

slog's integer levels accept extension without library changes. Custom levels
compose with comparison; tooling that filters on `>= Warn` keeps working when a
user adds `Notice = 2`. The cost: there is no canonical `Notice`; two libraries can
both define `Notice` at different integer values and silently disagree.

**Ecstasy fit.** Both designs are expressible. The slog model is closer to how
Ecstasy already treats severity in many places (raw `Int`-shaped severity in
`Console`, `xunit_engine` levels), and integrates naturally with the
`Comparable`/`Orderable` machinery. The SLF4J model is a `const` enum with explicit
methods — also clean, but rigidly closed.

### 3.2 Context propagation — `MDC` vs `With(attrs)`

This is the single biggest design fork.

**`MDC`.** Implicit. Every log call captures the current `MDC` snapshot regardless of
whether the calling code knows about it. This is enormously convenient for
cross-cutting concerns (request ids, tracing) — the framework injects ids once and
every downstream log line gets them for free. The cost is hidden state: code looks
pure but isn't, you can't tell from reading a log call whether it will pick up an MDC
key.

In `lib_logging` the implementation is `SharedContext`-backed for per-fiber scope, so
unlike Java MDC it doesn't leak into sibling fibers. That removes the worst class of
real-world MDC bugs. But the *implicitness* remains.

**`With(attrs)`.** Explicit. Code reads "this logger carries these attributes." When a
function wants to log with a request id, it must accept the request-aware logger —
either as a parameter or as a field set up at construction. There is no global state.
The cost is verbosity: every layer that wants the request id either accepts a
`Logger` parameter or accepts a context object that can produce one.

In Go, slog reaches for `context.Context` to pass loggers through the call graph
without adding parameters everywhere. The Ecstasy equivalent is `SharedContext<Logger>`
or service-local `Logger` fields — feasible but not idiomatic yet.

**Ecstasy fit.** The MDC story is *already implemented and works*: `const MDC` over an
immutable-map `SharedContext` is small (~50 lines) and per-fiber. The slog story is
*mechanically simple* (just derive a new const Logger with extra attrs) but loses the
"library code that doesn't know about the request id still gets the request id" trick
unless the Logger parameter threads everywhere. We want reviewer thoughts on which
side of this tradeoff Ecstasy programs should be on.

### 3.3 Structured data — three concepts vs one

#### Aside: what is a log marker?

A **log marker** is a *named tag* attached to a log record that gives sinks a
first-class "category" to filter on, separate from the message text and the
severity level. SLF4J introduced the concept in 2005; the same idea exists in
Log4j 2 (`Marker`) and is conceptually similar to `MDC` keys in Logback config or
"properties" in log4net — but markers travel *with the event* whereas MDC travels
*with the thread*.

Concrete use cases — all from production deployments using SLF4J/Logback:

- **`AUDIT`**: route every event tagged with this marker to an *append-only* audit
  log that satisfies SOX / PCI-DSS / HIPAA retention rules. The same logger writes
  ordinary events to a normal file; the audit log only sees marker-tagged events.
- **`SECURITY`** / **`SECURITY.BREACH`**: forward security-relevant events to a
  SIEM (Splunk, Elastic, Datadog) on a separate channel, optionally with stricter
  encryption / redaction filters in front.
- **`SLOW_QUERY`** / **`PERFORMANCE`**: emit perf-suspect events to an APM
  pipeline (New Relic, AppDynamics) without the noise of regular debug logs.
- **`BILLING`** / **`CONFIDENTIAL`**: route financial-impact events to a system
  with longer retention and tighter access controls.
- **`PUBLIC_API_REQUEST`**: tee API-boundary events to a separate access log used
  for traffic analysis and rate-limit tuning.

Markers also support a *parent/child DAG*: in SLF4J, `SECURITY.contains(BREACH)`
returns true if `BREACH` was added as a child of `SECURITY`. A filter configured
on `SECURITY` therefore catches any descendant marker without needing to know
their names. This is the marker model's only real edge over a plain
"`is_security: true`" attribute — and almost no production code uses it.

#### Who actually uses markers?

- **Logback** (the dominant SLF4J binding): markers are first-class. `TurboFilter`
  and `EvaluatorFilter` can route events by marker. Most JSON encoders emit the
  marker name as a structured field. Used in **almost every Java enterprise app**
  built between roughly 2010 and 2020.
- **Log4j 2**: `MarkerManager` interns markers; `MarkerFilter` accepts/denies by
  marker name. Used heavily in Apache projects (Kafka, Cassandra, Solr) and in
  legacy enterprise codebases.
- **SLF4J 2.x** itself: `Logger.atInfo().addMarker(...)` is the canonical fluent
  builder usage, and `LoggingEvent.getMarkers(): List<Marker>` (the multi-marker
  shape we just adopted) is the v2 wire format.
- **Apache Camel, Spring Boot, JBoss / WildFly, ActiveMQ, Hazelcast**: all surface
  marker-based filtering in their default Logback / Log4j configurations.

Outside the JVM:

- **Go's `log/slog`** *deliberately rejects* markers. Categorisation is just
  `slog.Bool("audit", true)` or `slog.String("category", "security")`.
- **Python's `logging`**: no markers. Categories live in the logger name
  hierarchy (`security.breach`) and in `extra={...}` dict attrs.
- **.NET's `Microsoft.Extensions.Logging`**: no markers. Categories are logger
  names; structured fields go in the `state` object.
- **Rust's `tracing`**: no markers. `target` field on each event approximates the
  same idea; otherwise use `field::display(...)` attributes.

The pattern: **markers are a JVM-ecosystem feature**, born of SLF4J, used widely
*inside* the JVM, and deliberately excluded from every well-known modern logger
designed since 2015.

#### What is the actual payoff?

Three things, in decreasing order of how often they justify the API surface:

1. **Backend routing.** A sink looks at the marker and decides where the event
   goes (audit log vs. main log vs. SIEM). This is how 90% of production marker
   usage works in practice. It is the *only* real pull for keeping markers.
2. **Marker DAG queries.** `SECURITY.contains(BREACH)` and `marker.iterator`
   walking parent references. Mostly unused outside of niche audit frameworks.
3. **Marker as a typed-tag**: a way to say "this is the audit log line" without
   spelling out a `"category"` string. Same as a string-keyed boolean attr; the
   "type-ness" is a thin gain.

Backend routing is real, but it is the same operation as filtering on
`attrs["category"] == "AUDIT"`. The marker concept is *one type to learn* and
gives you a slightly cheaper filter (interned reference identity vs string
equality). Whether that's worth a separate type in the API depends on whether
the audience reflexively reaches for `MarkerFactory.getMarker("AUDIT")` (JVM
veterans: yes; everyone else: no).

#### My opinion: keep markers in `lib_logging`, drop them in `lib_slogging`

For an **SLF4J-shaped library**, removing markers would be a betrayal of the
concept "instantly familiar to anyone who has used SLF4J 2.x." Production
Logback configs filter on markers; existing code uses `MarkerFactory`; the
fluent builder's `.addMarker(...)` is the standard idiom. Dropping it forces
JVM-veteran users to rewrite their mental model, which is the explicit thing
this library is trying not to do. **`lib_logging` keeps multi-marker support.**

For a **slog-shaped library**, importing markers would be the wrong move. slog's
single-concept attribute model is the half of its design that *is* worth
copying — markers are a separate type for what is otherwise just a string-keyed
flag, and the Go ecosystem deliberately voted against them. **`lib_slogging`
ships without markers.** Categorisation is `Attr.of("category", "AUDIT")` and
DAG queries are simulated by either dotted naming + prefix-match filters in the
handler, or by emitting both `category` and `parentCategory` attrs.

If the comparison surfaces "we want markers in the slog-shaped library," that
would be a strong signal the slog design is *not* the right fit and the SLF4J
shape should win — because at that point the simplification motivating slog has
been undone.

#### Side-by-side: how each library expresses common marker patterns

The four cases below cover what marker users actually do in production. Each
shows the SLF4J idiom (`lib_logging`) and the slog equivalent (`lib_slogging`).

##### Pattern 1 — single category tag ("this is an audit event")

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

##### Pattern 2 — multiple markers on one event

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

Multi-marker is what motivated W-1 in `open-questions.md`. In slog the
question doesn't arise — repeated attrs are the only shape.

##### Pattern 3 — hierarchical relationships ("BREACH is a kind of SECURITY")

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

**This is the strongest argument for keeping markers in `lib_logging`** —
the DAG model is genuinely more expressive than the slog equivalent. **It is
also the marker feature production code rarely uses.** Most operational
filters are `category == "SECURITY.*"` glob matches at the cloud-product
level, not transitive containment queries inside the application.

##### Pattern 4 — sink/handler-side routing ("send audit events to a separate file")

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

##### Pattern 5 — programmatic categorisation from a dynamic source

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

#### A summary table

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

#### Bottom line on markers

`lib_slogging` does not have markers because *slog deliberately doesn't*. Every
marker use case maps onto attributes, with one corner the attribute model
loses (the DAG containment query). Whether that loss is worth keeping a
separate type for depends on the audience.

The two libraries treat this differently on purpose: `lib_logging` carries
markers because SLF4J users expect them and the marker-aware Cloud Logging
adapter exists; `lib_slogging` omits markers because slog users expect them
to be omitted and the attribute-only API is the half of slog that's worth
copying.

---

#### The three concepts in `lib_logging`

SLF4J carries:

1. `arguments` — positional substitution into `{}` slots in the message template.
2. `marker` — categorisation (audit, security, billing).
3. `keyValues` — explicit structured pairs (added in SLF4J 2.x).

These were *bolted on over time* and the API shows it: the position of an `Object[]`
argument in the call signature is not the same as the position of a builder
`addKeyValue`. Some sinks (SimpleLogger) only use the message; some (Logback JSON
encoders) only use `keyValues` and ignore `arguments`.

slog has *one* concept: `Attr`. Everything is an attribute. The message is a free
string; everything structured is an attribute; categories are attributes; the cause
of an error is an attribute. There is no "is this rendered into the message text or
emitted as a structured field" question — the handler decides per attr key.

**Ecstasy fit.** slog wins on conceptual economy. SLF4J wins on familiarity and on
"you can have nicely-formatted free-text logs without writing handler code." A real
Ecstasy program will probably want both — which the SLF4J model gives explicitly and
the slog model gives via "the Text handler renders the message verbatim and appends
attrs."

### 3.4 Message — `{}` template vs no interpolation

SLF4J: `info("user {} did {}", [name, action])`. Lazy substitution: if the level is
disabled the `format` step is skipped. Familiar and concise.

slog: `Info("user did action", "name", name, "action", action)` — no interpolation.
The handler decides how to render. Output is typically `msg="user did action"
name=alice action=login` for the text handler.

**Ecstasy fit.** Both are easy. SLF4J's `{}` is more compact for human-readable
output; slog's structure-first is easier for machine consumption. The Ecstasy default
arguments and varargs make either ergonomic.

### 3.5 Sink/handler shape — minimal vs richer

`LogSink` has two methods. Everything (level filter, attr resolution, MDC capture,
formatting) happens above the sink. A new sink author writes `isEnabled` and `log`
and is done.

slog `Handler` has four — the extras are `withAttrs(attrs)` and `withGroup(name)`,
which let the *handler* pre-resolve attached attributes when the user calls
`logger.With(...)`. This means a JSON handler can fold `requestId` into a
namespace prefix once, not every emission. It's a real performance win for
high-volume structured logging.

**Ecstasy fit.** The slog handler shape is more work to implement but removes
allocation on the hot path for derived loggers. Whether that matters in Ecstasy
depends on workload. The lib_slogging skeleton currently exposes both methods so
the cost is a couple more lines per handler.

### 3.6 Logger naming — hierarchical vs attribute-based

SLF4J's `logger.named("payments")` gives you a `Logger` whose name is `"<parent>.
payments"`. Hierarchical names are how Logback configuration ("set
`com.example.payments` to `Debug`") works. It's a category system that doesn't need
attrs because the *name* IS the category.

slog has no notion of named loggers. You'd express "this is the payments component"
as an attribute that travels via `With`. Filtering happens by attr equality, not
name-prefix matching.

**Ecstasy fit.** SLF4J names map cleanly to Ecstasy module / package names, which is
a feature: a `@Inject Logger` resolved with the enclosing module's qualified name is
how SLF4J users intuitively expect it to work. slog forfeits this for uniformity.

### 3.7 Source location

Out of scope for both libraries' v0, but worth noting: slog explicitly carries a
`pc`/`source` field on `Record` and the API contract says handlers may render it.
SLF4J leaves it to the sink. This isn't an architectural difference; it's a contract
difference.

### 3.8 Async / batching

Same shape in both: a wrapper handler/sink owns a bounded queue and drains on a
worker fiber. Lives in a follower module (`lib_logging_logback` /
`lib_slogging_async`), not the base library.

### 3.9 Familiarity

A subjective dimension that nonetheless matters for adoption. SLF4J is the dominant
JVM logging API; Java/Kotlin engineers reach for it without thinking. slog is the
modern Go default; cloud-native engineers reach for it. Ecstasy targets a developer
audience that will overlap heavily with both.

---

## 4. Ecstasy idiom fit — where the languages bite

### 4.1 `const` vs `service` for the building blocks

In `lib_logging`:

- `BasicLogger` is a `const` (so `@Inject Logger` lives on the caller's fiber, MDC
  works).
- `MDC` is a `const` over `SharedContext<immutable Map>`, copy-on-write.
- Sinks split: stateless ones (`NoopLogSink`, `ConsoleLogSink`) are `const`;
  stateful ones (`MemoryLogSink`, `ListLogSink`) are `service`.

In `lib_slogging`:

- `Logger` is a `const` carrying `Handler handler` and `Attr[] attrs`. Derivation
  via `with(...)` returns a new `const Logger` — naturally immutable.
- There is no MDC, so no `SharedContext`-flavoured cross-cutting state.
- Handlers split the same way: stateless adapters (`TextHandler`, `JSONHandler`,
  `NopHandler`) are `const`; stateful collectors (`MemoryHandler`) are `service`.

The slog model is *substantially* simpler on the const side because the absence of
MDC removes one of the two interactions with `SharedContext`. The other interaction
— "I want a logger that propagates through fibers without threading a parameter" —
is achievable with `SharedContext<Logger>` if/when needed, but isn't required for the
core API.

### 4.2 Fluent builder vs varargs

SLF4J's fluent `.atInfo().setMessage(...).addKeyValue(...).log()` requires a
`LoggingEventBuilder` interface and a `BasicEventBuilder` implementation, plus a
no-op variant for short-circuiting disabled levels. That's three types of machinery
for one API surface.

slog's `LogAttrs(level, msg, ...attrs)` doesn't need a builder at all — varargs of
typed `Attr` values cover the same ground. Fewer types, fewer allocations on the
disabled-level path (because the caller usually doesn't construct attrs unless the
level is enabled, via `if logger.Enabled(...) { ... }`).

**Ecstasy fit.** The fluent builder feels Java-ish. The slog varargs feel closer to
how Ecstasy method signatures already work. But Ecstasy's default-argument support
already collapses many of SLF4J's fluent-builder use cases into one method call, so
the fluent surface is smaller in `lib_logging` than the SLF4J Java original.

### 4.3 Marker subgraph vs attribute uniformity

`Marker` in SLF4J/`lib_logging` is its own type with `add` (parent/child references)
and `contains` (transitive query). It also has identity (`MarkerFactory.getMarker`
interns). That's a small DAG type.

slog has no marker concept. The same use cases (filter audit-only events) are
handled by `Attr.of("audit", True)` and a handler that filters on that attr.

**Ecstasy fit.** Markers add a separate type; attributes don't. The marker model is
slightly more efficient (one interned reference vs a string equality check), but the
attribute model needs less surface area.

### 4.4 Compiler-side default-name injection

SLF4J expects `LoggerFactory.getLogger(MyClass.class)` to resolve to a class- or
module-named logger. `lib_logging` currently delegates that to a future compiler
change (Stage 4 in `runtime-implementation-plan.md`). Without it, `@Inject Logger`
gets a fixed-name root logger and you call `.named("...")` to derive children.

slog has no such machinery — there is one process-wide default Logger and you derive
from it. No naming hierarchy, no compiler change.

**Ecstasy fit.** slog removes a "do we make the compiler smarter?" question
entirely. SLF4J keeps the door open for the nicer ergonomics if the compiler change
lands.

---

## 5. What we want reviewer feedback on

1. **`MDC` worth keeping?** SLF4J's per-fiber MDC is the most opinionated piece of
   this comparison. It costs ~50 lines of `SharedContext` machinery + a small
   compiler ask. The slog approach (explicit `with(attrs)`) removes the implicit
   context entirely. Which side of the implicit/explicit fork should an Ecstasy
   library live on?

2. **One sink interface or two?** Both designs end up with sinks/handlers split into
   "stateless `const`" and "stateful `service`" implementations. We currently allow
   both shapes under one interface. Should the language convention be one interface
   per shape (forcing implementers to declare intent at the type level)?

3. **Fluent builder pull-back.** SLF4J's `.atInfo()...` family is a third API
   surface that pays for cases the per-level methods can't easily express. With
   Ecstasy default arguments and varargs, the fluent builder may be unnecessary.
   Should `lib_logging`'s fluent surface go away?

4. **Hierarchical names.** Are class- / module-qualified hierarchical logger names
   worth a compiler change to make `@Inject Logger` give you a class-named logger
   automatically? Or is the slog model — one logger, attributes for everything else —
   what we want for new Ecstasy code?

5. **Markers vs attrs.** Do reviewers see real value in the marker DAG model (audit
   marker, security marker, etc.) over a string-keyed attribute? `lib_logging`
   carries the marker type today; `lib_slogging` does not.

6. **Levels — closed enum vs extensible Int.** Five named levels are familiar but
   inflexible; integer levels are open but lose canonicality. Which idiom should
   Ecstasy converge on across logging, telemetry, and tracing libraries?

7. **`@Inject` ergonomics.** Both libraries currently funnel through `@Inject Logger
   logger;`. Do reviewers want this as the entry point, or should one of the two
   libraries demonstrate a non-injection idiom (a top-level `Logger.default` const,
   say) for comparison?

8. **Source location.** slog captures it; SLF4J doesn't. Is this a v0 concern in
   Ecstasy or can we defer to a follower release?

9. **Performance baseline.** Neither library has been profiled. If reviewers want
   benchmarks before deciding, name the workload (sustained 100k events/sec to a
   no-op sink? burst with disabled levels? 95th-percentile latency at the call
   site?) and we'll add it to the comparison.

10. **Async / batching home.** Both libraries punt async to a follower module. Is
    that the right call, or should one of the two ship a default async wrapper to
    make "log to network" easier out of the box?

11. **Should Ecstasy ship two logging libraries permanently?** The Java ecosystem has
    two large facades (SLF4J, Log4j 2 API) and is by no means ergonomic for it.
    Carrying both `lib_logging` and `lib_slogging` long-term has the same risk. The
    intent of this experiment is to *pick one* and move on. We want reviewer input
    on which.

---

## 6. Tentative recommendation

We lean — softly, fully expecting reviewer pushback — toward **`lib_slogging` as the
canonical Ecstasy logging library, with one borrowed feature from `lib_logging`**:
the per-fiber `SharedContext`-backed MDC, recast as `slogging.Context` and *also*
optional / never the only path. The reasoning, ranked:

1. **Conceptual economy.** One concept (`Attr`) covering markers, structured KVs,
   message slots, and exception causes is meaningfully simpler than three. Less for
   newcomers to learn, less for sink authors to handle.
2. **Modern fit.** slog is the design that emerged after a decade of structured-
   logging wins in Java/Go. SLF4J is the design that those wins were retrofitted
   onto. The 2026-era language probably wants the post-retrofit shape.
3. **Less compiler ask.** No hierarchical naming, no fluent builder, no marker DAG.
   Smaller surface to support.
4. **MDC is genuinely useful.** Threading a request-aware logger through a
   call graph is real work that real codebases get wrong. A `slogging.Context` with
   `withAttrs(...)` semantics, *opt in*, gives you the SLF4J win without the
   "everything is implicit" cost.

A third option — **hybrid SLF4J-facade with slog-shaped event model** — is
worth flagging once but not pushing for v0. Concretely: keep `Logger`,
`Marker`, `MDC`, the fluent builder; replace the free-form `Object[] arguments`
on `LogEvent` with a typed `Attr[]`; have `MessageFormatter` resolve `{}`
placeholders against `attrs` by index, but also have a `JsonLineLogSink`
render `attrs` directly as a JSON object. SLF4J users get their familiar
shape; cloud-side JSON pipelines get a clean wire format. The cost is
strictly more complexity than either pure design — two parallel mental
models in one library — so we don't recommend it for v0. The right time to
revisit is after one of the two pure designs is shipping and we know which
axis we wish we had more of.

Counter-arguments worth taking seriously:

- **Familiarity.** A great deal of Ecstasy's audience comes from JVM, where SLF4J is
  reflexive. Forcing them to relearn a logging API for no reason other than design
  preference is friction.
- **Markers and named loggers are not just "structured data dressed up funny" — they
  enable per-category configuration that's awkward to express as attribute filters
  in a config file.**
- **Fluent builders genuinely shine when the keys aren't statically known** — e.g.
  building a log line from a request whose fields vary. Varargs handle this less
  cleanly.

The recommendation is therefore tentative. We expect to revisit it once the
reviewers — particularly the XTC language team — weigh in on questions Q-D1 through
Q-D6 in `open-questions.md`.

---

## 7. What lives in this PR

This branch (`lagergren/logging`) contains:

- `lib_logging/` — the SLF4J-shaped library, near-feature-complete (47 unit tests
  passing). Tracking list of remaining items: `open-questions.md` § "SLF4J-style
  library work not yet implemented".
- `lib_slogging/` — the slog-shaped library, currently a **skeleton** (interfaces +
  one stub `TextHandler`). Full implementation gated on reviewer feedback on this
  document.
- This document — the design comparison and the explicit list of reviewer questions.
- `open-questions.md` — the unified question list (all "Q-D*" items added in this PR
  call out language-design questions specifically).
- `design.md` — the SLF4J-side design, now with the resolved sink-type rule.

We do not propose merging until reviewers have weighed in on at least Q-D6 (which
API shape) and Q-D1 (sink interface convention).


---

_See also [README.md](README.md) for the full doc index and reading paths._
