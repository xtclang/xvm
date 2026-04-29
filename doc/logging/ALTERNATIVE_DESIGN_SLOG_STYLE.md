# Alternative design — what `lib_logging` would look like as an `slog`-style library

This is an **exploratory** document. It exists so we have a clear-eyed picture of what
we'd be giving up (and what we'd be gaining) if we modeled the Ecstasy logging library
on Go's `log/slog` instead of SLF4J. No skeleton code is being added to the repo for
this design; everything below is illustrative.

The file is organised symmetrically with the SLF4J design so you can A/B them.

## What `slog` is

`slog` is the structured-logging package added to Go's standard library in Go 1.21. Its
shape diverges from SLF4J in three significant ways:

1. **Structured first, free-form text second.** `slog` calls take a message followed by
   alternating key/value pairs as the *primary* mechanism, not as an addendum:
   `slog.Info("processed", "count", 42, "duration", elapsed)`.
2. **`Handler` instead of `Appender`/`Encoder`.** A handler is a single combined
   "filter + render + ship" object. The standard library ships `TextHandler` and
   `JSONHandler`. Custom handlers wrap or replace them.
3. **`Logger` is a value type, not a service.** Loggers are cheap to derive: `child :=
   logger.With("requestId", id)` returns a new logger that prepends those attributes to
   every event. There is no `MDC`-style ambient context — context is lexical.

These three together produce a quite different developer experience.

## What `lib_logging` would look like as an slog-style library

### Core types

The user-facing facade collapses from "Logger plus Marker plus MDC plus Builder" down to
"Logger plus Attr". A sketch:

```ecstasy
/**
 * The slog-style logger. Holds an immutable list of pre-bound attributes plus a handler.
 */
const Logger(Attr[] attrs, Handler handler) {

    @RO Boolean traceEnabled.get() = handler.enabled(Trace, attrs);
    @RO Boolean debugEnabled.get() = handler.enabled(Debug, attrs);
    // ...

    void info (String message, Attr[] extra = []) = log(Info,  message, extra);
    void debug(String message, Attr[] extra = []) = log(Debug, message, extra);
    void warn (String message, Attr[] extra = []) = log(Warn,  message, extra);
    void error(String message, Attr[] extra = []) = log(Error, message, extra);
    void trace(String message, Attr[] extra = []) = log(Trace, message, extra);

    /**
     * Derive a new logger with extra attributes pre-bound.
     */
    Logger with(Attr... extra) {
        return new Logger(attrs + extra, handler);
    }

    /**
     * Derive a new logger inside a *group*; child attributes are nested under `name`
     * in structured output.
     */
    Logger group(String name) {
        return new Logger(attrs.add(GroupOpen(name)), handler);
    }

    void log(Level level, String message, Attr[] extra) {
        if (!handler.enabled(level, attrs)) {
            return;
        }
        handler.handle(new LogRecord(timestamp=clock.now,
                                     level=level,
                                     message=message,
                                     attrs=attrs + extra));
    }
}
```

### The `Attr` type

This is the unit of structured information. Always typed key/value, never free-form:

```ecstasy
const Attr(String key, AttrValue value);

const AttrValue
        | StringValue(String value)
        | IntValue(Int value)
        | FloatValue(Float value)
        | BoolValue(Boolean value)
        | TimeValue(Time value)
        | DurationValue(Duration value)
        | ErrorValue(Exception value)
        | GroupValue(Attr[] members)        // nested object
        | LazyValue(function AttrValue ()); // resolved at handler time
```

`LazyValue` is the slog `LogValuer` mechanism. The handler calls the function only when
the event is actually being emitted, so disabled levels never resolve the value.

### The `Handler` interface

Replaces `LogSink`. Takes a richer record, returns nothing:

```ecstasy
interface Handler {
    Boolean enabled(Level level, Attr[] context);
    void    handle(LogRecord record);

    /**
     * Return a new handler that pre-binds these attributes; child loggers' `.with(...)`
     * derivation goes through this so the handler can pre-render context once.
     */
    Handler withAttrs(Attr[] extra);

    /**
     * Open a structured group — output between matching `WithGroup` boundaries gets
     * nested under `name` in the rendered output.
     */
    Handler withGroup(String name);
}
```

