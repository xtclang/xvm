# `lib_logging` vs `lib_slogging` — design comparison

> **Audience:** reviewers picking the canonical XDK logging API shape.

Two parallel XDK logging libraries model the same end-to-end capability but follow
different prior-art traditions.

They coexist only so this branch can compare the designs. User code should choose one
shape, not mix both as permanent XDK APIs. After review, the chosen injectable logging
shape should graduate as `lib_logging`; `lib_slogging` is a POC name for the Go
`log/slog`-shaped candidate, not a proposal to keep a second default logging facade.

The branch does not introduce a clean XDK build switch between "logging" and
"slogging". During the POC, the meaningful validation is to compile both libraries and
run the two manual injection tests: `TestLogging` for the SLF4J-shaped API and
`TestSLogging` for the slog-shaped API. Runners that only carry one POC module should
still start; the native injector should simply expose the resources for whichever
logging module is present. That tolerance is a temporary comparison aid, not a
statement that both APIs should remain in the XDK.

| Library | Prior art | Core shape |
|---|---|---|
| `lib_logging` | SLF4J 2.x + Logback | Named loggers, `{}` message formatting, MDC, markers, fluent event builders, and a `LogSink` backend boundary. |
| `lib_slogging` | Go 1.21 `log/slog` | Attribute-first events, derived loggers via `with(...)`, open integer levels, grouped attrs, and a `Handler` backend boundary. |

Both libraries target the same set of features end-to-end so a reviewer can pick
either, drop it into an Ecstasy program, and get the equivalent operational
capability. The point of carrying two implementations is not to ship both forever —
it's to let the XTC language designers and prospective users see the two designs
side-by-side, in real Ecstasy, and tell us which one fits the language better.

The sections below show the same scenario in both APIs, compare the designs across
the axes that matter for XDK code, and end with the reviewer questions that should
decide which shape survives. Direct links from each Ecstasy type to its SLF4J or
Go `log/slog` counterpart are in [`api-cross-reference.md`](api-cross-reference.md).

> Status note: both libraries are working comparison POCs. `lib_logging` is the
> recommended canonical facade and has the fuller SLF4J surface (70 focused XTC test
> methods plus the dedicated injected manual demo), including async/composite/hierarchical/JSON
> backend building blocks. `lib_slogging` has the more compact slog surface (41 focused
> XTC test methods plus the dedicated injected manual demo), with runtime injection, async
> handler support, `lib_json` JSON rendering, redaction options, source metadata,
> context binding, and handler derivation semantics implemented.

---

## Fast conclusion

Use this section for the first pass; the later sections are the depth-first rationale.

Choose **`lib_logging`** as the canonical XDK facade unless review explicitly decides
Ecstasy should prefer the Go `slog` mental model. The deciding point is operational
familiarity: `lib_logging` gives users named loggers, `{}` formatting, markers, MDC,
fluent event builders, and a Logback-style `LogSink` boundary, while still supporting
modern structured JSON/cloud output through `LogEvent.keyValues`.

`lib_slogging` proves the alternative is viable and clean. It is smaller because all
structured data is `Attr`, and backend extension is through `Handler`. The cost is that
hierarchical logger names, markers, and message templates are not first-class; they must
be modeled as attrs or helper adapters.

The comparison can be read in two passes:

- **Sections 1-3:** enough to understand the API choice and what exists or does not
  exist in each model.
