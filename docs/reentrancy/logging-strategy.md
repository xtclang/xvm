# Cost-Free Disabled Logging Strategy

This note investigates whether developer diagnostic logging can be integrated
with the XVM runtime, ASM/type system, and compiler without adding cost to
normal execution. It is background analysis only. It does not propose replacing
user-facing compiler diagnostics, launcher output, assertions, or ownership
validators.

## Short Recommendation

Use the existing SLF4J 2.x line from `gradle/libs.versions.toml` as the logging
API for new runtime/compiler developer diagnostics. Keep standalone
`javatools` launch classpaths on a no-op provider by default, let embedders
choose their provider, and make disabled `trace`/`debug` logging on hot paths a
strictly guarded pattern:

```java
private static final Logger TYPE_LOG = LoggerFactory.getLogger("org.xvm.asm.type");

if (TYPE_LOG.isTraceEnabled()) {
    TYPE_LOG.trace("type.relation result={} reason={} right={} left={} rightPool={} leftPool={}",
            relation,
            reason,
            typeName(typeRight),
            typeName(typeLeft),
            poolId(typeRight.getConstantPool()),
            poolId(typeLeft.getConstantPool()));
}
```

The disabled cost target is:

- one static logger reference,
- one boolean level check,
- no message construction,
- no varargs array allocation,
- no primitive boxing,
- no lambda allocation,
- no `toString()` or `getValueString()` calls,
- no MDC/ThreadLocal map mutation,
- no diagnostic dump construction.

Prefer SLF4J over `java.util.logging` for runtime/compiler diagnostics. JUL is
usable for isolated utility code, and the repo already has one JUL utility
logger, but JUL is a weaker fit for an embeddable runtime/compiler because it is
JVM-global, awkward to structure, harder to route consistently with the existing
language tooling, and its lazy supplier APIs can still allocate capturing
lambdas before a disabled log call is rejected.

## Current Repository State

### Dependencies

The core version catalog already defines SLF4J:

```toml
slf4j = "2.0.18"
slf4j-nop = { module = "org.slf4j:slf4j-nop", version.ref = "slf4j" }
```

`javatools/build.gradle.kts` currently depends on:

```kotlin
implementation(libs.slf4j.nop) // to avoid startup warnings while preserving no-op logging
```

That provider artifact brings the API transitively, but runtime/compiler source
should not rely on a transitive API if it starts importing `org.slf4j.Logger`.
The clean future build shape is:

```toml
slf4j-api = { module = "org.slf4j:slf4j-api", version.ref = "slf4j" }
slf4j-nop = { module = "org.slf4j:slf4j-nop", version.ref = "slf4j" }
```

```kotlin
dependencies {
    implementation(libs.slf4j.api)
    runtimeOnly(libs.slf4j.nop) // only for standalone javatools launch/runtime classpaths
}
```

Use a real provider only in a diagnostic launch or test configuration. If the
published `javatools` metadata would cause `runtimeOnly(libs.slf4j.nop)` to
follow embedders, isolate the no-op provider to the standalone CLI/test
classpath instead. Embedders should see `slf4j-api` and choose their own
provider. Do not bundle logback into `javatools` by default. The `lang` tools
already show the right split for out-of-process tools: SLF4J API plus logback
and explicit resource configuration. The runtime/compiler should stay API-only
plus no-op on standalone launch classpaths because it is an embeddable library
and an interpreter hot path.

### Existing Logging Patterns

Current patterns are mixed:

- `Launcher` and `Console` implement user-facing output and severity filtering.
  `--verbose` prints tool progress and options. This is not developer trace
  logging.
- `ErrorListener` carries compiler and verification diagnostics. These are
  structured user/compiler errors, not SLF4J events.
- The Gradle plugin uses Gradle's `Logger`, and the direct executor adapts
  launcher `Console.log(...)` into Gradle log levels.
- The `lang` DSL, LSP, and DAP tools already use SLF4J with logback resources.
- `javatools_utils` has an isolated JUL logger in
  `CooperativelyCleanableReference`.
- Runtime, ASM, compiler, and JIT code still contain direct `System.err`,
  `System.out`, `printStackTrace`, `Constants.DEBUG`, and one-off debug prints.
- Reentrancy diagnostics already exist as opt-in invariants and dumps:
  `xvm.runtime.validateOwnership`,
  `xvm.asm.validateConstantPoolLateRegistration`, and
  `OwnershipDiagnostics.dump(...)`.