### Acquisition

`@Inject Logger logger` still works — the binding is the platform-controlled handler.
Derived loggers are explicit:

```ecstasy
@Inject Logger logger;

void handleRequest(Request req) {
    Logger reqLog = logger.with(
        new Attr("requestId", new StringValue(req.id)),
        new Attr("user",      new StringValue(req.user))
    );

    reqLog.info("incoming");
    process(req, reqLog);
}

void process(Request req, Logger log) {
    log.info("validating", [new Attr("size", new IntValue(req.size))]);
    // ...
}
```

There is no `MDC.put`. Context lives on a logger value that gets passed down explicitly.

### Default handlers

```ecstasy
service TextHandler implements Handler { /* writes "key=value key=value" lines */ }
service JsonHandler implements Handler { /* one JSON object per line */ }
service TeeHandler  implements Handler { /* fans out to many sub-handlers */ }
```

`TextHandler` is the analogue of slog's `TextHandler`; `JsonHandler` is `slog.JSONHandler`.

### Example call sites

**Free-form log line, no structured fields:**

```ecstasy
logger.info("startup complete");
```

**Structured event:**

```ecstasy
logger.info("payment processed", [
    new Attr("paymentId", new StringValue(payment.id)),
    new Attr("amount",    new IntValue(payment.amount)),
    new Attr("currency",  new StringValue(payment.currency))
]);
```

**Nested group:**

```ecstasy
logger.group("network").info("connected", [
    new Attr("host", new StringValue(addr.host)),
    new Attr("port", new IntValue(addr.port))
]);
```

Renders as JSON:

```json
{"level":"INFO","msg":"connected","network":{"host":"db.internal","port":5432}}
```

**Lazy attribute:**

```ecstasy
logger.debug("snapshot", [
    new Attr("dump", new LazyValue(() -> new StringValue(serializer.dump(thing))))
]);
```

`serializer.dump` runs only if `Debug` is enabled.

**Per-request derived logger (replaces MDC):**

```ecstasy
@Inject Logger logger;
Logger reqLog = logger
    .with(new Attr("requestId", new StringValue(req.id)))
    .with(new Attr("tenant",    new StringValue(req.tenant)));

reqLog.info("dispatch");
process(req, reqLog);
```

## What this design wins

- **Structured logging is the primary path.** No KV pairs glued onto the side of a
  free-form message; the message is one of many fields. JSON output is the natural
  default. This is what modern observability stacks (Datadog, Loki, Grafana, ELK) want
  anyway.
- **No ambient state.** No MDC `put`/`remove` ceremony. No surprise context bleed
  between requests because someone forgot a `finally` block. Loggers are values.
- **`with` and `group` are honest about what they do.** A derived logger is a new value;
  deriving is cheap (`O(1)` allocation) and obviously local.
- **Lazy values are first-class.** `LazyValue` slots into the same `AttrValue` union as
  every other type; no separate Supplier-overload story.
- **Handler-side structure, not encoder-side.** A handler that wants pretty-print JSON
  reads the structured `Attr[]` directly. There is no message-text-first / structure-
  glued-on bifurcation.

## What this design loses

- **No instant familiarity for the SLF4J majority.** The dominant working population of
  backend engineers in the world today is "people who write `log.info("...", arg)` in
  Java." They will look at this and have to learn a new model. That cost is real.
- **No first-class `Marker`.** slog has no markers. Routing-by-tag is done either via
  attributes (`new Attr("category", ...)`) plus a handler that filters on them, or via
  multiple handlers. This is fine in principle but requires user code to learn the new
  pattern.
- **No `MDC`.** Context propagation is *explicit*: derive a logger and pass it down. In
  Ecstasy fibers this is sometimes great (no leakage) and sometimes painful (every
  function in the chain needs a `Logger` parameter). Java/SLF4J users hit this and
  immediately ask "where's MDC?" because that's the muscle memory.
