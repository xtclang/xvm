# Unified Logging And JFR Telemetry

Status: design only. Nothing in this document is implemented. The whole slice
is parked as nice-to-have behind all must-fix and must-audit work; see the
"Nice to have (do last)" row in
[../must-audit-backlog.md](../must-audit-backlog.md). This document exists so
the design is decided once, on paper, and can be picked up later as a small
standalone PR sequence.

This plan extends the decisions already recorded in
[../logging-strategy.md](../logging-strategy.md) — repo-owned facade
(`XtcLog` + `LogCat` + `LogSink` in `javatools_utils`), no `org.slf4j` at call
sites, no per-class static logger constants, no call-site guards (the Tier 2
constant gate lives inside the facade), an ungated `warn`/`error` reporting
plane, and pluggable/composable sinks. Nothing here changes those decisions.
This document adds the deferred Tier 3: one emission API whose sinks include
both text loggers and JFR, so a single call site produces a human-readable log
line and a typed, recordable, streamable JFR event.

## Problem Statement

The repo needs two kinds of observability from the same decision points:

- a text line a developer can read while reproducing a bug
  (console, SLF4J/logback in the LSP, Gradle's logger in the plugin);
- a structured, typed record that tooling can filter, aggregate, and assert on
  (which constant moved between which pools, which type relation was decided
  with what reason, which container owned the composition).

Instrumenting twice — a log call here, a JFR event there — guarantees the two
channels drift apart, doubles the review surface, and makes every hot-path
cost analysis twice as hard. The requirement is one call site, one emission,
fanned out by configuration:

```
call site ──> XtcLog.event(...) ──> LogSink (active configuration)
                                      ├── ConsoleLogSink   -> stderr text
                                      ├── Slf4jLogSink     -> host logging
                                      └── JfrLogSink       -> typed JFR events
```

Boundaries that this mechanism explicitly does NOT absorb:

- `ErrorListener` remains the compiler/verifier diagnostic channel with
  source positions, severities, and abort behavior. User-facing compile
  errors never become log events.
- `Console`/`Launcher.log(...)` remain the CLI's user-visible output.
- The future `DiagnosticEvent`/`DiagnosticContext` model (PR 15 in
  [github-issue-breakdown.md](github-issue-breakdown.md)) is the primary
  representation for compiler/runtime *diagnostic decisions* with
  owner/request/phase/document identity. When it lands, console rendering,
  logging, and LSP publication become subscribers of that model. The unified
  telemetry here is the *developer/operational plane* underneath it; a
  `DiagnosticEvent` subscriber can trivially forward into `XtcLog`, but
  telemetry events do not carry the compiler diagnostic contract.
- Print-and-continue-after-failure sites are excluded on principle. Per
  [../exception-hygiene-audit.md](../exception-hygiene-audit.md), a failure
  that invalidates the result gets an exception, not a nicer log line. The
  survey below marks those sites; they are fixed by the exception-hygiene
  work, not by this plan.

## Survey: Print-Only Sites And What They Become

`rg -n "System.err.println|printStackTrace" javatools/src/main/java` finds
roughly forty production sites. Representative classification (line numbers
from branch `lagergren/lazy-instance`, 2026-08-24):

| Site | Current shape | Classification | Becomes |
| --- | --- | --- | --- |
| `compiler/ast/ConvertExpression.java:104` | `System.err.println("No conversion found for " + aVal[i])` | Developer plane (compiler decision trace) | `XtcLog` event on `COMPILER`; later an `ErrorListener`/`DiagnosticEvent` candidate |
| `compiler/ast/Expression.java:814` | same message in fit/validation path | Developer plane | same event name, so both emitters correlate |
| `runtime/ClassComposition.java:322` and `:334` | `"WARNING: Foreign method chain for " + sig` | Developer plane, high-value ownership evidence | typed `OWNER` event with pool identities |
| `asm/constants/TypeConstant.java:5971` | `"rejecting isA() due to a recursion: " + sRecursion` | Developer plane, type-system evidence | typed `TYPE` relation event with `reason=recursion` |
| `asm/constants/TypeInfoReal.java:1567` | `"conflicting " + sigBest + " vs. " + sigTest` soft assert | Developer plane; possible future compiler diagnostic | typed `TYPE` conflict event |
| `runtime/template/_native/web/xRTServer.java:781`, `:787`, `:880` | `Handy.logTime() + " Trace: ..."` TLS/route traces, `TODO: REMOVE` | Developer plane (service tracing) | `SERVICE` events; the hand-rolled timestamp disappears (both sinks timestamp) |
| `asm/FileRepository.java:206`, `asm/DirRepository.java:371` | `System.out.println("Error loading module from file: ...")`, return null | Split: best-effort scan is reporting plane; requested load is a must-fix exception item | scan path: `warn` + typed event; requested path: excluded — cause-preserving failure per the open must-fix item |
| `asm/ConstantPool.java:3498` | `"Unsupported tuple type: ..."` soft assert, returns `INCOMPATIBLE` | Reporting plane (invariant soft assert that must stay visible by default) | ungated `warn` on `TYPE` |
| `runtime/MainContainer.java:203` | `"Missing: " + sMethodName + " method for ..."`, then `return` | Must-fix exception class: invocation setup failure reported as text, method returns as if fine | excluded from log migration; tracked in the exception-hygiene audit |

### Before/After: Compiler Conversion Decision

Current (`ConvertExpression.java:104`; `Expression.java:814` is the same
shape):

```java
Constant constNew = convertConstantValue(aVal[i], aType[i]);
if (constNew == null) {
    // there is no compile-time conversion available; continue with run-time
    // conversion
    System.err.println("No conversion found for " + aVal[i]);
}
```

With the unified API (no logger field, no guard; the facade's gate folds the
whole call away in normal runs):

```java
Constant constNew = convertConstantValue(aVal[i], aType[i]);
if (constNew == null) {
    // no compile-time conversion; fall back to run-time conversion
    XtcLog.event(COMPILER, DEBUG, "compiler.convert.miss",
            "constant", aVal[i].getValueString(),
            "target", aType[i].getValueString());
}
```

One emission, two renderings:

```text
DEBUG compiler.convert.miss constant="42" target="test:Dec64"
```

```text
org.xvm.compiler.ConvertMiss {
  startTime = 12:01:31.104
  constant = "42"
  target = "test:Dec64"
  eventThread = "main" (javaThreadId = 1)
}
```

Note the argument rule from `logging-strategy.md` still applies:
`getValueString()` is not provably pure, so on a *hot* path this site would
use the Tier 1 guard (`if (XtcLog.isDebugEnabled(COMPILER))`). Constant
folding misses are rare, so the ungarded shape is fine here.

### Before/After: Foreign Method Chain (Ownership Evidence)

Current (`ClassComposition.java:322`):

```java
} else {
    // what else can we do here?
    fCache = false;
    System.err.println("WARNING: Foreign method chain for " + sig); // TODO: remove
}
```

After:

```java
} else {
    // cannot cache under a foreign pool's signature; compute without caching
    fCache = false;
    XtcLog.event(OWNER, WARN, "owner.chain.foreign",
            "sig", sig.getValueString(),
            "sigPool", XtcLog.id(sig.getConstantPool()),
            "clzPool", XtcLog.id(pool));
}
```

This is exactly the event the ownership sweeps want: it is a live sighting of
a cross-pool structure reaching a composition. As a `WARN` it stays on the
ungated reporting plane, so it keeps printing by default — but now it also
lands in any active JFR recording with both pool identities as separate
fields, so a stress run can count and group these sightings instead of
grepping stderr.

### Before/After: Repository Scan Failure

Current (`FileRepository.java:206`):

```java
} catch (Exception e) {
    System.out.println("Error loading module from file: " + file + "; " + e.getMessage());
}
err = true;
return null;
```

After (best-effort scan path only; the requested-module load path is an open
must-fix that needs a cause-preserving failure, not a log):

```java
} catch (Exception e) {
    XtcLog.event(STARTUP, WARN, "repo.module.unreadable", e,
            "file", file.getPath());
}
err = true;
return null;
```

The cause travels as a real `Throwable`: the console sink prints
`e.toString()`, SLF4J gets the full stack, and the JFR generic event records
the exception class and message as fields.

### Before/After: TLS Route Trace

Current (`xRTServer.java:781`):

```java
if (route == null) {
    // TODO: REMOVE
    System.err.println(Handy.logTime() + " Trace: Handshake with unknown host: " + sHost);
}
```

After:

```java
if (route == null) {
    XtcLog.event(SERVICE, TRACE, "web.tls.unknownHost", "host", sHost);
}
```

The `TODO: REMOVE` tension dissolves: the trace costs nothing in normal runs
(gated), so it can stay permanently instead of being deleted the day after it
is next needed.

## Type-System Deep-Dive: Where Structured Visibility Pays

The most valuable channels are the ones where a text log is nearly useless
because the interesting question is aggregate ("how many relation decisions
crossed pools during this run?", "which types were decided by duck-typing?"),
which is exactly what JFR recordings answer.

### Relation Decisions And Cache Hits

`TypeConstant.calculateRelation(...)` already has the perfect emission points:
the cache probe at `TypeConstant.java:5938` and every decision store into
`mapRelations`. Instrumented (helper method keeps the hot path readable; the
Tier 1 guard is used here because `getValueString()` is expensive and this is
one of the hottest paths in the compiler):

```java
Relation relation = mapRelations.get(typeLeft);
if (relation != null) {
    if (XtcLog.isTraceEnabled(TYPE)) {
        XtcLog.event(TYPE, TRACE, "type.relation.cacheHit",
                "left", typeLeft.getValueString(),
                "right", this.getValueString(),
                "result", relation.name());
    }
    return relation;
}
```

and at the recursion rejection (`TypeConstant.java:5971`, replacing the
stderr soft assert while keeping the existing de-duplication set):

```java
if (s_setRecursions.add(sRecursion)) {
    XtcLog.event(TYPE, DEBUG, "type.relation.decided",
            "left", typeLeft.getValueString(),
            "right", this.getValueString(),
            "result", "INCOMPATIBLE",
            "reason", "recursion");
}
```

What the operator sees. Text plane (`-Dxvm.log.dev=true`):

```text
DEBUG type.relation.decided left="test:I" right="test:C" result=INCOMPATIBLE reason=recursion
```

JFR plane, after `jcmd <pid> JFR.start name=xvm settings=xvm-type.jfc`:

```text
$ jfr print --events org.xvm.type.Relation recording.jfr

org.xvm.type.Relation {
  startTime = 12:01:33.482
  left = "test:I"
  right = "test:C"
  result = "INCOMPATIBLE"
  reason = "recursion"
  cacheHit = false
  eventThread = "xvm:ServiceContext[TestApp]" (javaThreadId = 42)
}
```

and the aggregate question is one command:

```bash
jfr print --events org.xvm.type.Relation recording.jfr \
  | grep 'reason = ' | sort | uniq -c | sort -rn
```

### TypeInfo Conflict Soft Assert

`TypeInfoReal.java:1567` currently prints `"conflicting ..."` and picks a
winner. As an event, the pick becomes auditable evidence:

```java
// soft assert: neither signature subsumes the other; keep the current best
XtcLog.event(TYPE, WARN, "typeinfo.method.conflict",
        "best", sigBest.getValueString(),
        "test", sigTest.getValueString(),
        "type", f_type.getValueString());
return methodBest;
```

`WARN` keeps it visible by default (it is a should-not-happen), and a JFR
recording of a big compile shows every conflict with the owning type — the
input needed to decide whether this becomes a real `ErrorListener` diagnostic.

### Constant Adoption Across Pools

`Constant.copyForAdoption(...)`/`adoptedBy(...)` is the choke point for
cross-pool constant movement — the exact traffic the adoption audit cares
about. One event in the final `adoptedBy(...)` wrapper:

```java
XtcLog.event(POOL, TRACE, "pool.constant.adopt",
        "kind", getFormat().name(),
        "constant", getValueString(),
        "from", XtcLog.id(getConstantPool()),
        "to", XtcLog.id(poolTarget));
```

A same-JVM stress run with a JFR recording then yields a complete adoption
ledger per iteration: which constant kinds moved, between which pools, in
what volume — sweepable with `jfr print` or the streaming API rather than by
reading interleaved stderr from parallel containers.

### Composition Owner Assignment

`ClassComposition`/template resolution under `Container.getTemplate(...)`
gains one `OWNER` event per composition creation (`DEBUG`, gated), carrying
container id, pool id, and template identity. Combined with the foreign-chain
`WARN` above, a recording contains both the normal owner-assignment baseline
and the anomalies, which is what makes the anomalies interpretable.

## Unified API Design

### One Emission, Two Shapes

The facade from `logging-strategy.md` is extended with a structured emission
entry point and a structured sink overload. Text sinks render the key/value
pairs; the JFR sink maps them to typed events.

```java
// javatools_utils, org.xvm.util.logging

public record LogEvent(LogCat cat, LogLevel level, String name,
                       Throwable cause, Object[] kv) {
    /** Renders "name k1=v1 k2=v2" for text sinks. */
    public String render() { ... }
}

public interface LogSink {
    boolean isEnabled(LogCat cat, LogLevel level);

    /** Text plane. */
    void accept(LogCat cat, LogLevel level, String message, Throwable cause);

    /** Structured plane; default keeps existing sinks working unchanged. */
    default void accept(LogEvent event) {
        accept(event.cat(), event.level(), event.render(), event.cause());
    }
}

public final class XtcLog {
    /** Text developer plane gate. */
    static final boolean DEV = Boolean.getBoolean("xvm.log.dev");
    /** JFR emission capability gate. */
    static final boolean JFR = Boolean.getBoolean("xvm.jfr");

    /** Structured emission: name plus alternating key/value pairs. */
    public static void event(LogCat cat, LogLevel level, String name, Object... kv) {
        if (live(cat, level)) {
            sink().accept(new LogEvent(cat, level, name, null, kv));
        }
    }

    public static void event(LogCat cat, LogLevel level, String name,
                             Throwable cause, Object... kv) {
        if (live(cat, level)) {
            sink().accept(new LogEvent(cat, level, name, cause, kv));
        }
    }

    private static boolean live(LogCat cat, LogLevel level) {
        // reporting plane is never gated; developer plane needs a gate
        return (level.compareTo(LogLevel.INFO) >= 0 || DEV || JFR)
                && sink().isEnabled(cat, level);
    }

    /** Owner identity helper: "ConstantPool@3f2a1b" style, side-effect free. */
    public static String id(Object owner) { ... }

    // trace/debug/info/warn/error convenience entry points and setSink(...)
    // as already specified in logging-strategy.md
}
```

Rules carried over unchanged from `logging-strategy.md`: entry points stay
tiny so they inline; when `DEV` and `JFR` are both `false`, developer-plane
call sites fold to zero instructions (arguments included, subject to the
purity rules); expensive argument computation on genuinely hot paths uses the
Tier 1 `isTraceEnabled(cat)` guard.

### String Versus Typed: The Payload Contract

The tension: text sinks want one string; JFR wants typed fields; and
`javatools_utils` must not depend on ASM/runtime types. Resolution:

- Payloads are flat alternating `key, value` pairs. Values are `String`,
  boxed primitives, or enums — never `TypeConstant`, `Container`,
  `ConstantPool`, handles, or other owner-bearing objects. Reduction to
  strings/ids happens at the emission site (`getValueString()`,
  `XtcLog.id(...)`), which is also what keeps owner objects out of any sink's
  retained state.
- Event *names* are stable dotted identifiers (`type.relation.decided`,
  `pool.constant.adopt`, `owner.chain.foreign`). They are the join key
  between the text plane, the JFR plane, and future golden tests.
- The JFR sink owns a small registry from event name to a typed
  `jdk.jfr.Event` subclass. Registered names get first-class typed events;
  unregistered names fall back to a generic `org.xvm.Log` event whose fields
  are `category`, `level`, `name`, `message` (the rendered pairs), and
  `exception`. This means every emission is always recordable, and promoting
  a channel to a typed event is a sink-side change that touches no call site.
- Alternative considered and rejected for the first slice: per-category
  record payloads (`record RelationDecision(String left, ...)`) as the
  emission argument. Stronger typing at the call site, but it forces a
  payload class per event name in `javatools_utils` and a matching overload
  set; the flat-pair form with a name registry gets the same JFR shape for a
  tenth of the API surface. Revisit if event names proliferate past a few
  dozen.

### LogCat To JFR Mapping

| Facade concept | JFR concept |
| --- | --- |
| `LogCat.TYPE` | `@Category({"XVM", "Type System"})` |
| `LogCat.POOL` | `@Category({"XVM", "Constant Pool"})` |
| `LogCat.OWNER` | `@Category({"XVM", "Ownership"})` |
| event name `type.relation.decided` | `@Name("org.xvm.type.Relation")` |
| `LogLevel` TRACE/DEBUG | event disabled by default in `.jfc`; enabled per recording |
| `LogLevel` INFO/WARN/ERROR | event enabled by default in `.jfc` |
| `Throwable cause` | `exceptionClass`/`exceptionMessage` string fields |

A shipped `xvm.jfc` settings file (two profiles, `xvm-default` and
`xvm-typesystem`) gives `jcmd JFR.start settings=...` something to grab:

```xml
<event name="org.xvm.type.Relation">
  <setting name="enabled">false</setting>
  <setting name="stackTrace">false</setting>
</event>
<event name="org.xvm.Log">
  <setting name="enabled">true</setting>
</event>
```

### Typed Event Example

```java
// javatools_utils, org.xvm.util.logging.jfr

@Name("org.xvm.type.Relation")
@Label("Type Relation Decision")
@Category({"XVM", "Type System"})
@StackTrace(false)
public final class TypeRelationEvent extends Event {
    @Label("Left Type")   public String left;
    @Label("Right Type")  public String right;
    @Label("Result")      public String result;
    @Label("Reason")      public String reason;
    @Label("Cache Hit")   public boolean cacheHit;
}
```

`jdk.jfr` is a JDK module; no dependency is added anywhere.

### The JFR Sink

```java
public final class JfrLogSink implements LogSink {
    @Override
    public boolean isEnabled(LogCat cat, LogLevel level) {
        return FlightRecorder.isAvailable();
    }

    @Override
    public void accept(LogEvent e) {
        switch (e.name()) {
            case "type.relation.decided", "type.relation.cacheHit" -> {
                var ev = new TypeRelationEvent();
                if (ev.isEnabled()) {
                    ev.left     = str(e, "left");
                    ev.right    = str(e, "right");
                    ev.result   = str(e, "result");
                    ev.reason   = str(e, "reason");
                    ev.cacheHit = e.name().endsWith("cacheHit");
                    ev.commit();
                }
            }
            default -> {
                var ev = new XvmLogEvent();
                if (ev.isEnabled()) {
                    ev.category = e.cat().name();
                    ev.level    = e.level().name();
                    ev.name     = e.name();
                    ev.message  = e.render();
                    if (e.cause() != null) {
                        ev.exceptionClass   = e.cause().getClass().getName();
                        ev.exceptionMessage = e.cause().getMessage();
                    }
                    ev.commit();
                }
            }
        }
    }

    @Override
    public void accept(LogCat cat, LogLevel level, String message, Throwable cause) {
        accept(new LogEvent(cat, level, "log", cause, new Object[] {"message", message}));
    }
}
```

Per-event `isEnabled()` means an idle JFR sink (recording not running, or the
event type disabled in the active `.jfc`) does no field population and no
commit; the event allocation does not escape and is removed by escape
analysis. The composite arrangement for the common "text + JFR" configuration
is `XtcLog.setSink(XtcLog.compositeOf(new ConsoleLogSink(), new JfrLogSink()))`.

### Gate Policy: How Tier 2 And "Always-Capable JFR" Coexist

JFR's model wants events always compiled in, enablement decided per
recording. Tier 2 wants developer-plane call sites to fold to zero. Options:

1. One gate (`xvm.log.dev`) covering both planes. Simplest, but a JFR-only
   investigation then also pays for text-plane formatting readiness, and
   text-only debugging compiles in JFR emission.
2. Two independent gates: `xvm.log.dev` (text developer plane) and `xvm.jfr`
   (JFR emission capability). Each is a `static final`; each folds
   independently; `live(...)` above ORs them for developer-plane levels.
3. No gate on JFR emission (always capable). Rejected: argument reduction
   (`getValueString()`, `XtcLog.id(...)`) at every developer-plane call site
   would run whenever any sink reports enabled, and even with careful sinks
   the varargs boxing is only removable when the whole call is dead.

Recommendation: option 2. A normal run has both gates off and pays zero. A
diagnosis run restarts once with `-Dxvm.jfr=true` (and/or `-Dxvm.log.dev=true`),
after which recordings can be started, stopped, dumped, and re-profiled
repeatedly via `jcmd` with no further restarts — that is the operational
payoff JFR adds over the text plane. The reporting plane (`INFO`/`WARN`/
`ERROR`) is never gated on either flag, exactly as specified in
`logging-strategy.md`.

### Testing With RecordingStream (JEP 349)

Tests assert on typed fields, not console text:

```java
class TypeRelationTelemetryTest {
    /**
     * The recursion rejection in TypeConstant.calculateRelation() historically
     * reported itself only as a de-duplicated stderr line. This asserts the
     * decision surfaces as a typed event with the reason field intact.
     */
    @Test
    void recursionRejectionEmitsTypedRelationEvent() throws Exception {
        var seen  = new CopyOnWriteArrayList<RecordedEvent>();
        var latch = new CountDownLatch(1);

        try (var rs = new RecordingStream()) {
            rs.enable("org.xvm.type.Relation");
            rs.onEvent("org.xvm.type.Relation", ev -> {
                seen.add(ev);
                latch.countDown();
            });
            rs.startAsync();

            compileRecursiveDuckTypeFixture();   // triggers the rejection

            assertTrue(latch.await(10, TimeUnit.SECONDS),
                    "expected a type.relation event");
        }

        var ev = seen.getFirst();
        assertEquals("INCOMPATIBLE", ev.getString("result"));
        assertEquals("recursion",    ev.getString("reason"));
    }
}
```

## Use Cases

- **LSP host**: binds `compositeOf(new Slf4jLogSink(), new JfrLogSink())`.
  Day-to-day, javatools events flow into the language server's logback files.
  When a user reports "types resolve wrong after edit 40 in this session",
  support starts a JFR recording in the running server, replays the edits,
  and reads the relation/TypeInfo event stream — no restart, no code change.
- **Same-JVM stress runs**: `runDirectSequenceStress` starts a recording per
  run and archives the `.jfr` next to the test report. The ownership sweep
  gains a second data source: every `owner.chain.foreign` and
  `pool.constant.adopt` sighting across all iterations, greppable and
  countable offline. A stress failure ships with its own flight recording.
- **Production-adjacent triage**: a long-running embedded XVM shows drift;
  `jcmd <pid> JFR.start name=xvm settings=xvm.jfc duration=5m` captures type
  and pool traffic with bounded overhead and no log-volume explosion,
  because JFR buffers binary events instead of formatting text.
- **Gradle plugin**: installs a sink forwarding WARN/ERROR to Gradle's
  logger, so repository scan problems and ownership warnings land in the
  build output with Gradle's own severity handling; a build-scan-adjacent
  `.jfr` can be produced for compiler performance investigations.
- **Golden diagnostics tests**: the `CapturingLogSink` asserts event names
  and pairs for compiler decision channels; the RecordingStream pattern
  covers the typed plane. Both replace "parse stderr" tests.

## Disabled-Cost Analysis

| Configuration | Developer-plane site (`trace`/`debug`) | Reporting-plane site (`info`/`warn`/`error`) |
| --- | --- | --- |
| Both gates off (normal run) | Zero instructions: `DEV`/`JFR` are folded constants, the call body is empty, arguments are dead and eliminated (purity rules from `logging-strategy.md` apply) | One inlined `live()` check + sink `isEnabled`; on the rare enabled emission, one `LogEvent` allocation + rendering. These sites are cold by definition |
| `xvm.jfr=true`, no recording running | Argument reduction + one `LogEvent` + `JfrLogSink.accept` reaching a disabled event's `isEnabled()` — nanoseconds per emission; acceptable because the flag is an explicit diagnosis opt-in. Hot channels additionally sit behind Tier 1 guards, which stay cheap because the guard folds to the sink check | as above, plus a disabled JFR event check |
| `xvm.jfr=true`, recording running | Full emission: reduction, event population, JFR buffer write (~µs-class, JFR's own cost model) | same |
| `xvm.log.dev=true` | Text formatting + console/SLF4J write on enabled categories | unchanged |

The one structural cost this design adds over plain text logging is the
`LogEvent` record on the *enabled* path. That is deliberate: enabled-path
allocation is the price of a single fan-out point, and the disabled path —
the only path normal users ever run — stays at zero.

## Migration Plan (Standalone PR Sequence)

1. **PR A — facade + structured emission**: `LogLevel`, `LogCat`, `LogEvent`,
   `LogSink`, `XtcLog`, console/composite/capturing sinks, both gates, unit
   tests including the fold-precondition source-shape checks. No call-site
   changes yet. (This is the `logging-strategy.md` PR with `LogEvent` added.)
2. **PR B — JFR sink**: `JfrLogSink`, `XvmLogEvent` generic event, the first
   typed events (`TypeRelationEvent`, `ConstantAdoptionEvent`), `xvm.jfc`
   profiles, RecordingStream tests.
3. **PR C — print-site migration, wave 1**: the survey table above, excluding
   the must-fix-exception rows. Each site's diff is one line plus deleted
   stderr noise; each migrated channel gets a `CapturingLogSink` test.
4. **PR D — type-system channels**: relation decision/cache instrumentation
   behind Tier 1 guards, adoption events, composition owner events; a stress
   run producing and asserting on a recording.
5. Later, independently: SLF4J adapter binding in LSP/DAP (one line each),
   Gradle sink, `DiagnosticEvent` bridge when PR 15 lands.

Priority: parked. This entire sequence starts only when the must-fix list and
the open must-audit closures are done, per the task ordering in
[../must-audit-backlog.md](../must-audit-backlog.md). The design is recorded
here so that no design work blocks the pickup later.