The new strategy should not flatten those categories. User errors stay in
`ErrorListener`/`Console`. Runtime invariants stay as assertions or explicit
diagnostic properties. Developer trace logging is an additional, disabled-by-
default observation layer.

## SLF4J Versus JUL

| Topic | SLF4J 2.x API | `java.util.logging` |
| --- | --- | --- |
| Existing repo fit | Already in the catalog; `javatools` already ships `slf4j-nop`; `lang` tooling already uses SLF4J. | Present only in one utility class. No broader runtime/compiler pattern. |
| Default disabled behavior | With `slf4j-nop`, logs are discarded and startup warnings are avoided. | Always present and globally configured by the JVM. Default handlers/config can surprise embedded users. |
| Embedding | API lets an embedding host choose a provider. | Uses JVM-global logger configuration and handlers unless bridged. |
| Message style | Placeholder API and fluent builder; common ecosystem for providers. | Message supplier exists, but placeholders/structured fields are weaker. |
| Structured context | SLF4J 2 key-value API is available, provider permitting. | No comparable cross-provider key-value convention. |
| MDC | Available but should be avoided on hot runtime paths. | Equivalent context requires custom ThreadLocal/filter handling. |
| Disabled hot-path cost | Excellent only when the call site follows explicit guards. | Possible with `isLoggable(...)`, but supplier/lambda usage is easy to misuse. |
| Gradle/tooling consistency | Matches language tooling and can be bridged/configured by launch classpath. | Diverges from existing Gradle/SLF4J tooling story. |

Recommendation: use SLF4J for new runtime/compiler developer logs. Leave the
existing JUL utility logger alone until that package has a broader logging
policy. Do not mix JUL and SLF4J in the same diagnostic subsystem.

## Required Semantics

Logging must serve debugging without changing runtime/compiler behavior:

- Type relation decisions must be observable without forcing type names,
  relation maps, auto-narrowing, or `TypeInfo` computation when disabled.
- Owner assignment must expose `Container`, `NativeTemplates`, `ClassTemplate`,
  `TypeComposition`, handle, and pool identity only when enabled.
- Constant-pool ownership logs must not register constants, force lazy cells,
  or call dumps by accident.
- Startup-race diagnostics must be low overhead when disabled because template
  startup and same-JVM stress runs are timing-sensitive.
- Reentrancy logs must not add new `ThreadLocal`/MDC state that can leak across
  services, IO callbacks, or pooled Java threads.
- Logging must not become a hidden owner mechanism. Loggers are allowed to be
  static final process-global objects because they carry no XVM owner state.
  Logged context must be derived from explicit owners already available at the
  call site.

## Logger Categories

Use class loggers for ordinary local diagnostics. Use named cross-cutting
loggers when a decision spans many classes and needs independent enablement.

Recommended category names:

| Category | Purpose |
| --- | --- |
| `org.xvm.compiler.phase` | Compiler phase retries, deferred work, last-attempt behavior. |
| `org.xvm.compiler.resolve` | Name/type resolution decisions that are compiler-specific. |
| `org.xvm.asm.type` | `TypeConstant.calculateRelation`, `isA`, generic resolution, method selection, duck typing. |
| `org.xvm.asm.pool` | Constant registration, adoption, late-registration diagnostics, ambient-pool boundaries. |
| `org.xvm.runtime.owner` | Owner checks, handle/composition/template owner mismatches, validation failures. |
| `org.xvm.runtime.startup` | Container/native-template startup, template registration, startup ordering. |
| `org.xvm.runtime.reentry` | Recursion and reentrancy guards, in-progress markers, service/fiber reentry. |
| `org.xvm.runtime.service` | Service scheduling/execution failures and async boundary failures. |
| `org.xvm.javajit` | JIT connector/type-system diagnostics. |

Do not encode high-cardinality details in logger names. For example, do not
create a logger per module, type, method, pool, or container. Put those values
in guarded message arguments or key-values.

## Concrete API Patterns

### Basic Guarded Placeholder Logging

Use this as the default in hot Java runtime/compiler code:

```java
private static final Logger POOL_LOG = LoggerFactory.getLogger("org.xvm.asm.pool");

private Constant register(Constant constant) {
    // existing registration logic

    if (POOL_LOG.isTraceEnabled()) {
        POOL_LOG.trace("constant.register new={} pool={} position={}",
                describeConstantForDiagnostics(constant),
                poolId(this),
                constant.getPosition());
    }

    return constant;
}
```