- **Sections 4-8:** deeper rationale, marker examples, Ecstasy `const`/`service`
  implications, reviewer questions, and exact branch contents.

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
| **Context propagation** | Per-fiber `MDC` (`SharedContext`-backed map). Implicit — every emission snapshots it. | Explicit derived loggers via `Logger.with(attrs)` by default. Optional `LoggerContext` uses `SharedContext<Logger>` when framework/request code wants implicit propagation. |
| **Structured data** | `Marker` for category, `addKeyValue("k", v)` for KV pairs, `arguments` for `{}` substitution slots. Three concepts. | One concept: `Attr(key, value)`. Attributes are the first-class carrier. |
| **Message** | Templated: `info("user {} did {}", [name, action])`. SLF4J-style `{}` placeholders. | Free-form string + attrs separately. No interpolation. |
| **Sinks / handlers** | `LogSink.isEnabled(name, level, marker)`, `LogSink.log(event)`. Two methods. | `Handler.enabled(level)`, `Handler.handle(record)`, `Handler.withAttrs(attrs)`, `Handler.withGroup(name)`. Four methods — handler can pre-resolve attrs at derivation time. |
| **Logger naming** | Hierarchical names: `logger.named("payments.stripe")`. The caller supplies the full category name, like `LoggerFactory.getLogger("...")`. | Loggers are uniform; categorisation lives in attrs (`"component" -> "payments.stripe"`). |
| **Source location** | `Logger.logAt(...)` explicitly populates `LogEvent.sourceFile` / `sourceLine`; automatic call-site capture is future runtime/compiler work. | `Logger.logAt(...)` explicitly populates `Record.sourceFile` / `sourceLine`; automatic call-site capture is future runtime/compiler work. |
| **Async / batching** | `AsyncLogSink` wraps any sink with a bounded queue. | `AsyncHandler` wraps any handler with the same bounded-queue shape. |
| **Familiarity bench** | Java/Kotlin/Scala. Anyone who has done structured logging on the JVM. | Go (1.21+). Increasingly the modern go-to in cloud-native code. |

---

## 3. Exists / Missing Matrix

This is the blunt version of the comparison. "Missing" does not mean impossible; it
means the concept is not first-class in that API and must be modeled with a more general
mechanism such as attributes or a custom backend.

| Capability | SLF4J / Logback | Go `log/slog` | `lib_logging` | `lib_slogging` | Why it matters |
|---|---|---|---|---|---|
| Hierarchical logger names | **Exists.** `LoggerFactory.getLogger(PaymentService.class)`, Logback `<logger name="com.acme.payments" level="DEBUG"/>`. | **No first-class logger name hierarchy.** `Logger.With` and `WithGroup` attach attrs / qualify attr keys; they do not define a logger category string with longest-prefix routing. | **Exists.** `logger.named("com.acme.payments")` plus `HierarchicalLogSink.setLevel(...)`. | **Modeled as attrs/groups.** Use `logger.with([Attr.of("component", "...")])` or `withGroup("payments")`; custom handlers can filter attrs, but there is no built-in prefix tree. | Per-package/per-module level control is an operational staple in Logback configs. |
| Markers | **Exists.** `Marker`, `MarkerFactory`, marker-aware level checks, Logback marker filters. | **No Marker type.** Use attrs such as `"audit"=true` or `"category"="audit"`. | **Exists.** `Marker`, marker DAG, multi-marker fluent events, marker-aware sinks. | **Modeled as attrs.** `Attr.of("audit", True)` is simpler but has no marker containment semantics. | Markers are useful for audit/security routing that should not depend on message text. |
| Message templates | **Exists.** `"processed {}"` with argument arrays and trailing throwable promotion. | **Not the model.** Message is a stable string; variable data is attrs. | **Exists.** `MessageFormatter` implements SLF4J-style `{}` formatting. | **Intentionally absent.** Use `logger.info("processed", [Attr.of("count", count)])`. | SLF4J favors migration familiarity; slog favors structured data over interpolation. |
| Mapped context | **Exists.** `MDC.put(...)`; Logback layouts can render it. | **Different shape.** Context-aware methods accept `context.Context`; persistent fields usually come from `Logger.With(...)`. | **Exists.** `@Inject MDC` backed by `SharedContext`. | **Different shape.** `Logger.with(...)` is explicit; `LoggerContext` is an optional `SharedContext<Logger>` helper. | Request IDs and tenant/user IDs should not be threaded through every logging call by hand. |
| Open custom levels | **No.** SLF4J has the standard fixed ladder. | **Exists.** `slog.Level` is an integer; `Level(2)` is valid. | **No.** Closed enum plus `Off`. | **Exists.** `new Level(2, "NOTICE")`. | Useful for environments with `NOTICE`, `CRITICAL`, or domain-specific levels; less canonical for generic tooling. |
| Handler derivation hooks | **No direct facade equivalent.** Backends configure appenders/loggers; event builders carry per-event data. | **Exists.** `Handler.WithAttrs` and `WithGroup` let handlers pre-resolve context. | **No direct equivalent.** `LogSink` stays two-method and relies on logger names/MDC/builders. | **Exists.** `Handler.withAttrs` / `withGroup`, with `BoundHandler` default semantics. | Helps high-volume structured handlers avoid recomputing prefixes per record. |
| Source location | **Backend-dependent.** Logback can calculate caller data, but SLF4J does not make source a normal facade argument. | **Exists in the record model.** `Record.PC`, `Record.Source()`, `HandlerOptions.AddSource`. | **Explicit API.** `Logger.logAt(...)` populates `LogEvent.sourceFile` / `sourceLine`; compiler capture is future work. | **Explicit API.** `Logger.logAt(...)` populates `Record.sourceFile` / `sourceLine`; compiler capture is future work. | Useful for debugging, but automatic capture has runtime/compiler cost. |
| Attribute replacement / redaction | **Backend concern.** Logback filters/encoders decide. | **Exists in `HandlerOptions.ReplaceAttr`.** | **Exists in shipped JSON sink options.** `JsonLogSinkOptions.redactedKeys`. | **Exists in POC options.** `HandlerOptions.redactedKeys`. | Production JSON output needs redaction without caller code remembering every sink policy. |

