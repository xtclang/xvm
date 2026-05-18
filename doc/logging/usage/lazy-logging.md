# Lazy logging

> **Audience:** anyone whose log calls have non-trivial argument cost. Mandatory reading if you use `debug` heavily.

Lazy logging is a core requirement for the XDK logging API, not a future nice-to-have.
Disabled log calls must not build expensive messages, serialize payloads, or compute
structured values just to be dropped by the level check.

## The requirement

The accepted logging API **MUST** support one-line lazy logging:

- message construction runs only after the backend accepts the level;
- structured value construction runs only after the backend accepts the level;
- callers can still use explicit `if (logger.debugEnabled)` guards for multi-statement work;
- the lazy call shape is familiar to Java, Kotlin, Go, and Scala users.

## Why placeholder formatting is not enough

SLF4J `{}` formatting defers string substitution, but it does not defer argument
evaluation:

```java
log.debug("payload {}", expensiveSerialize(payload));
```

`expensiveSerialize(payload)` runs before Java calls `debug(...)`, even when DEBUG is
off. The same is true in Ecstasy for eager arguments:

```ecstasy
logger.debug("payload {}", [expensiveSerialize(payload)]);
```

The `MessageFormatter` work is skipped for disabled events, but the caller expression
already ran. Lazy logging closes that gap.

## Industry shapes

| Ecosystem | Lazy mechanism | Design lesson for Ecstasy |
|---|---|---|
| Kotlin logging | `logger.debug { "payload ${expensive()}" }`; inline blocks avoid allocation in Kotlin/JVM. | The call site should stay one line. |
| Java SLF4J 2.x | `log.atDebug().addArgument(() -> expensive()).log("payload {}")` and `log(() -> "...")` on the builder. | Suppliers belong behind the enabled check. |
| Java JUL | `logger.log(Level.FINE, () -> expensiveMessage())`. | Supplier messages are already a standard Java pattern. |
| Go `log/slog` | `Handler.Enabled` runs before record construction; expensive values can implement `slog.LogValuer`. | Attribute-first APIs need lazy values, not message formatting. |
| Scala / Rust / C++ | Macros expand logging calls into guarded code. | Compiler elision is powerful but should not be required for v0. |

## `lib_logging`: SLF4J/Kotlin-style suppliers

Use a lazy message supplier when the whole message is expensive:

```kotlin
// Kotlin
logger.debug { "payload ${serializer.dump(payload)}" }
```

```java
// Java JUL / supplier-style APIs
logger.log(Level.FINE, () -> "payload " + serializer.dump(payload));
```

```ecstasy
// Ecstasy lib_logging
logger.debug(() -> $"payload {serializer.dump(payload)}");
```

Use the fluent builder when the message template is cheap but one argument or structured
field is expensive:

```java
// Java SLF4J 2.x
log.atDebug()
   .addArgument(() -> serializer.dump(payload))
   .addKeyValue("requestId", requestId)
   .log("payload {}");
```

```ecstasy
// Ecstasy lib_logging
logger.atDebug()
      .addLazyArgument(() -> serializer.dump(payload))
      .addKeyValue("requestId", requestId)
      .log("payload {}");
```

For expensive structured fields:

```ecstasy
logger.atDebug()
      .addLazyKeyValue("payload", () -> serializer.dump(payload))
      .log("debug payload attached");
```

### How it is implemented

`BasicLogger` checks `LogSink.isEnabled(name, level, marker)` before invoking a
`MessageSupplier`. `BasicEventBuilder` freezes markers first so service sinks can see the
primary marker during enablement, then checks the sink. Only if enabled does it invoke
lazy message, argument, or key/value suppliers, run `MessageFormatter`, snapshot MDC, and
allocate `LogEvent`.

Ecstasy cannot overload `addArgument(Object)` with `addArgument(function Object())`
because functions are also objects. The POC therefore uses explicit method names:
`addLazyArgument` and `addLazyKeyValue`. That is slightly less Java-like, but it is clear
at the call site and avoids ambiguous overload rules.

## `lib_slogging`: slog-style lazy attrs

Do not use `MessageFormatter` for slogging. The slog model has a complete message plus
structured attrs, so the lazy equivalent is a message supplier and lazy attribute values:

```go
// Go slog-style idea
logger.Debug("payload", "json", expensiveValueThatMayResolveLazily)
```

```ecstasy
// Ecstasy lib_slogging
logger.debug("payload", [
        Attr.lazy("json", () -> serializer.dump(payload)),
]);
```

Lazy message construction is also available:

```ecstasy
logger.debug(() -> $"payload {serializer.dump(payload)}", [
        Attr.of("requestId", requestId),
]);
```

`Attr.lazy` is the POC's compact equivalent of Go slog's `LogValuer`: a value wrapper
that resolves only for enabled records. `Logger.emit` performs `Handler.enabled(level)`
first, then resolves lazy attrs, constructs the `Record`, and calls `Handler.handle`.
`BoundHandler` also resolves lazy attrs attached by `logger.with(...)` when an enabled
record is handled.

One Ecstasy-specific rule matters: `Attr` is a `const`, so the lazy supplier must capture
only passable state. Capturing a service reference or immutable value is fine. Capturing
and mutating an ordinary local variable is not, because the supplier carrier is frozen.
For mutable request state, resolve to a stable value before `Attr.lazy`, capture a service,
or use the direct lazy message overload when the deferred computation does not need to
be stored inside an `Attr`.

## When to still use explicit guards

Lazy suppliers are for one-line expensive message/value construction. Use explicit level
guards when the logging side work is multi-step or has side effects:

```ecstasy
if (logger.debugEnabled) {
    DebugSnapshot snapshot = diagnostics.snapshot(order);
    logger.debug("order diagnostics {}", [snapshot.summary]);
    metrics.debugSnapshotCount++;
}
```

The same rule applies to slogging:

```ecstasy
if (logger.debugEnabled) {
    PayloadDump dump = diagnostics.dump(payload);
    logger.debug("payload dump", [Attr.of("dump", dump.text)]);
}
```

## Compiler optimization path

The current POC uses runtime function values. That is enough for API semantics and is
the same conceptual shape as Java suppliers. Later compiler work can optimize single-use
non-escaping closures, or add Kotlin-style trailing-block syntax if Ecstasy wants that
surface. The API does not depend on that work.

---

Previous: [`structured-logging.md`](structured-logging.md) | Next: [`configuration.md`](configuration.md) → | Up: [`../README.md`](../README.md)