`describeConstantForDiagnostics(...)` and `poolId(...)` must only be called
inside the guard. They may call `toString()`, `getDescription()`, or
`System.identityHashCode(...)`, and those costs are acceptable only when the log
is enabled.

### Helper Methods For Dense Hot Paths

For paths such as `TypeConstant.calculateRelation(...)`, put the guard at the
top of a small helper. The caller can pass already-available objects and enum
values without allocation:

```java
final class TypeDecisionLog {
    private static final Logger LOG = LoggerFactory.getLogger("org.xvm.asm.type");

    private TypeDecisionLog() {
    }

    static boolean isTraceEnabled() {
        return LOG.isTraceEnabled();
    }

    static void relationResult(TypeConstant right,
                               TypeConstant left,
                               Relation relation,
                               String reason) {
        if (!LOG.isTraceEnabled()) {
            return;
        }

        LOG.trace("type.relation result={} reason={} right={} left={} rightPool={} leftPool={}",
                relation,
                reason,
                typeName(right),
                typeName(left),
                poolId(right.getConstantPool()),
                poolId(left.getConstantPool()));
    }

    private static String typeName(TypeConstant type) {
        return type == null ? "null" : type.getValueString();
    }

    private static String poolId(ConstantPool pool) {
        return pool == null
                ? "null"
                : Integer.toHexString(System.identityHashCode(pool)) + ':' + pool.getDescription();
    }
}
```

Callers must still avoid computing the `reason` string dynamically before the
call. Use literals or enum values for reasons:

```java
TypeDecisionLog.relationResult(typeRight, typeLeft, Relation.INCOMPATIBLE,
        "recursive-duck-type");
```

If the reason itself is expensive, put the level check at the caller:

```java
if (TypeDecisionLog.isTraceEnabled()) {
    TypeDecisionLog.relationResult(typeRight, typeLeft, relation, buildReason());
}
```

### SLF4J 2 Fluent Logging

SLF4J 2 has a fluent event builder and key-value API. It is useful for future
structured providers, but still guard hot paths first:

```java
if (OWNER_LOG.isDebugEnabled()) {
    OWNER_LOG.atDebug()
            .setMessage("native.template.owner")
            .addKeyValue("template", templateName)
            .addKeyValue("container", containerId(container))
            .addKeyValue("pool", poolId(container.getConstantPool()))
            .log();
}
```

Do not rely on fluent logging alone for zero allocation. Expressions passed to
`addKeyValue(...)` are evaluated before the method call. Capturing suppliers can
also allocate. The guard is the contract.

### Expensive Dumps

`OwnershipDiagnostics.dump(...)` is intentionally expensive and may traverse
lazy owner state. Build it only on an explicit failure path or behind a level
guard:

```java
try {
    OwnershipDiagnostics.assertValid(containers);
} catch (IllegalStateException e) {
    OWNER_LOG.error("runtime ownership validation failed module={}", moduleName, e);

    if (OWNER_LOG.isDebugEnabled()) {
        OWNER_LOG.debug("ownership dump\n{}", OwnershipDiagnostics.dump(containers));
    }

    throw e;
}
```

For stress harnesses where the dump is the artifact, it is fine to emit it
unconditionally after failure. Do not put dump construction into a normal
runtime trace argument.

## Message Construction Rules

Follow these rules for every `trace` or `debug` call in runtime, ASM, compiler,
and JIT hot paths:

1. Guard with `isTraceEnabled()` or `isDebugEnabled()` before any expensive
   expression.
2. Do not concatenate strings in a disabled log expression.
3. Do not call `String.format`, `MessageFormat`, streams, `Arrays.toString`,
   dumps, `toString`, `getValueString`, or `getDescription` outside the guard.
4. Do not pass three or more arguments without a guard; SLF4J varargs create an
   `Object[]`.
5. Do not pass primitives without a guard; autoboxing allocates.
6. Do not use capturing lambdas or suppliers as a substitute for guards.
7. Do not populate MDC or other thread-local context merely because a method
   entered a hot path.
8. Prefer short reason codes and identity strings over whole-object dumps.
9. Keep helper formatting methods side-effect free. A logging helper must not
   register constants, force lazy template initialization, or resolve types.

Bad:

```java
LOG.trace("relation " + typeRight.getValueString() + " -> " + typeLeft.getValueString());
LOG.trace("pool={}", pool.getDescription());
LOG.trace("args={} depth={} count={}", args, depth, count);
LOG.atTrace().addKeyValue("dump", OwnershipDiagnostics.dump(container)).log("owner");
LOG.log(Level.FINER, () -> "relation " + typeRight.getValueString()); // JUL example
```