### Examples for the two contentious claims

**Hierarchical category routing exists in SLF4J / Logback and `lib_logging`:**

```java
// Java + SLF4J
private static final Logger LOG =
        LoggerFactory.getLogger("com.acme.payments.stripe");
LOG.debug("charged {}", paymentId);
```

```xml
<!-- Logback -->
<logger name="com.acme.payments" level="DEBUG"/>
<root level="INFO"/>
```

```ecstasy
// Ecstasy + lib_logging
HierarchicalLogSink sink = new HierarchicalLogSink(new ConsoleLogSink(), Info);
sink.setLevel("com.acme.payments", Debug);

Logger log = new BasicLogger("root", sink)
        .named("com.acme.payments.stripe");
log.debug("charged {}", [paymentId]);
```

Go `slog` can carry a category, but it is data, not a built-in logger hierarchy:

```go
// Go + slog
log := slog.New(handler).With("component", "com.acme.payments.stripe")
log.Debug("charged", "paymentId", paymentID)
```

```ecstasy
// Ecstasy + lib_slogging
SLogger log = logger.with([
        Attr.of("component", "com.acme.payments.stripe"),
]);
log.debug("charged", [Attr.of("paymentId", paymentId)]);
```

That is perfectly workable for structured output. What it does not give by itself is
Logback's standard longest-prefix rule: "turn on DEBUG for `com.acme.payments` and all
children." A slog handler can implement attr-based filtering, but it is not the same
standard category mechanism.

**Markers exist in SLF4J / Logback and `lib_logging`; slog uses attrs instead:**

```java
// Java + SLF4J
Marker audit = MarkerFactory.getMarker("AUDIT");
LOG.info(audit, "user signed in");
```

```xml
<!-- Logback marker filter -->
<filter class="ch.qos.logback.classic.filter.MarkerFilter">
  <marker>AUDIT</marker>
  <onMatch>ACCEPT</onMatch>
  <onMismatch>DENY</onMismatch>
</filter>
```

```ecstasy
// Ecstasy + lib_logging
Marker audit = MarkerFactory.getMarker("AUDIT");
logger.info("user signed in", marker=audit);
```

```go
// Go + slog: model the same signal as an attribute
logger.Info("user signed in", "audit", true)
```

```ecstasy
// Ecstasy + lib_slogging
logger.info("user signed in", [Attr.of("audit", True)]);
```

The attr version is simpler and often enough. The marker version has marker identity,
marker containment (`SECURITY` can contain `AUDIT`), marker-aware enabled checks, and
direct compatibility with existing Logback marker filters.

### Are Java APIs moving toward slog?

They are moving toward the same structured/fluent direction, but they are not the same
API shape.

