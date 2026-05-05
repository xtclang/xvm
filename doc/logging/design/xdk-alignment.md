# XDK lib alignment — making `lib_logging` a first-class citizen

This document audits `lib_logging` against the conventions used by the other shipped XDK
libs (`lib_ecstasy`, `lib_cli`, `lib_crypto`, `lib_json`, `lib_jsondb`, `lib_net`,
`lib_oodb`, `lib_sec`, `lib_web`, `lib_webauth`, `lib_xenia`, `lib_xunit`) and against
how they are actually used in `~/src/platform` and `~/src/examples`. Every deviation has
to be a deliberate choice.

## The XDK conventions, observed

### 1. Injectables are interface types acquired by `@Inject Type ref;`

Across the XDK and downstream code, every injected resource is an interface that the
runtime resolves to an implementation:

| Resource type | Where defined | Used like |
|---|---|---|
| `Console`         | `lib_ecstasy/src/main/x/ecstasy/io/Console.x`       | `@Inject Console console;` |
| `Clock`           | `lib_ecstasy/src/main/x/ecstasy/temporal/Clock.x`   | `@Inject Clock clock;` |
| `Random`          | `lib_ecstasy/src/main/x/ecstasy/numbers/Random.x`   | `@Inject Random random;` |
| `Directory`       | `lib_ecstasy/src/main/x/ecstasy/fs/Directory.x`     | `@Inject Directory curDir;` |
| `FileSystem`      | `lib_ecstasy/src/main/x/ecstasy/fs/FileSystem.x`    | `@Inject FileSystem fs;` |
| `ModuleRepository`| `lib_ecstasy/.../mgmt/ModuleRepository.x`           | `@Inject ModuleRepository repository;` |
| `Compiler`        | `lib_ecstasy/.../mgmt/Compiler.x`                   | `@Inject Compiler compiler;` |
| `Algorithms`      | `lib_crypto/.../crypto/Algorithms.x`                | `@Inject Algorithms algorithms;` |
| `CertificateManager` | `lib_crypto/.../crypto/CertificateManager.x`     | `@Inject CertificateManager manager;` |
| `Authenticator?`  | `lib_webauth/...`                                   | `@Inject Authenticator? authenticator;` |

The pattern is so consistent it is the language's idiom. **`Logger` slots into exactly
this list.** No code in the XDK uses a static factory to acquire a runtime resource;
that's the SLF4J/Java pattern that `lib_logging` deliberately turns into injection.

### 2. Named injection where multiple instances are reasonable

Where there is more than one of something, the resource name disambiguates:

```ecstasy
@Inject Directory curDir;
@Inject Directory testOutput;
@Inject Directory testOutputRoot;
@Inject Directory tmpDir;
@Inject("homeDir") Directory home;            // from BasicResourceProvider
```

`lib_logging` deliberately does **not** use named injection for logger names. It uses
`@Inject Logger logger;` for the root logger, then derives full-name loggers by API:
`Logger payments = logger.named("com.example.payments");`. This keeps the injector's
resource table exact-match and avoids a one-off wildcard injection rule for logging.

### 3. Defaults via the `Inject` annotation's `resourceName`

`lib_ecstasy/src/main/x/ecstasy/annotations/Inject.x` defines the annotation as
`Inject<Referent>(String? resourceName = Null, Options opts = Null)`. The `resourceName`
is the dispatch key; null means "default for this type". Every XDK lib follows this.

`lib_logging` follows the default-resource part (`@Inject Logger logger;`) but not
the named-resource part for logger names. Directory names identify distinct host
resources; logger names are application categories. Those categories belong in the
logging API (`logger.named(...)`), not in the runtime injector.

### 4. The four canonical interfaces for shareable values

Other XDK libs that ship value types reach for one or more of these mixins consistently:

