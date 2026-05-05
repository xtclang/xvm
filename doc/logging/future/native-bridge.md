# Native bridge — could we plug real Java logging libraries in?

A research document. Investigates whether it's technically feasible — and whether it's
*advisable* — to ship `lib_logging` with a `LogSink` whose implementation is *native
Java code* wrapping the real `slf4j-api` / `logback-classic` libraries via Ecstasy's
existing JIT bridge.

The short answer:

- **Feasibility: high.** The bridge already does this for `jline`. Adding SLF4J + Logback
  is a small engineering task.
- **Branch decision: don't ship it in this POC.** The pure-Ecstasy `LogSink` design is
  the canonical path because it wins on portability, debuggability, and surface-area
  minimisation. A native bridge remains an escape hatch for users who want to keep
  existing Java Logback configuration assets unchanged.

The rest of this doc walks through the evidence, the trade-offs, and what a native
bridge would look like if we built one later.

## Evidence the bridge can carry external Java dependencies

Ecstasy's runtime in `javatools_jitbridge/` already contains worked examples of native
services delegating to Java libraries.

### TerminalConsole wraps `jline`

The Ecstasy `Console` interface (`lib_ecstasy/src/main/x/ecstasy/io/Console.x`) is
implemented natively by `javatools_jitbridge/src/main/java/org/xtclang/_native/io/TerminalConsole.java`.
That class extends `nService` (the native-service base) and delegates to `xTerminalConsole`,
which imports `org.jline.reader` and `org.jline.terminal` from the third-party `jline`
library (declared in `gradle/libs.versions.toml` as `jline = "4.0.12"`).

This proves three things:

1. The bridge accepts third-party Java dependencies.
2. The build picks them up cleanly through the version catalog.
3. The pattern of "Ecstasy interface → native `nService` subclass → external Java
   library" works end-to-end.

### SLF4J and Logback are already in the catalog

`gradle/libs.versions.toml`:

```toml
lang-logback   = "1.5.32"
lang-slf4j     = "2.0.17"

lang-slf4j-api = { module = "org.slf4j:slf4j-api",        version.ref = "lang-slf4j"  }
lang-logback   = { module = "ch.qos.logback:logback-classic", version.ref = "lang-logback" }
```

These are declared today for the lang/IDE tooling. They aren't currently consumed by
`javatools_jitbridge`, but adding them to `javatools_jitbridge/build.gradle.kts` is a
two-line change.

## How native services bind to Ecstasy interfaces

Concretely, the binding pattern (from inspecting `TerminalConsole.java`):

- The Ecstasy `Console` interface declares `void print(Object object = "", Boolean
  suppressNewline = False)`.
- The Java side implements a method `print$p(Ctx ctx, nObj object, boolean
  suppressNewline, boolean dfltSuppressNewline)`. The `$p` suffix is convention; the
  trailing `dflt*` booleans tell the native code which arguments were defaulted vs
  passed explicitly.
- Argument marshalling: `String` becomes a custom Ecstasy `String` (UTF-8/compressed),
  `Object` becomes `nObj`, primitive `Boolean` becomes Java `boolean`. To convert an
  Ecstasy `Object` to a Java `String`, the native code calls `obj.toString(ctx)`.

The bridge has no example yet of a native method receiving a heterogeneous `Object[]`
or invoking back into Ecstasy code. The architecture supports it (via
`nFunction.stdMethod.invokeExact`); it just hasn't been used.

## Resource registration

`javatools_jitbridge/src/main/java/org/xtclang/_native/mgmt/nMainInjector.java` line ~51:

```java
suppliers.put(new Resource(consoleType, "console"), TerminalConsole::$create);
```

For `lib_logging`, we'd add:

```java
suppliers.put(new Resource(loggerType,   "logger"),  RTLogger::$create);
suppliers.put(new Resource(logSinkType,  "default"), RTLogbackSink::$create);
```

The interpreter-side `NativeContainer` now resolves by `(resource name, requested type)`;
the JIT bridge should follow the same rule if this experiment grows into a JIT-backed
logging bridge.

The suppliers map is per-Injector instance, so each container can choose its own
sink. There is no JVM-global state; this matches Ecstasy's container isolation
philosophy.

## What a native Logback-backed sink would look like

`javatools_jitbridge/src/main/java/org/xtclang/_native/logging/RTLogbackSink.java`:

```java
public class RTLogbackSink extends nService implements LogSink {

    public static RTLogbackSink $create(Ctx ctx, nObj opts) {
        return new RTLogbackSink();
    }

    public boolean isEnabled$p(
            Ctx ctx,
            org.xtclang.ecstasy.text.String loggerName,
            nObj level,
            nObj marker,
            boolean dfltMarker) {
        org.slf4j.Logger l = org.slf4j.LoggerFactory.getLogger(loggerName.toString(ctx));
        return switch (toJavaLevel(level)) {
            case TRACE -> l.isTraceEnabled();
            case DEBUG -> l.isDebugEnabled();
            case INFO  -> l.isInfoEnabled();
            case WARN  -> l.isWarnEnabled();
            case ERROR -> l.isErrorEnabled();
        };
    }

    public void log$p(Ctx ctx, nObj eventObj) {
        // unpack eventObj into (loggerName, level, message, marker, exception, mdc, ts)
        // call org.slf4j.LoggerFactory.getLogger(name).atLevel(level)
        //     .addMarker(...).setCause(...).log(message);
    }
}
```