| Java API / framework | Looks slog-like because | Still differs from Go slog |
|---|---|---|
| **SLF4J 2.x fluent API** | `logger.atInfo().addKeyValue("k", v).log("msg")` gives a message plus structured fields, close to slog's `Info("msg", "k", v)`. | Keeps SLF4J concepts: named loggers, `{}` message templates, markers, MDC, and backend binding through Logback/other providers. |
| **Log4j 2 API** | Has `LOGGER.atInfo().withMarker(...).withThrowable(...).withLocation().log(...)`, custom levels, Thread Context, JSON layouts, and rich message types. | It is a broader Java logging API, not an attr-only slog model. Markers, logger names, message factories, and config/plugins remain first-class. |
| **Google Flogger** | Uses a fluent API: `logger.atInfo().withCause(e).log("value: %s", value)` and supports lazy/rate-limited logging. | It is optimized for self-documenting Java call sites and performance, not Go's `Handler` / `Attr` / `Record` architecture. |
| **Logback + logstash-logback-encoder / structured arguments** | Many teams keep SLF4J and add structured fields at the call site plus a JSON encoder at the backend. | The facade remains SLF4J/Logback-shaped; structured data is added to that ecosystem rather than replacing it with slog semantics. |

So the designs are not completely different things. They are two answers to the same
industry pressure: log events need structured fields. Go made attrs the central model.
Modern Java mostly retrofitted structured/fluent APIs onto the existing logger-name,
marker, MDC, appender, and configuration ecosystem.

That is why `lib_logging` can be both SLF4J-shaped and modern: it keeps the Java
operational model but includes SLF4J 2-style `addKeyValue`, JSON sinks, redaction, and
async/composite/hierarchical backend primitives.

---

## 4. Per-axis analysis

### 4.1 Levels — closed enum vs open integer

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

### 4.2 Context propagation — `MDC` vs `With(attrs)`

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

**`With(attrs)`.** Explicit. Code reads "derive a handler that always includes these
attributes." When a function wants to log with a request id, it normally accepts the
request-aware logger — either as a parameter or as a field set up at construction.
`LoggerContext` exists for framework/request code that needs implicit propagation.

In Go, slog reaches for `context.Context` to pass loggers through the call graph
without adding parameters everywhere. The Ecstasy equivalent in this POC is
`LoggerContext`, a small `SharedContext<Logger>` wrapper.

**Ecstasy fit.** The MDC story is *already implemented and works*: `const MDC` over an
immutable-map `SharedContext` is small and per-fiber. The slog story is similarly
small: explicit `Logger.with(...)` for normal code, optional `LoggerContext` for
framework code that wants the "library code still sees the request logger" trick. We
want reviewer thoughts on which side of this tradeoff Ecstasy programs should be on.

### 4.3 Structured data — three concepts vs one

#### Markers — what they are and which library keeps them

A **log marker** is a named, event-scoped tag SLF4J/Logback users reach for
when categorising events for backend routing (audit/security/billing channels).
The full explainer — what they are, who uses them, the patterns they cover, and
the cloud-side rendering — lives in [`concepts/markers.md`](concepts/markers.md).
The summary, for the comparison axis here:

- **`lib_logging` keeps markers.** SLF4J users expect them; Logback configs
  filter on them; the marker-aware Cloud Logging adapter exists.
- **`lib_slogging` drops markers.** Go `slog` deliberately omits them; every
  use case maps to attributes, with one corner the attribute model loses (the
  DAG containment query). Importing markers into a slog-shaped library would
  undo the simplification that motivates copying slog in the first place.

If the comparison surfaces "we want markers in the slog-shaped library", that
is a strong signal the slog design is not the right fit and the SLF4J shape
should win.


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

### 4.4 Message — `{}` template vs no interpolation

SLF4J: `info("user {} did {}", [name, action])`. Lazy substitution: if the level is
disabled the `format` step is skipped. Familiar and concise.

slog: `Info("user did action", "name", name, "action", action)` — no interpolation.
The handler decides how to render. Output is typically `msg="user did action"
name=alice action=login` for the text handler.

**Ecstasy fit.** Both are easy. SLF4J's `{}` is more compact for human-readable
output; slog's structure-first is easier for machine consumption. The Ecstasy default
arguments and varargs make either ergonomic.