- **`Freezable`** (`lib_ecstasy/src/main/x/ecstasy/Freezable.x`) — for anything that may
  cross a service boundary. Sinks are typically services; anything they receive must be
  freezable. Examples: `Path`, `URI`, `Tuple`, every `const`.
- **`Stringable`** (`lib_ecstasy/src/main/x/ecstasy/text/Stringable.x`) — for anything
  routinely formatted into log lines, JSON, or CLI output. Lets the renderer pre-size
  buffers via `estimateStringLength()`.
- **`Hashable`** (`lib_ecstasy/src/main/x/ecstasy/collections/Hashable.x`) — for anything
  that may end up as a `Map` or `Set` key.
- **`Orderable`** (`lib_ecstasy/src/main/x/ecstasy/Orderable.x`) — for anything that has
  a natural ordering.

`lib_ecstasy/.../Const.x:149-152` defines `Const` as `extends Hashable, Orderable,
Freezable, Stringable` — all four. So every `const` gets them automatically.

### 5. Const for value types, service for stateful actors, class otherwise

- **`const`** for immutable value records: `Path`, `URI`, `Time`, `Duration`, `Range`.
- **`service`** for stateful, thread-safe shared resources: `TerminalConsole`,
  `Clock`, `Random`, sinks that hold writers / queues / counters.
- **`class`** for everything else: small mutable helpers, builders.

### 6. Module declaration shape

Every lib_* declares one module file at `src/main/x/<libname>.x` with a brief module
doc-comment, naming the package as `<libname>.xtclang.org`. Subordinate types live under
`src/main/x/<libname>/<TypeName>.x`. Tests live under `src/test/x/<TestModule>.x` plus
`src/test/x/<TestModule>/<TestName>.x`.

### 7. Build script shape

Every lib_* has a tiny `build.gradle.kts`:

```kotlin
plugins {
    alias(libs.plugins.xtc)
}

dependencies {
    xdkJavaTools(libs.javatools)
    xtcModule(libs.xdk.ecstasy)
    // optional test deps:
    xtcModuleTest(libs.javatools.bridge)
    xtcModuleTest(libs.xdk.xunit.engine)
}
```

`lib_logging`'s build script is identical.

## How `~/src/platform` and `~/src/examples` use these conventions

### `~/src/platform` patterns

- `kernel.x` injects `Console`, `Clock`, `ModuleRepository`, `CertificateManager`,
  `Configuration` — all type-keyed, no resource names except where explicitly needed.
- `host/HostManager.x` injects `Directory testOutput;` — a named directory.
- `auth/OAuthEndpoint.x` and `auth/OAuthProvider.x` use `@Inject Console console;`
  followed by ad-hoc `console.print($"Info : ...")` / `console.print($"Error: ...")`
  patterns — exactly the surface `lib_logging` is meant to absorb.

### `~/src/examples` patterns

- `welcome.x` uses `@Inject Console console;` for genuine user-facing output.
- `CardGame/cardGame.x` and friends inject `Console` for game state messages — these
  remain `Console` after lib_logging adoption (they are user output, not log events).
- `chess-game/...` similarly mixes user output and would benefit from a clean split.

The clean split between "user output" (`Console`) and "diagnostic event" (`Logger`)
is exactly the property `lib_logging` introduces. Both repos already inject `Console`
the right way; they just lack a `Logger` to round out the injection surface.

## Where `lib_logging` already follows the conventions

| Convention | `lib_logging` |
|---|---|
| Injectable interface type | `Logger` and `MDC` are wired by the runtime in this branch. `LogSink`, `MarkerFactory`, and `LoggerFactory` are designed as injectable extension points but not yet registered by the interpreter's native container. |
| Named logger acquisition | `@Inject Logger logger; Logger named = logger.named("com.example");` |
| `Const` for immutable records | `LogEvent` |
| `const` for stateless/passable values | `BasicLogger`, `ConsoleLogSink`, `NoopLogSink`, `MDC`, `LogEvent` |
| `service` for stateful actors | `LoggerFactory`, `LoggerRegistry`, `MemoryLogSink` |
| `class` for mutable helpers/builders | `BasicEventBuilder`, `BasicMarker`, `MarkerFactory` |