Good:

```java
if (LOG.isTraceEnabled()) {
    LOG.trace("relation right={} left={} depth={} count={}",
            typeRight.getValueString(),
            typeLeft.getValueString(),
            depth,
            count);
}
```

The good version can allocate when enabled. That is acceptable because the user
asked for that diagnostic channel. It allocates nothing when disabled.

## Structured Context

Use explicit event fields in the log message, not hidden global context, for the
first migration.

Recommended fields:

- `event`: stable event name when using fluent key-values.
- `reason`: short enum/string reason, such as `cache-hit`, `late-registration`,
  `recursive-duck-type`, `foreign-owner`.
- `module`: module name when already available.
- `method`: method/signature name when already available.
- `type`: type name only inside an enabled guard.
- `container`: short identity string for `Container`.
- `pool`: short identity string for `ConstantPool`.
- `template`: native template key or class.
- `thread`: Java thread name for startup or async boundaries.
- `depth`: recursion/reentrancy depth when already tracked.

Identity helpers should avoid retaining owner objects:

```java
static String objectId(Object value) {
    return value == null
            ? "null"
            : value.getClass().getSimpleName() + '@'
                    + Integer.toHexString(System.identityHashCode(value));
}
```

Call this only inside an enabled guard. Log records may contain strings and
primitive values derived from owners. They should not store `Container`,
`Frame`, `ServiceContext`, `ConstantPool`, or `ObjectHandle` in any static
context.

## MDC, ThreadLocal, And ScopedValue

Do not use MDC as the default runtime/compiler context mechanism.

MDC is ThreadLocal-based in common SLF4J providers. It has several problems for
XVM runtime diagnostics:

- populating MDC allocates and mutates context even if the relevant logger is
  disabled;
- service execution, IO callbacks, `CompletableFuture`, and request handlers can
  move work across Java threads;
- MDC values can leak across pooled threads if cleanup is missed;
- it hides the owner dependency in exactly the way the reentrancy work is trying
  to remove;
- it is not a substitute for explicit `Frame`, `Container`, `ServiceContext`,
  or `ConstantPool` parameters.

`ScopedValue` is a better fit than raw `ThreadLocal` for bounded ambient
execution context, and existing reentrancy notes already treat it as a
transitional bridge. It is still not the first logging tool:

- a scoped logging context should contain immutable ids or a small immutable
  diagnostic record, not mutable owner-bearing runtime objects;
- it should be bound at coarse operation boundaries, not per hot method;
- it does not automatically solve unstructured async propagation;
- it must never become a new cache owner.

If a future diagnostic mode needs correlation across a run, prefer an explicit
diagnostic token on the existing runtime/service/request owner:

```java
record DiagnosticIds(String runId, String module, String containerId) {}
```

Create it only when a diagnostic feature is enabled, and pass it through the
same owner graph that already controls execution.

## Diagnostic Needs By Area

### Type Decisions

Useful events:

- relation cache hit/miss,
- result relation and reason,
- auto-narrowing context,
- generic resolution fallback,
- duck-typing path chosen or rejected,
- recursion guard rejection,
- invalidation count observed.

Recommended logger: `org.xvm.asm.type`.

Rules:

- log `TypeConstant.getValueString()` only inside a trace guard;
- log pool identity for both sides when investigating adoption or cross-pool
  type movement;
- do not log every relation at `debug`; this is a `trace` channel;
- prefer reason codes over long English messages for high-volume events.

Example replacement for the current soft recursion print shape:

```java
if (!typeLeft.isInterfaceType()
        && !typeLeft.containsRecursiveType()
        && !typeRight.containsRecursiveType()) {
    String recursion = "left=" + typeLeft.getValueString()
            + "; right=" + typeRight.getValueString();
    if (s_setRecursions.add(recursion) && TYPE_LOG.isTraceEnabled()) {
        TYPE_LOG.trace("type.relation rejected reason=recursion {}", recursion);
    }
}
```

The string is already constructed here because it is the de-duplication key. If
that de-duplication changes later, build the string after the level guard.

### Owner Assignment

Useful events:

- template resolved through `NativeTemplates`,
- fallback template construction in `Container.getTemplate(...)`,
- canonical versus derived native role,
- composition/handle owner check failures,
- owner mismatch recovery or fail-fast boundary.

