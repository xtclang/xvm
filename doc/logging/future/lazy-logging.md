# Lazy logging exploration

This document explores how to give `lib_logging` users the same ergonomic property the
Kotlin `kotlin-logging` community loves:

```kotlin
log.info { "expensive: ${serializer.dump(thing)}" }
```

The lambda body is evaluated *only* if the level is enabled. Caller code is one line and
zero-cost when the level is off.

## Why this matters

Three things together motivate caring about lazy logging:

1. **Argument construction is the dominant cost of a disabled log call.** SLF4J's `{}`
   substitution defers the *formatting* but not the *argument materialization* — if you
   write `logger.debug("size: {}", expensiveSerialize(thing))`, the
   `expensiveSerialize(thing)` call still runs even when DEBUG is off. The level check
   only protects the `MessageFormatter` work, not the caller's expression evaluation.
2. **The traditional escape hatch is verbose.** `if (logger.debugEnabled) { logger.debug(...) }`
   works, but doubles the line count of every expensive log site, and people skip the
   guard until a profiler tells them to add it back.
3. **Modern logging libraries have figured this out.** Kotlin, JUL, SLF4J 2.x, Scala,
   Rust, C++ spdlog — they all offer some lazy-by-default emission. We should too.

## What we already have for free

`lib_logging` already eliminates two of the three sources of disabled-call overhead:

- **Deferred formatting.** `MessageFormatter` only runs after the `sink.isEnabled` check.
  When the level is off, no string is built.
- **Builder short-circuits.** `logger.atDebug()` will, when fully implemented, return a
  no-op builder when DEBUG is disabled, so the chain
  `logger.atDebug().addArgument(x).addKeyValue("k", v).log("...")` does no work.

The remaining gap is **caller-side expression evaluation** of `arguments`/values. That's
exactly the gap the Kotlin lambda form closes.

## Industry survey — how other languages address this

| Ecosystem | Mechanism | Notes |
|---|---|---|
| **Java SLF4J 2.x** | `LoggingEventBuilder.log(Supplier<String>)` and `addArgument(Supplier<?>)` | The builder evaluates suppliers lazily. Older `Logger` overloads do not. |
| **Java JUL** | `Logger.log(Level, Supplier<String>)` since Java 8 | Lazy variant added late but in stdlib. |
| **kotlin-logging** | Inline functions taking `() -> Any?` lambda, e.g. `logger.info { msg }` | The de-facto Kotlin idiom. Inlined at the call site so no allocation. |
| **Scala scala-logging** | Compile-time macros — the call expands at compile time to wrap the message in a `if (logger.isDebugEnabled)` guard | Zero overhead when disabled, no runtime check beyond the level read. |
| **Rust `log`** | Macros (`debug!`, `info!`) | Same idea: macro expands to the if-enabled guard around format args. Compile-time, not runtime. |
| **Go `log/slog`** | `slog.LogValuer` interface lets the value type defer its own resolution | Plus handler-side `Enabled(level)` check before any attribute is materialized. |
| **C++ spdlog / glog** | Macros (`SPDLOG_DEBUG`, `LOG(INFO)`) — expand to a guarded statement | Pre-processor lazy. |
| **Python stdlib `logging`** | `%s` placeholder + `*args` gets deferred formatting; for full arg laziness people guard on `isEnabledFor(...)` | No first-class lambda support; the convention is good enough for most cases. |
| **Erlang/Elixir Logger** | Reports as a *function* `Logger.debug(fn -> "..." end)` | Lambda-based laziness baked into the API. |

The pattern is universal: every modern logging library has a way to defer message
construction. Some use compile-time macros, some use runtime closures, some use type-
based deferral. **The user expectation is that this works.** Anyone porting a Kotlin or
SLF4J 2.x service over will look for it first thing.

## Three options for `lib_logging`

We have three plausible paths. They are not mutually exclusive — we can ship one now and
add others later. Listed in order of ease.

### Option 1 — `Supplier<String>` overloads on `Logger`

Add lambda-taking variants of every level method. The signature uses Ecstasy's
function-typed parameters:

```ecstasy
interface Logger {
    // Existing:
    void info(String message,
              Object[]   arguments = [],
              Exception? cause     = Null,
              Marker?    marker    = Null);

    // Proposed: lambda variant.
    void info(function String () messageFn,
              Exception? cause  = Null,
              Marker?    marker = Null);

    // Same for trace/debug/warn/error and the polymorphic `log`.
}
```

Caller side:

```ecstasy
logger.info(() -> $"expensive: {serializer.dump(thing)}");
```

Implementation in `BasicLogger`:

```ecstasy
void info(function String () messageFn,
          Exception? cause  = Null,
          Marker?    marker = Null) {
    if (!sink.isEnabled(name, Info, marker)) {
        return;
    }
    String msg = messageFn();
    sink.log(new LogEvent(name, Info, msg, ...));
}
```

**Pros**