The slog-shaped sibling follows the same injection convention: `slogging.Logger` is
also registered under the resource name `logger`, and the native injector disambiguates
by requested type.
| Module file layout | `lib_logging/src/main/x/logging.x` declaring `module logging.xtclang.org` |
| Subordinate types under same dir | `lib_logging/src/main/x/logging/<Type>.x` |
| Build script shape | matches `lib_cli` / `lib_json` exactly |
| Test layout | `lib_logging/src/test/x/LoggingTest.x` + `LoggingTest/<Test>.x` |
| `Stringable` / `Hashable` / `Freezable` on shareable types | `Marker` extends all three |

## Where `lib_logging` deliberately *does* deviate, and why

- **`Marker` is an interface, not a const.** SLF4J's contract requires markers to be
  mutable (`add(child)` / `remove(child)`). A `const` cannot be mutable. We keep
  `Marker` as an interface, implement `Freezable` for boundary crossing, and let
  `BasicMarker` enforce the SLF4J contract on the unfrozen path.

- **`LogSink` does not extend `Service`.** Most `LogSink` impls *are* services, but the
  `LogSink` interface itself doesn't require it. Reason: trivial sinks (`NoopLogSink`)
  don't need to be services and shouldn't pay for the per-fiber isolation. This is
  consistent with `Stringable` and `Hashable` — XDK interfaces leave the implementation
  policy to the implementer rather than forcing one shape.

- **`Logger` does not extend `Stringable` or `Hashable`.** Loggers are not values; they
  are facades. You wouldn't put a `Logger` in a `Map` or render it into a log line.
  Compare `Console` — also a façade interface, also not Stringable/Hashable.

- **`Level` is an enum, not a const.** Enums in Ecstasy already get `Hashable +
  Orderable + Freezable + Stringable + Comparable` automatically (via the same `Const`
  base they inherit), so there is no extra work to do — but the design choice is
  noted because it's the same shape as `lib_ecstasy/.../io/SeekDirection.x` and other
  XDK enums.

## Concrete improvements made to `lib_logging` for XDK alignment

Pre-alignment, `Marker` was a plain interface and `LogEvent` carried a `String?
markerName` because the unfreezable Marker couldn't ride on a `const`. Post-alignment:

- `Marker extends Freezable, Stringable, Hashable`.
- `BasicMarker` implements `freeze` (deep-freezes children, makes self immutable) and
  acquires `Stringable.appendTo` / `estimateStringLength` for free from `Marker`'s
  default methods.
- `LogEvent.marker` carries the full `Marker?` reference again. Sinks doing
  `containsName` filtering or `marker.references` traversal work end-to-end without
  giving up the const safety.
- The mutable `Object[] arguments` from the fluent builder is converted to
  `Constant`-mode (frozen) before being handed to the sink — same boundary-crossing
  story Ecstasy applies to every other shareable value type.

Together: every value that may end up on a `LogEvent` honours the XDK's `Freezable`
contract, every type that wants to be a map key honours `Hashable`, every type that
wants to be efficiently rendered honours `Stringable`. There is no part of
`lib_logging` that an experienced XDK engineer would point at and say "that's not how
we do things."

## Summary

`lib_logging` is now structurally indistinguishable from the other shipped XDK libs:

- Same module / file / build conventions.
- Same injection idiom.
- Same value-type interfaces (`Freezable`, `Stringable`, `Hashable`) where appropriate.
- Same const / service / class split.

The only thing missing for full first-class status is the runtime-side resource
registration (`@Inject Logger` actually resolving), which is the next chunk of work
described in `../open-questions.md` items 1, 2, and 9.


---

_See also [../README.md](../README.md) for the full doc index and reading paths._