**Formatter boundary.** `lib_logging.MessageFormatter` belongs only to the SLF4J-shaped
side. Go `log/slog` has no equivalent formatter: callers pass a finished message and
separate attrs. Reusing the SLF4J formatter in `lib_slogging` would erase the cleanest
part of slog's design by adding positional arguments next to attrs. If we want a
migration helper later, it should be an adapter outside the core slog API.

### 4.5 Sink/handler shape — minimal vs richer

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
depends on workload. `lib_slogging` now ships
[`BoundHandler`](../../lib_slogging/src/main/x/slogging/BoundHandler.x) as the default
derivation wrapper; a production backend can still override those hooks to cache a
serialized prefix or backend-native context object.

### 4.6 Logger naming — hierarchical vs attribute-based

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

### 4.7 Source location

Both libraries now expose an explicit source-aware call as the lowering target for
future compiler/runtime help: `logging.Logger.logAt(...)` populates
`LogEvent.sourceFile` / `sourceLine`; `slogging.Logger.logAt(...)` populates
`Record.sourceFile` / `sourceLine`.

Automatic call-site capture remains compiler/runtime polish. The library-level
decision is made: source metadata belongs on the immutable event/record, not in a
backend-specific side channel.

### 4.8 Async / batching

Same shape in both: a wrapper handler/sink owns a bounded queue and drains on a
worker fiber. The base libraries now ship these as `AsyncLogSink` and
`AsyncHandler` so slow output can be isolated without waiting for a full
configuration backend.

### 4.9 Familiarity

A subjective dimension that nonetheless matters for adoption. SLF4J is the dominant
JVM logging API; Java/Kotlin engineers reach for it without thinking. slog is the
modern Go default; cloud-native engineers reach for it. Ecstasy targets a developer
audience that will overlap heavily with both.

---

## 5. Ecstasy idiom fit — where the languages bite

### 5.1 `const` vs `service` for the building blocks

In `lib_logging`:

- `BasicLogger` is a `const` (so `@Inject Logger` lives on the caller's fiber, MDC
  works).
- `MDC` is a `const` over `SharedContext<immutable Map>`, copy-on-write.
- Sinks split: stateless ones (`NoopLogSink`, `ConsoleLogSink`) are `const`;
  stateful ones (`MemoryLogSink`, `ListLogSink`) are `service`.

In `lib_slogging`:

- `Logger` is a `const` carrying `Handler handler`. Derivation via `with(...)`
  returns a new `const Logger` with a derived handler — naturally immutable.
- `LoggerContext` is the optional `SharedContext<Logger>` helper for framework/request
  propagation; it is not required for normal logging calls.
- Handlers split the same way: stateless adapters (`TextHandler`, `JSONHandler`,
  `NopHandler`) are `const`; stateful collectors (`MemoryHandler`) are `service`.

The slog model is *substantially* simpler on the const side because the core API does
not snapshot an MDC map on every event. The optional interaction — "I want a logger
that propagates through fibers without threading a parameter" — lives in
`LoggerContext`, not in the hot path.

### 5.2 Fluent builder vs varargs

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

### 5.3 Marker subgraph vs attribute uniformity

`Marker` in SLF4J/`lib_logging` is its own type with `add` (parent/child references)
and `contains` (transitive query). It also has identity (`MarkerFactory.getMarker`
interns). That's a small DAG type.

slog has no marker concept. The same use cases (filter audit-only events) are
handled by `Attr.of("audit", True)` and a handler that filters on that attr.

**Ecstasy fit.** Markers add a separate type; attributes don't. The marker model is
slightly more efficient (one interned reference vs a string equality check), but the
attribute model needs less surface area.

### 5.4 Compiler-side default-name injection

SLF4J expects `LoggerFactory.getLogger(MyClass.class)` to resolve to a class- or
module-named logger. `lib_logging` currently handles the simple interpreter case with
a runtime fallback from the injected field name to the caller namespace. Exact
compiler-synthesized names remain future polish. Without them, `@Inject Logger` gets a
fixed-name root logger and you call `.named("...")` to derive children.

slog has no such machinery — there is one process-wide default Logger and you derive
from it. No naming hierarchy, no compiler change.

**Ecstasy fit.** slog removes a "do we make the compiler smarter?" question
entirely. SLF4J keeps the door open for the nicer ergonomics if the compiler change
lands.