Recommended logger: `org.xvm.runtime.owner`.

Example:

```java
if (OWNER_LOG.isDebugEnabled()) {
    OWNER_LOG.debug("template.resolve key={} container={} pool={} template={}",
            key,
            containerId(container),
            poolId(container.getConstantPool()),
            objectId(template));
}
```

Do not put this at `info`. Owner assignment is developer state, not user-facing
tool output.

### Container And Constant-Pool Ownership

Useful events:

- `ConstantPool.withPool(...)` boundary entered/exited at runtime launch,
  service, request, or management invocation boundaries;
- explicit-owner assertion failure;
- constant adoption from source pool to destination pool;
- late registration after runtime publication when diagnostics are enabled;
- live `HandleConstant` attempted cross-pool movement.

Recommended logger: `org.xvm.asm.pool`.

Example:

```java
if (POOL_LOG.isTraceEnabled()) {
    POOL_LOG.trace("constant.adopt sourcePool={} targetPool={} constant={}",
            poolId(sourcePool),
            poolId(targetPool),
            describeConstantForDiagnostics(constant));
}
```

The late-registration guard should remain an explicit diagnostic invariant. A
log line is useful context, but it must not replace the exception when the
diagnostic property is enabled.

### Startup Races

Useful events:

- native-container creation boundary;
- native template load start/end;
- template table first creation;
- lazy owner table compute start/end;
- startup failure with owner/pool/thread ids.

Recommended logger: `org.xvm.runtime.startup`.

Example:

```java
if (STARTUP_LOG.isDebugEnabled()) {
    STARTUP_LOG.debug("native.startup.begin container={} module={} pool={} thread={}",
            containerId(container),
            moduleName,
            poolId(pool),
            Thread.currentThread().getName());
}
```

Startup logs change timing when enabled. That is acceptable for diagnosis, but
race fixes must still be proven by ownership assertions and stress tests with
logging disabled.

### Reentrancy Debugging

Useful events:

- recursion guard enter/exit,
- in-progress marker observed,
- deferred type-info list creation/take,
- service/fiber reentry boundary,
- same-JVM direct runtime validation window updates.

Recommended logger: `org.xvm.runtime.reentry`.

Rules:

- do not create new `ThreadLocal` state for logging;
- log only already-tracked depth/status values;
- when logging enter/exit, use `try/finally` only for correctness state that
  already exists, not just to balance logs;
- prefer one log on rejection/failure over paired logs around every hot call.

## Interaction With `Console`, `ErrorListener`, And `--verbose`

Do not route compiler errors, verification errors, or launcher messages through
SLF4J as their primary path.

Keep:

- `ErrorListener` for compiler/verifier diagnostics and abort behavior;
- `Launcher.log(...)` and `Console` for user-visible tool messages;
- Gradle `Logger` adaptation for direct launcher execution;
- `--verbose` for user-facing tool progress.

SLF4J developer logs are separate. A future command-line flag could add a
diagnostic provider or set provider properties, but it should not make
`--verbose` equivalent to `trace`. Verbose mode and trace logging have
different audiences, output volume, and compatibility promises.

## Migration Plan

1. Add an explicit core `slf4j-api` alias in `gradle/libs.versions.toml`.
2. Change `javatools` from compile-through-provider to an explicit
   `implementation(libs.slf4j.api)`, with `slf4j-nop` only on standalone
   launch/test runtime classpaths unless Gradle metadata proves it will not
   force a provider on embedders.
3. Add a small, SLF4J-free identity formatting helper if repeated owner id
   formatting becomes noisy. Keep it side-effect free and call it only inside
   guards.
4. Start with low-risk unconditional debug prints and duplicate stack traces:
   compiler catch blocks that already call `Launcher.log(...)`, type-system soft
   prints, `TypeInfoReal` conflict prints, runtime scheduling failure prints,
   and JIT TODO diagnostics.
5. Classify each existing print before migrating it:
   user-facing error, assertion/invariant, developer trace, test output, or
   temporary TODO.
6. Add cross-cutting category loggers only where class loggers are not enough.
   Start with `org.xvm.asm.type`, `org.xvm.asm.pool`, and
   `org.xvm.runtime.owner`.
7. Keep `OwnershipDiagnostics` dumps explicit. Replace only the print path, not
   the invariant behavior.
8. Add focused tests or profiling checks for hot helpers before touching
   `TypeConstant.calculateRelation(...)`, `ConstantPool.register(...)`, or
   runtime frame/service loops.