- **More allocation per call site, on the surface.** Every structured event constructs
  an `Attr[]` array literal. The compiler can almost certainly elide most of this, but
  it's a different shape than the SLF4J `(message, args, ...)` flat call.
- **Verbosity for simple unstructured events.** `slog.Info("starting up")` is fine but
  the moment you want one piece of context you're typing
  `[new Attr("port", new IntValue(8080))]`. SLF4J's `info("starting up on {}", port)` is
  shorter for the human reader, even if it's worse for machines.
- **Configuration story is less standardized.** SLF4J inherits Logback's
  configuration-file ecosystem (XML, programmatic, autoscan, reload). slog has handlers,
  and that's it. Anything fancier you build yourself.

## Two-axis comparison

|                            | SLF4J-shape (proposed) | slog-shape (this doc) |
|---|---|---|
| **Familiarity to Java engineers** | Instant | Requires learning a new model |
| **Familiarity to Go engineers** | Slight pivot | Native |
| **Structured-first vs message-first** | Message-first, KV pairs additive | Structured-first |
| **Context propagation** | MDC (ambient) + `Logger.with` (explicit, future) | `Logger.with` only (explicit) |
| **Markers** | First-class | Replaced by attribute-based filtering |
| **Lazy values** | Lambda overloads (Option 1 from `LAZY_LOGGING.md`) | `LazyValue` in the `AttrValue` union |
| **JSON output** | Sink-side encoder (`JsonLineLogSink`) | Native (`JsonHandler`) |
| **API↔impl boundary** | `LogSink` interface | `Handler` interface |
| **Configuration model** | Inherits Logback ecosystem (future) | DIY per-handler |
| **Allocation per disabled call** | Zero (level check, no formatting, no MDC snapshot) | Zero (level check first) |
| **Allocation per enabled simple call** | One `LogEvent` const | One `LogRecord` const + one `Attr[]` |
| **Default sink/handler shipped with v0** | `ConsoleLogSink` (text) | `TextHandler`, `JsonHandler` |

Both designs land at the same place on performance once the level check short-circuits
disabled events. They diverge sharply on user-facing ergonomics.

## What we'd be saying yes to, what we'd be saying no to

**Yes:**

- A clean slate that maps onto modern observability tools.
- One less concept (no MDC).
- Structured logging without the "but the encoder has to be configured" footnote.

**No:**

- Inheriting SLF4J's audience for free.
- Inheriting Logback's configuration ecosystem for free (later).
- Inheriting markers.

## Why we're not picking this

The recommendation in `WHY_SLF4J_AND_INJECTION.md` stands. The argument compresses to:
"the audience that matters most is SLF4J users, and SLF4J's design choices are battle-
tested wins, not legacy baggage." But the slog-shape is a coherent, internally
consistent alternative — it is not a strawman. If at some future point the centre of
gravity in backend engineering shifts decisively toward Go-shaped APIs, this document is
the starting point for revisiting the choice. We can also implement an `slog`-style
*adapter* over the SLF4J-shaped core later, exactly the way `slf4j-jul-impl` adapts JUL
to SLF4J — which is a reminder that the choice is recoverable in either direction.

## Hybrid path, if we ever want it

The interesting hybrid is **SLF4J-shaped facade, slog-shaped event model.** Concretely:

- Keep `Logger`, `Marker`, `MDC`, the fluent builder.
- Replace the free-form `arguments: Object[]` on `LogEvent` with a typed
  `attrs: Attr[]`.
- Have `MessageFormatter` resolve `{}` placeholders against `attrs` by index, but also
  have a `JsonLineLogSink` render `attrs` directly as a JSON object.

That gives SLF4J users their shape and slog users their wire format. It's also
strictly more complex than either pure design. We do not recommend it for v0; the right
moment to consider it is after the basic SLF4J-shaped library is shipping and we know
which axis we wish we had more of.