- Zero language work — implementable today against the existing `lib_logging` POC.
- One-line change at every call site that wants laziness.
- Plays nicely with markers, MDC, and the fluent builder.
- Works the same way `slf4j-api`'s newer fluent `log(Supplier<String>)` works, so the
  mental model carries over.

**Cons**

- The closure allocates (one captured-frame object per call site, even when the level is
  enabled). For hot paths in a long-running service this is non-trivial. Mitigation:
  Ecstasy's compiler can in theory inline a closure that's used exactly once and
  doesn't escape; whether it does today is a separable optimisation question.
- Slightly less ergonomic than the Kotlin lambda form, which uses `{ ... }` braces with
  no arrow. We can revisit syntax if Ecstasy gains a Kotlin-style trailing-block sugar.

### Option 2 — Build on top of issue #437 scope functions

Issue [xtclang/xvm#437](https://github.com/xtclang/xvm/issues/437) proposes adding
Kotlin-style scope functions to Ecstasy. The proposed shape is a postfix `.{ ... }`
block where `this` is the receiver:

```ecstasy
val adam = new Person("Adam").{
    age = 32;
    city = "London";
};
```

This wouldn't directly give us the Kotlin `logger.info { msg }` form, but it would make
the level-guarded form *short enough to use everywhere*:

```ecstasy
// Without #437:
if (logger.infoEnabled) {
    logger.info($"expensive: {serializer.dump(thing)}");
}

// With #437 (logger.{ ... } opens a scope where `this` is `logger`):
logger.{
    if (infoEnabled) {
        info($"expensive: {serializer.dump(thing)}");
    }
};
```

That's still not great. Where #437 *would* shine is if it's combined with a small
extension method on `Logger`:

```ecstasy
class Logger {
    // pseudo-extension; the actual mechanism depends on Ecstasy's extension story
    void infoIfEnabled(function String () messageFn) {
        if (infoEnabled) {
            info(messageFn());
        }
    }
}

// usage:
logger.infoIfEnabled(() -> $"expensive: {serializer.dump(thing)}");
```

…which is just Option 1 with a different name. So #437 isn't a substitute for Option 1;
it's a useful complement when the caller wants to do _multiple_ things with the logger
in a level-guarded scope.

### Option 3 — Compile-time elision via macros / annotation processor

The Scala / Rust / C++ approach. The compiler sees `logger.debug("...", $arg)` and
expands it to a guarded form. In Ecstasy this would mean an XTC compiler enhancement
that recognises `Logger` calls and rewrites them.

**Pros**

- Zero overhead when disabled, even for the closure allocation.
- Caller code looks identical to the eager form.

**Cons**

- Compiler-side work; out of scope for the `lib_logging` v0.
- Couples the compiler to a specific library's identity, which is a smell.
- Mostly redundant with Option 1 once Ecstasy's optimizer handles single-use closures.

We do not recommend this path.

## Recommendation

**Ship Option 1 in v0.1.** It's small, it's idiomatic, it lands today, and it makes the
library usable for hot-path code without ceremony. The signature lives in `Logger.x`
and the implementation in `BasicLogger.x`; both already have the surrounding shape.

**Track #437 as it lands.** When scope functions arrive, document the `logger.{ ... }`
idiom for the cases where multiple log calls share a level guard. No code change needed
in `lib_logging`.

**Keep Option 3 in mind only if profiling later shows closure allocation is a real
problem.** The right time to invest in compiler-level elision is after we have data
saying it matters.

## Concrete next step

Add the lambda overloads to `Logger.x` and `BasicLogger.x`. Update `../usage/ecstasy-vs-java-examples.md`
with a side-by-side showing:

```kotlin
// Kotlin
logger.info { "size: ${expensiveSerialize(thing)}" }
```

```ecstasy
// Ecstasy
logger.info(() -> $"size: {expensiveSerialize(thing)}");
```

That's the pull-quote that closes the gap for an SLF4J/Kotlin user evaluating the
library.

## Wider survey takeaways

A few patterns recur across the industry survey worth noting because they shape future
decisions:

1. **The level check is *always* the first thing the implementation does.** Whether the
   API is lambda-based, macro-based, or builder-based, every modern library short-
   circuits at the level check before any other work. `lib_logging` already does this.
2. **The structured-data story (KV pairs) and the laziness story converge.** SLF4J 2.x's
   `addArgument(Supplier<?>)` and slog's `LogValuer` are both "evaluate the value lazily
   per attribute". `lib_logging` could follow with `addArgument(function Object ())` on
   the builder. Worth doing in the same v0.1 cut as the per-method lambda overloads.
3. **Macros are losing.** Of the languages that started with macro-based logging (C++,
   Rust), the Rust crate has been creeping toward closure-based extensibility for years.
   Macro-based laziness is a 1990s/early-2000s solution. Closure-based is what's left
   standing in the languages that have first-class lambdas.

This is consistent with picking Option 1 and only revisiting if data demands it.


---

_See also [../README.md](../README.md) for the full doc index and reading paths._