9. Document a diagnostic launch recipe for developers, such as adding a real
   SLF4J provider on the classpath and enabling one category at `trace`.
10. After the first migration wave, scan for unguarded `debug`/`trace`, string
    concatenation in log calls, and direct `System.err` runtime/compiler prints.

Suggested classification examples:

| Existing shape | Classification | Migration |
| --- | --- | --- |
| `Launcher.log(ERROR, ...)` | User/tool diagnostic | Keep. |
| `ErrorListener.log(ErrorInfo)` | Compiler diagnostic | Keep. |
| `System.err.println("rejecting isA()...")` | Developer type trace | Guarded `org.xvm.asm.type` trace. |
| `OwnershipDiagnostics.assertValid(...)` | Invariant | Keep; optionally log failure context. |
| `OwnershipDiagnostics.dump(...)` | Heavy diagnostic artifact | Build only on explicit failure or enabled debug. |
| `Constants.DEBUG` | Debug behavior flag | Do not expand; replace with explicit deterministic flags or logger categories. |
| Test `System.out.println(...)` | Test diagnostics | Leave unless it affects CI noise. |

## Risks

### Missed Guards

The biggest risk is adding SLF4J calls that look cheap but allocate when
disabled. The most common misses are string concatenation, primitive boxing,
varargs arrays, and helper method calls in argument position.

Mitigation: code review rule plus a source scan for `LOG.debug(`, `LOG.trace(`,
`.atDebug()`, `.atTrace()`, and string concatenation near log calls.

### Provider Conflicts

An embedding application, Gradle, test harness, or future launcher can provide a
different SLF4J provider. Multiple providers or an accidental logback dependency
in `javatools` could change output and startup behavior.

Mitigation: `javatools` source should depend on `slf4j-api`; standalone
launch/test classpaths should use the no-op provider by default; embedders and
diagnostic runs should opt into their own provider.

### Timing Perturbation

Trace logging can hide or expose races by changing scheduling and allocation
timing.

Mitigation: logs are for explanation, not proof. Race work still needs
ownership invariants, late-registration guards, and stress runs with logging
disabled.

### Sensitive Data

Runtime templates include crypto, network, HTTP, filesystem, and user values.
Logging raw values can expose secrets or large payloads.

Mitigation: log owner ids, types, module names, and reason codes by default.
Never log passwords, keys, request bodies, certificate private material, or
large binary/text values. Add redaction helpers before enabling logs in those
areas.

### Hidden Context

MDC, raw `ThreadLocal`, or logging-specific scoped state could recreate the
hidden-owner problem the reentrancy work is removing.

Mitigation: pass owner context explicitly. Use `ScopedValue` only as a bounded
bridge for immutable ids when there is a real operation-level need.

## What Not To Do

- Do not replace `ErrorListener` or `Console` with SLF4J.
- Do not make `--verbose` enable runtime/compiler `trace` logs.
- Do not add logback as a default `javatools` runtime dependency.
- Do not use JUL for new runtime/compiler diagnostic subsystems.
- Do not use MDC on hot runtime/compiler paths.
- Do not add new `ThreadLocal` state just to make logs prettier.
- Do not put `Container`, `Frame`, `ConstantPool`, handles, templates, or
  compositions into static logging context.
- Do not log by calling `OwnershipDiagnostics.dump(...)` in a disabled log
  argument.
- Do not rely on SLF4J supplier/fluent APIs as a substitute for explicit
  `isTraceEnabled()` or `isDebugEnabled()` guards.
- Do not use static mutable debug flags such as `DEBUG` to change normal
  runtime/compiler semantics.
- Do not catch and only log exceptions that should still fail an invariant,
  abort compilation, or propagate to the launcher.

## Bottom Line

SLF4J can be integrated with the XVM runtime and compiler in a way that is
effectively free when disabled, but only with a strict call-site discipline.
The API choice alone does not provide that property. The required design is:

- SLF4J API, with a no-op provider only on standalone default launch paths;
- explicit guards around all hot `debug`/`trace` logs;
- helper methods that begin with a level check;
- no MDC or hidden owner context on hot paths;
- separate user diagnostics from developer trace logs;
- keep expensive ownership dumps and invariant checks explicit.

That gives the runtime/compiler a practical diagnostic surface for type
decisions, owner assignment, constant-pool ownership, startup race hunting, and
reentrancy debugging without paying heap or formatting cost in normal runs.
