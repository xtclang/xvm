# Writing a custom `Handler`

`Handler` is the backend extension point for `lib_slogging`, the same way `LogSink`
is the backend extension point for `lib_logging`.

Official Go reference: [`slog.Handler`](https://pkg.go.dev/log/slog#Handler).

## The contract

```ecstasy
interface Handler {
    Boolean enabled(Level level);
    void    handle(Record record);
    Handler withAttrs(Attr[] attrs);
    Handler withGroup(String name);
}
```

The first two methods are the same basic shape as `LogSink`: cheap enabled check,
then emit the record. The second two methods are the slog-specific part. They are
called when user code derives a logger:

```ecstasy
Logger requestLog = logger.with([Attr.of("requestId", req.id)]);
Logger grouped    = requestLog.withGroup("payments");
```

A handler that only counts/drops records may return `this` from both derivation
methods because attrs do not affect its behavior. A normal emitting handler should
either return `new BoundHandler(this, attrs/name)` for correct default semantics or
pre-render attrs/group prefixes into a handler-specific derived instance so each later
`handle(record)` does less work.

## `const` or `service`?

Same rule as `LogSink`:

| Shape | Use it for | Examples |
|---|---|---|
| `const` | Stateless forwarders and fixed-configuration renderers. | `TextHandler`, `JSONHandler`, `NopHandler`. |
| `service` | Shared mutable state or external resources. | `MemoryHandler`, file writers, network writers, async queues. |

## Counting records by level

```ecstasy
service CountingHandler
        implements Handler {

    public/private Map<Level, Int> counts = new HashMap();

    @Override
    Boolean enabled(Level level) {
        return True;
    }

    @Override
    void handle(Record record) {
        counts.process(record.level, e -> {
            e.value = e.exists ? e.value + 1 : 1;
            return Null;
        });
    }

    @Override
    Handler withAttrs(Attr[] attrs) = this;

    @Override
    Handler withGroup(String name) = this;
}
```

Used like:

```ecstasy
CountingHandler handler = new CountingHandler();
Logger          logger  = new Logger(handler);

logger.info("a");
logger.warn("b");

assert handler.counts[Level.Info] == 1;
assert handler.counts[Level.Warn] == 1;
```

The same code exists as a unit-test helper in
[`lib_slogging/src/test/x/SLoggingTest/CountingHandler.x`](../../../lib_slogging/src/test/x/SLoggingTest/CountingHandler.x).

## Reusing the handler contract checks

The test suite includes a small `HandlerContract`, modelled on Go's
[`testing/slogtest`](https://pkg.go.dev/testing/slogtest), that third-party handlers
can reuse:

```ecstasy
MemoryHandler handler = new MemoryHandler();

HandlerContract.assertWithAttrsPrepend(handler, () -> handler.records);
handler.reset();
HandlerContract.assertWithGroupNests(handler, () -> handler.records);
```

Your own handler test can pass the handler instance plus a snapshot function that
returns the records it emitted. The contract checks that derived attrs are prepended
and groups are nested before the final handler observes the record.

## Filtering audit events

In the slog model, categories are attrs, not markers:

```ecstasy
service AuditFilteringHandler(Handler delegate)
        implements Handler {

    @Override
    Boolean enabled(Level level) {
        return delegate.enabled(level);
    }

    @Override
    void handle(Record record) {
        for (Attr attr : record.attrs) {
            if (attr.key == "audit" && attr.value == True) {
                delegate.handle(record);
                return;
            }
        }
    }

    @Override
    Handler withAttrs(Attr[] attrs) {
        return new AuditFilteringHandler(delegate.withAttrs(attrs));
    }

    @Override
    Handler withGroup(String name) {
        return new AuditFilteringHandler(delegate.withGroup(name));
    }
}
```

Call site:

```ecstasy
logger.info("user signed in", [
        Attr.of("audit", True),
        Attr.of("user",  userId),
]);
```

The SLF4J-shaped equivalent would use a marker. The slog-shaped equivalent keeps the
category in the same `Attr` channel as every other structured field.

## Operational rules

| Rule | Reason |
|---|---|
| Keep `enabled` cheap. | It runs before record allocation on every logging call. |
| Keep normal backend failures inside the handler. | A failed remote endpoint should lead to drop, buffer, or fallback behavior, not an application failure. |
| Do not parse `record.message` to recover fields. | Structured data belongs in `Attr` values. |
| Do not add SLF4J-style placeholder formatting to the core handler path. | Migration sugar can live in an adapter; the slog core stays message-plus-attrs. |

## What a production JSON handler still needs

The shipped `JSONHandler` renders through `lib_json`, preserves nested groups, and
represents exceptions structurally. A production cloud sink built on the same handler
contract would add destination configuration (`Console`, file, stream, socket, HTTP
exporter), attr replacement/redaction, timestamp and level formatting options, and an
async wrapper for network or disk output.


---

_See also [../README.md](../README.md) for the full doc index and reading paths._