This wraps `org.slf4j.Logger` directly. Logback (already on the classpath) handles
configuration, filtering, appenders, layouts, async, rolling files — everything from
its existing `logback.xml`.

For an Ecstasy host running this sink:
- Existing `logback.xml` files work as-is.
- Existing Logback expertise transfers directly.
- Existing operational tooling (log shippers expecting Logback's wire formats) keeps
  working.

## Trade-offs

### Pros of going native

| Pro | Detail |
|---|---|
| **Zero re-implementation cost** | We get Logback's filters, layouts, async, rolling, MDC rendering for free. The Java SLF4J/Logback ecosystem is mature; reproducing a fraction of it in pure Ecstasy is months of work. |
| **Existing config files just work** | Teams porting from Java keep their `logback.xml` and operational dashboards. |
| **Performance** | Logback is heavily tuned. Pure-Ecstasy reimplementations would take time to match. |
| **Battle tested** | Decades of production use means corner cases (rolling file mid-write, broken pipe on a TCP appender, GC pause behaviour) are known and handled. |

### Cons of going native

| Con | Detail |
|---|---|
| **JVM lock-in** | `lib_logging` becomes JVM-only at the native-sink layer. A future Ecstasy runtime (native, WASM, embedded) needs an equivalent native sink for that platform, or has to fall back to a pure-Ecstasy sink. |
| **Marshalling cost** | Every log call crosses the bridge: `Object → nObj`, then `nObj.toString(ctx)` to materialize a Java `String`. For high-volume disabled-level calls this is a measurable hit even after the level check. (Disabled calls don't cross; that's fine. The cost is on enabled calls.) |
| **Surface area** | Logback brings in dozens of classes, transitive deps, and configuration knobs we don't control. A bug in Logback becomes an Ecstasy-runtime bug from the user's perspective. |
| **Debug story** | Ecstasy stack traces stop at the bridge boundary; the inside of Logback shows up as opaque Java frames. Users debugging a misconfigured appender see Java-shaped errors, not Ecstasy-shaped ones. |
| **Configuration model coupling** | If we ship a native Logback sink, users will write `logback.xml`. If a future pure-Ecstasy `lib_logging_logback` ships, they have two ways to configure logging that look the same but don't interoperate. |
| **Bootstrap weirdness** | Logback initialises during classloading. The order of operations between Logback's static init and the Ecstasy injector's `addNativeResources()` call is fragile — solvable, but a footgun. |
| **Dependency churn** | We've now made every Ecstasy program depend transitively on `slf4j-api` + `logback-classic` (~3 MB of JARs). For programs that just want to log to console, that's overkill. |

### Marshalling cost — concrete sketch

For an enabled `info` call, the per-call cost crossing the bridge once would be roughly:

- One nObj→Java String conversion for the logger name.
- One nObj→Java String conversion for the message (after Ecstasy-side `MessageFormatter`
  does the substitution).
- One Object[]→Java `Object[]` conversion if SLF4J 2.x's `KeyValuePair` story is used.
- One MDC map copy across the bridge if the sink wants MDC.

All allocating, all per-call. For a service emitting 10K log lines per second the
overhead is in the milliseconds-per-second range — non-trivial but not catastrophic.
For most applications the level check screens out the bulk of calls anyway.

## Recommendation

Three options, ordered by recommendation:

### Option A (recommended): pure-Ecstasy `LogSink` is the primary path

Ship `ConsoleLogSink` plus the base pure-Ecstasy backend primitives as the default.
Build any `lib_logging_logback` configuration module on top of those primitives.

A native sink, if we ever ship one, is *one* of multiple `LogSink` choices, not the
default. Users who want the Java Logback experience opt in explicitly.

This keeps `lib_logging` portable (any Ecstasy runtime, not just JVM-hosted), keeps
the dependency graph small, and keeps stack traces uniform.

### Option B: native sink as a separate optional module

Ship `lib_logging_native_logback` (or similar) as an optional XDK lib that pulls in
SLF4J + Logback. The runtime injector picks it up if present. Users who need
production-grade Logback today can use this; users who don't aren't paying for it.

Concretely: a new lib_logging_native_logback module, a new
`javatools_jitbridge/src/main/java/org/xtclang/_native/logging/RTLogbackSink.java`, a
small change to `nMainInjector` to register it conditionally. Estimated work: under a
week of focused effort once the pure-Ecstasy `lib_logging` v0 is solid.

### Option C: native sink as the default

Don't recommend. It's the path of least immediate work but it pins us to the JVM and
inherits Logback's surface area into Ecstasy's contract. Hard to undo.

## Conclusion

The native bridge angle is worth knowing about because:

1. It's technically feasible — the `jline` precedent is direct evidence.
2. It's a useful escape hatch for teams porting JVM workloads who have heavy
   investment in `logback.xml` configurations they don't want to rewrite.
3. The existence of the option *constrains the API design* of `lib_logging` itself —
   anything in the SLF4J 2.x surface that a native sink would want to forward to
   `org.slf4j.Logger.atInfo()...log()` should be expressible in our `LoggingEventBuilder`.
   We've designed for that.

But: the long-term answer is a pure-Ecstasy implementation. We get there incrementally:
ship the API, ship the base backend primitives, add destination/configuration modules
written in Ecstasy, and optionally ship a native bridge for legacy interop.

The most important property is that none of these decisions break caller code. The
`Logger` interface and the `LogSink` boundary make all of this swappable.


---

_See also [../README.md](../README.md) for the full doc index and reading paths._
