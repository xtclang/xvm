# SLF4J 2.x → lib_logging mapping

This document inventories every SLF4J 2.x type and major method that user code is likely
to touch, and shows the equivalent in `lib_logging`. The intent is that an SLF4J user can
scan this once and know everything they need.

## Acquiring a logger

| SLF4J 2.x (Java) | lib_logging (Ecstasy) |
|---|---|
| `LoggerFactory.getLogger(MyClass.class)` | `@Inject Logger logger;` then `Logger LOG = logger.named(MyClass.path);` |
| `LoggerFactory.getLogger("com.example.foo")` | `logger.named("com.example.foo")` *(or `LoggerFactory.getLogger("com.example.foo")` when the non-injection factory is wired to a default sink)* |
| (no equivalent) | `@Inject Logger logger;` *(injects a single root logger)* |

`@Inject("com.example.foo") Logger logger;` was considered and rejected — see
`../future/runtime-implementation-plan.md` Stage 1.4 for why. SLF4J doesn't have a
parameterized injection annotation either; `LoggerFactory.getLogger(MyClass.class)`
is its idiom and `Logger.named(String)` is the direct Ecstasy equivalent.

`Logger.named(String)` takes the full logger name. It does not append a suffix to the
current logger name; write `logger.named("billing.Charger")`, not
`logger.named("billing").named("Charger")`, unless the second call also supplies the
full name.

## `Logger` core methods

SLF4J's `Logger` defines five families (one per level) each with ~10 overloads. We collapse
each family to one canonical signature using Ecstasy default arguments, plus the fluent
builder for unusual shapes.

| SLF4J 2.x (Java)                                            | lib_logging (Ecstasy)                                |
|---|---|
| `void info(String msg)`                                     | `info(message)`                                       |
| `void info(String format, Object arg)`                      | `info(message, [arg])`                                |
| `void info(String format, Object arg1, Object arg2)`        | `info(message, [arg1, arg2])`                         |
| `void info(String format, Object... args)`                  | `info(message, args)`                                 |
| `void info(String msg, Throwable t)`                        | `info(message, cause=t)`                              |
| `void info(Marker marker, String msg)`                      | `info(message, marker=m)`                             |
| `void info(Marker marker, String format, Object... args)`   | `info(message, args, marker=m)`                       |
| `void info(Marker marker, String msg, Throwable t)`         | `info(message, cause=t, marker=m)`                    |
| `boolean isInfoEnabled()`                                   | `infoEnabled` (property)                              |
| `boolean isInfoEnabled(Marker marker)`                      | `isEnabled(Info, marker=m)`                           |
| `LoggingEventBuilder atInfo()`                              | `atInfo()`                                            |
| `LoggingEventBuilder atLevel(Level level)`                  | `atLevel(level)`                                      |
| `String getName()`                                          | `name` (property)                                     |

Same applies to `trace`, `debug`, `warn`, `error`.

## `Level` enum

| SLF4J `org.slf4j.event.Level` | lib_logging `Level` |
|---|---|
| `TRACE` | `Trace` |
| `DEBUG` | `Debug` |
| `INFO`  | `Info`  |
| `WARN`  | `Warn`  |
| `ERROR` | `Error` |
| (n/a — sink-side only) | `Off` |

## `Marker`

| SLF4J 2.x (Java) | lib_logging (Ecstasy) |
|---|---|
| `MarkerFactory.getMarker("AUDIT")` | `MarkerFactory.getMarker("AUDIT")` |
| `MarkerFactory.getDetachedMarker("X")` | `MarkerFactory.getDetachedMarker("X")` |
| `marker.add(child)`, `marker.remove(child)` | `marker.add(child)`, `marker.remove(child)` |
| `marker.hasReferences()` | `marker.hasReferences` |
| `marker.iterator()` | `marker.references` |
| `marker.contains(other)` | `marker.contains(other)` |
| `marker.contains("NAME")` | `marker.containsName("NAME")` |
| `marker.getName()` | `marker.name` |

## `MDC`

| SLF4J 2.x (Java) | lib_logging (Ecstasy) |
|---|---|
| `MDC.put("k", "v")` | `mdc.put("k", "v")` |
| `MDC.get("k")` | `mdc.get("k")` |
| `MDC.remove("k")` | `mdc.remove("k")` |
| `MDC.clear()` | `mdc.clear()` |
| `MDC.getCopyOfContextMap()` | `mdc.copyOfContextMap` |

In Ecstasy the MDC is acquired by injection (`@Inject MDC mdc;`) rather than via a static
class. The semantics — per-fiber/thread scratchpad readable by sinks — are the same.

## `LoggingEventBuilder` (SLF4J 2.x fluent API)

| SLF4J 2.x (Java) | lib_logging (Ecstasy) |
|---|---|
| `b.setMessage("...")` | `b.setMessage("...")` |
| `b.addArgument(x)` | `b.addArgument(x)` |
| `b.addMarker(m)` | `b.addMarker(m)` |
| `b.setCause(e)` | `b.setCause(e)` |
| `b.addKeyValue("k", v)` | `b.addKeyValue("k", v)` |
| `b.log()` | `b.log()` |
| `b.log("msg")` | `b.log("msg")` |
| `b.log("fmt", a)` | `b.log("fmt", a)` |

## Message format

SLF4J's `{}` placeholder semantics are reproduced verbatim by `MessageFormatter.format`:

- Each unescaped `{}` consumes the next argument's `toString()`.
- Excess arguments are dropped; excess placeholders are left literal.
- `\{}` is an escape; the literal `{}` is emitted.
- `\\{}` is an escaped backslash followed by a real placeholder.
- The last argument, if it is an `Exception` and there is no remaining placeholder, is
  promoted to the cause. (SLF4J calls this "throwable promotion".)

## What we don't replicate

- SLF4J's static `LoggerFactory` returns logger instances unconditionally; ours is a
  service that accesses an injected sink. Behaviorally identical for users.
- SLF4J's `EventRecodingLogger` / `SubstituteLoggerFactory` machinery for handling
  pre-init log calls — we don't have an init race because injection resolves at scope
  entry.
- SLF4J's `Marker.iterator()` is a Java `Iterator<Marker>`; ours is an Ecstasy
  `Iterator<Marker>`. Identical concept.

## What we add

- Native injection (`@Inject Logger logger;`). SLF4J has nothing like this; it relies on
  the static factory. Injection makes per-container sink override trivial — see
  `../design/design.md` for the documented override pattern.
- `Off` level, useful for sink configuration. SLF4J expresses this through level checks
  in `Logger.isEnabledFor(...)`.


---

_See also [../README.md](../README.md) for the full doc index and reading paths._