---

## 6. What we want reviewer feedback on

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

8. **Source location.** Both POCs expose explicit `logAt(...)` APIs. Do reviewers want
   compiler/runtime sugar that automatically populates those fields on ordinary
   `logger.info(...)` calls?

9. **Performance baseline.** Neither library has been profiled. If reviewers want
   benchmarks before deciding, name the workload (sustained 100k events/sec to a
   no-op sink? burst with disabled levels? 95th-percentile latency at the call
   site?) and we'll add it to the comparison.

10. **Configuration home.** Both libraries now have async wrappers. Should the XDK
    also ship a standard JSON config loader/reload service, or leave config-file
    loading to applications/backends?

11. **Should Ecstasy ship two logging libraries permanently?** The Java ecosystem has
    two large facades (SLF4J, Log4j 2 API) and is by no means ergonomic for it.
    Carrying both `lib_logging` and `lib_slogging` long-term has the same risk. The
    intent of this experiment is to *pick one* and move on. We want reviewer input
    on which.

---

## 7. Recommendation

Choose **`lib_logging` as the canonical Ecstasy logging library**.

The decisive reason is not that the SLF4J shape is prettier. It is that the XDK
needs one logging facade that looks industrially boring to the largest likely
audience while still leaving room for structured output and backend innovation.
`lib_logging` now does that:

- JVM users recognize the call surface immediately: `Logger`, named loggers, `{}` message
  formatting, `Exception` cause handling, markers, MDC, and SLF4J 2.x fluent builders.
- Kotlin/SLF4J 2.x users get lazy message/value construction for hot paths:
  suppliers are invoked only after the level/marker check accepts the event.
- Backend authors get one narrow extension point, `LogSink`, with working examples for
  console, memory capture, JSON-Lines, async forwarding, multi-destination fanout, and
  hierarchical per-logger level routing.
- Cloud and JSON users are not forced to parse message text. Structured key/value pairs
  travel on `LogEvent.keyValues`; `JsonLogSink` renders them directly and applies
  redaction policy before output.
- Runtime injection already works for the interpreter, including a default-name fallback
  for canonical `logging.Logger` injections.

`lib_slogging` remains valuable. It is smaller, cleaner, and closer to modern
attribute-first logging. It also documents a real alternative for teams that prefer
Go's `slog` mental model. The right long-term shape, however, is not to ship two
competing XDK facades. Keep `lib_slogging` as review material and possible adapter
surface; make `lib_logging` the default API users learn first.

A hybrid SLF4J-facade with slog-shaped event internals is worth revisiting after v0,
but it should not be the starting point. Mixing `Marker`, `MDC`, `Object[] arguments`,
and `Attr[]` in one public contract would make the facade harder to teach than either
pure design.

The remaining compiler/runtime work is polish around the chosen facade, not a reason
to reopen the API choice: automatic source-location capture, better generated logger
names, and optional backend configuration loaders can all target the existing
`Logger` -> `LogSink` boundary.

---

## 8. What lives in this PR

This branch (`lagergren/logging`) contains two comparable implementations and the
review material needed to choose between them.

| Area | Contents |
|---|---|
| `lib_logging/` | Recommended canonical SLF4J-shaped library with 70 focused XTC test methods, runtime injection, lazy suppliers, source metadata API, JSON/redaction sink, async wrapper, composite fanout, and hierarchical per-logger thresholds. |
| `lib_slogging/` | slog-shaped sibling library with 41 focused XTC test methods, runtime injection, lazy message/attr suppliers, `lib_json` JSON rendering, handler options/redaction, async wrapper, explicit source metadata, `LoggerContext`, handler derivation, and handler contract tests. |
| `doc/logging/` | Design comparison, API cross-reference, language/runtime questions, usage guides, and backend follow-up sketches. |

The branch is now opinionated: Q-D6 is answered as `lib_logging`. Review can still
challenge that decision, but the implementation and docs no longer leave the reader
with two equally recommended facades.

---

Previous: [`usage/custom-handlers.md`](usage/custom-handlers.md) | Next: [`concepts/markers.md`](concepts/markers.md) → | Up: [`README.md`](README.md)
