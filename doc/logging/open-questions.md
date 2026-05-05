# Open questions for `lib_logging`

This is the running list of design decisions and remaining implementation questions,
kept deliberately short so it stays readable as the project moves.

It is split into resolved design notes, implementation trackers, and the few items
that still need compiler/tooling/backend follow-up. Historical plan links are kept
where they explain why the branch made a decision.

---

## Runtime decisions

| # | Question | Resolution |
|---|---|---|
| 1 | **Wildcard-name injection in `nMainInjector`** — `@Inject("any.name") Logger` doesn't resolve because the existing resource map is exact-match. | **Rejected.** Single fixed-name `("logger", loggerType)` supplier; per-name loggers come from `Logger.named(String)` instead. No special-case in the injector. See [Stage 1.4](future/runtime-implementation-plan.md#14--single-fixed-name-supplier-no-wildcard-injection). |
| 2 | **Default logger name when no `resourceName` supplied** — should `@Inject Logger logger;` (no name) get `"default"`, the enclosing module's name, or the class name? | Runtime fallback implemented for canonical `logging.Logger`: if the injected field name is `"logger"`, `NativeContainer` derives the caller namespace. Exact compiler-synthesized module/class names remain future polish. See [Stage 4](future/runtime-implementation-plan.md#stage-4--compiler-side-default-name-optional-but-high-impact). |
| 3 | **MDC scope: per-fiber, per-service, or per-call?** | Per-fiber semantics implemented in Ecstasy with `SharedContext<immutable Map>`. The logger is injected as a `BasicLogger` const so calls stay on the caller fiber and can see the current MDC. |
| 5 | **Throwable promotion: where does it happen, and who wins when both are supplied?** | `MessageFormatter.format` does the promotion; explicit `cause=` always wins over a promoted-from-args throwable. See [Stage 2.1](future/runtime-implementation-plan.md#21--real-messageformatterformat). |
| 9 | **Where does the runtime live?** | Interpreter-side wiring lives in `javatools/src/main/java/org/xvm/runtime/NativeContainer.java`. The earlier native-resource class plan is superseded for this branch. |
| 10 | **Bootstrap: do we need a tiny native fallback for early-runtime logging before `Console` is registered?** | Not for this branch. The default `ConsoleLogSink` writes through `@Inject Console`; a bootstrap-native fallback can be added later only if runtime startup logging needs it. |

---

## Design notes and remaining follow-up

Numbered to preserve traceability to earlier discussion.

### 4. Multiple markers per event — *resolved*

**Resolution.** Implemented option A. `LogEvent.markers: Marker[]` is now the canonical
field (see `LogEvent.x`); a `LogEvent.marker: Marker?` convenience accessor returns
`markers[0]` for sinks that only surface a single category line. `BasicEventBuilder`
accumulates per `addMarker(...)` call and freezes the array before crossing the sink
boundary. Per-level methods (`info`/`warn`/...) still take a single optional `Marker`
and wrap it; multi-marker is reachable through the fluent builder. SPI-level
`LogSink.isEnabled(...)` keeps its single-marker signature (matches SLF4J 1.x's
`Logger.isEnabledFor(Marker)`); the "primary" marker is `markers[0]`. Tests:
`MarkerTest.shouldPropagateMultipleMarkersThroughFluentBuilder`,
`MarkerTest.shouldEmitNoMarkersWhenNoneAttached`.

### 6. `const` vs `service` for sinks — *resolved*

**Resolution.** `LogSink` is an interface; implementations choose between `const` and
`service` according to the rule documented in `design/design.md` ("Sink type: `const` vs
`service`"):

- stateless forwarders / pure adapters → `const`
- stateful collectors, resource owners, async workers → `service`

`BasicLogger` is a `const`, so every concrete sink must be `Passable` (either
`immutable` or a `service`). Forcing `LogSink extends Service` in the interface
signature was rejected because it would prohibit the legitimate stateless cases
(`NoopLogSink`, `ConsoleLogSink`) and contradicts the `class ConsoleAppender` /
`class ConsoleLog` pattern already used in `lib_ecstasy/src/main/x/ecstasy/io/`.

This rule is checked for parity against patterns in `platform/common`,
`platform/host`, and `lib_xunit_engine` — see design/design.md for the citations.

### 7. Async / batched sinks — *resolved*

**Resolution.** Adopted option A. `AsyncLogSink` ships in `lib_logging` as a small
bounded-queue wrapper around any `LogSink`; `AsyncHandler` mirrors the same shape for
`lib_slogging`. Slow output can now be isolated without waiting for a full
configuration backend. A future Logback-style module can still choose to compose or
replace the wrapper for richer batching policies.

### 8. Per-container override convenience — *resolved*

**Resolution.** Adopted option A — document the pattern, add no API. A host that
wants a nested container to use a different sink configures the child container's
injector explicitly to resolve the `("logger", Logger)` key against the alternate
sink. Pattern documented in `design/design.md` § "Per-container sink override". No
`lib_logging`-side helper, deliberately: per-container resource overrides are the
host runtime / injector library's job, not the logging library's.

### 11. Defensive copy of caller-supplied `Object[] arguments` — *resolved*

**Resolution.** Adopted option B for v0: callers contractually must not mutate the
`arguments` array between the return of `info(...)` and any sink consuming the
resulting `LogEvent`. Matches SLF4J's posture; documented as a one-paragraph policy
comment in `BasicLogger.emitWith` (the only entry point that receives caller-owned
args). `BasicEventBuilder` is already safe by construction because it freezes its
internally-accumulated `args` to `Constant`-mode before crossing the sink boundary.
Reconsider if/when async sinks become a default — at that point the cost of the
defensive copy is justified by the larger window during which the caller can
mutate.

### 12. Compiler/tooling support for log statements

SLF4J has linters that flag `info("count: " + n)` (eagerly formatted instead of using
`{}`) and missing exception arguments. Should Ecstasy?

**Recommendation:** Out of scope for `lib_logging`. If we want it, it's an XTC linter
feature, not a library feature.

---

## Questions for the XTC language / runtime designers

These are points where we made a call but want explicit confirmation from the people
who own the language semantics, before locking the API.

### Q-D1. Is "`const` if stateless / `service` if stateful with shared mutable state" the right pattern for an Ecstasy SPI boundary?

We landed on a rule (see design/design.md, "Sink type") that lets `LogSink` accept both shapes,
mirroring the way `lib_ecstasy/src/main/x/ecstasy/io/ConsoleAppender.x` is a `class`
while `service ErrorLog` and `service ConsoleExecutionListener` exist elsewhere. The
alternative is forcing every implementation to be one shape (e.g. `LogSink extends
Service`) and accepting the loss of `const` stateless sinks.

**Question:** is permitting both shapes idiomatic for an XDK SPI, or does the language
team prefer SPI interfaces that constrain to one shape for clarity?

### Q-D2. `@Inject` inside a `const` — is this fully supported?

`BasicLogger` is a `const` and declares `@Inject Clock clock; @Inject MDC mdc;`.
`ConsoleLogSink` is a `const` and declares `@Inject Console console;`. This works in
practice today, but the docs are thin on whether `@Inject` resolution semantics are
identical inside a `const` versus a `service` (timing, fiber affinity, caching).

**Question:** is `@Inject` inside a `const` first-class, or is there a subtlety we
should know about (e.g. resolved at construction vs at first access; affinity to the
constructing fiber vs the calling fiber; freezing behaviour after construction)?

### Q-D3. Calling a service from a `const` field — boundary semantics

`const BasicLogger` holds a `LogSink sink` field. When that sink is a `service`, every
`sink.log(event)` is an inter-service hop with implicit freeze of the arguments. We
adopted "freeze the marker before the first sink hop" as a workaround
(`BasicLogger.emitWith`) because the diagnostic wording ("Property not freezable" /
"mutable object cannot be used as an argument to a service call") is hard to map back
to "the value crossed an implicit service boundary."

**Question:** is there a more idiomatic way to express "this `const` adapts a service
under the hood, and the service boundary cost is paid at this method call"? Should
the compiler give us a clearer diagnostic when a value would need to be `Passable` to
cross such a boundary?

### Q-D4. Per-fiber `MDC` via `SharedContext` — are we using it as intended?

`MDC` is implemented as `const MDC` storing `immutable Map<String, String>` in a
single static `ecstasy.SharedContext`, with copy-on-write derivation on every mutation
(`put / remove / clear`). This relies on (a) `const` methods running on the caller's
fiber so `mapContext.hasValue()` resolves against that fiber's tokens; and (b) the
runtime's clone-on-write fiber-token map for sync calls so child fibers see parent
state without being able to mutate it back.

**Question:** does this match the intended use of `SharedContext` for fiber-local
context propagation, or should we be using a different primitive (a built-in
`MDC`-equivalent, a service-injected ledger, fiber-local slots) for SLF4J-grade MDC
semantics?

### Q-D5. Removing the `RTLogger.java` wrapper — is there a class of "injection-only"
runtime resources where the right answer is "construct the const directly, don't wrap"?

The original POC went through `xRTLogger.INSTANCE` (a service template wrapping a
`Logger`). That wrapper crossed a fiber boundary and severed the caller's MDC tokens.
Removing the wrapper and registering `BasicLogger` directly as the resource (see
`NativeContainer.ensureLogger`) fixed the problem and simplified the runtime side.

**Question:** is this generally the right pattern when (a) the injected type is a
`const`, (b) it has no per-call native state, and (c) we want fiber-local context
visibility to survive injection? If so, should `nMainInjector` grow a
`registerConstResource(...)` helper to make this the documented happy path?

### Q-D6. SLF4J vs slog as the API shape — *resolved for this branch*

We're maintaining two parallel libraries (`lib_logging` modeled after SLF4J 2.x and
`lib_slogging` modeled after Go's `slog`) so the design tradeoffs can be evaluated
side-by-side. See `lib-logging-vs-lib-slogging.md` for the comparison and the explicit
list of things we want reviewer feedback on.

**Resolution.** Recommend `lib_logging` as the canonical XDK facade. The deciding
factors are ecosystem familiarity, hierarchical logger routing, markers/MDC/fluent
builder parity for SLF4J users, and the now-working `LogSink` backend path. Keep
`lib_slogging` as comparison material and possible adapter surface, not as a second
permanent default facade.

### Q-D7. Cross-module default-argument resolution on `const` constructors

Calling `new ConsoleLogSink()` from the `TestLogger` module fails with
`Unresolvable function "void construct()"` even though `ConsoleLogSink` was
declared as `const ConsoleLogSink(Level rootLevel = Info)`. Same shape failed
for `new BasicLogger(name, sink)` against
`const BasicLogger(String name, LogSink sink, LoggerRegistry? registry = Null)`.
Adding explicit no-arg / shorter-arg `construct(...)` forms that delegate to the
primary constructor fixes it; same-module callers (the convenience constructor
inside `BasicLogger.x` itself, the unit tests inside `LoggingTest`) work fine
without the explicit forms.

We worked around it by writing every "shorter form" explicitly — see
`BasicLogger.x`, `ConsoleLogSink.x`, `Logger.x`, `TextHandler.x`, `JSONHandler.x`.
Each has a `Why:` comment naming the workaround.

**Question:** is the synthesis of "callable" forms from default-argument
constructors only available within the declaring module, or is this a bug? If
intended, the `construct(Level rootLevel = Info)` syntax is misleading because
it reads as "this defaults to Info" but practically requires explicit
delegating constructors for cross-module ergonomics. If a bug, fixing it would
remove the explicit-delegation clutter from every const that wants a no-arg
form.

---

## SLF4J-style library (`lib_logging`) — implementation tracker

Tracking list so the SLF4J-shaped library stays comparable to `lib_slogging` in
feature scope. Each item is a concrete deliverable with a one-line summary.

| # | Item | Notes |
|---|---|---|
| W-1 | Multiple markers per event | **Done.** `LogEvent.markers: Marker[]`, `marker` convenience accessor, `BasicEventBuilder.addMarker(...)` accumulation, and `ConsoleLogSink` multi-marker rendering are implemented and tested. |
| W-2 | Logger interning in `Logger.named` | **Done.** Implemented `service LoggerRegistry(LogSink sink)` keyed by logger name; `BasicLogger.registry: LoggerRegistry?` (optional, defaulted `Null` so unattached loggers stay allocation-light); `LoggerFactory` lazily constructs a registry over its `defaultSink` and routes both `getLogger(...)` and `named(...)` through it. Tests: `NamedLoggerTest.shouldInternChildrenWhenRegistryAttached`, `NamedLoggerTest.shouldNotInternWhenRegistryAbsent`. |
| W-3 | Real `MessageFormatter` | **Done.** Full SLF4J ParameterFormatter parity: `{}` substitution, `\{}` literal escape, `\\{}` double-escape, trailing-throwable promotion (with explicit-cause precedence enforced in `BasicLogger.emit`), defensive `safeToString`. 12 tests in `MessageFormatterTest`. The "stub" claim in earlier docs was stale. |
| W-4 | Default injected logger naming | **Partially done.** `NativeContainer` now falls back from the field name `"logger"` to the caller namespace for canonical `logging.Logger` injections. Exact module/class names still need compiler lowering if we want deterministic names outside the runtime fallback. |
| W-5 | Async / batched sink (`AsyncLogSink`) | **Done.** Bounded queue + worker-fiber drain wrapper ships in the base library and is tested. |
| W-6 | Logback-shaped backend primitives | **Done at the base-programmatic level.** `CompositeLogSink`, `HierarchicalLogSink`, `JsonLogSink`, and `JsonLogSinkOptions` cover multi-appender fanout, per-logger threshold routing, JSON rendering, and redaction/field-name knobs. External config-file loading, rolling files, hot reload, network sinks, and filters remain follow-up backend modules. |
| W-7 | Native bridge (`RTLogbackSink.java`) | **Decision documented; not shipped.** The bridge remains an optional escape hatch in `future/native-bridge.md`. The canonical XDK path should stay pure Ecstasy first so users do not inherit Java Logback configuration/bootstrap behavior by default. |
| W-8 | Per-container override convenience | **Resolved: no library helper.** Child containers should override the injectable resource through the host injector API. |
| W-9 | Defensive copy of caller-supplied `Object[] arguments` | **Resolved: document, no copy.** Listed here so it can be revisited if async sinks become default-on. |
| W-10 | Compiler/tooling lints for log statements | Out of scope for the library; would be an XTC linter feature. Open question 12. |
| W-11 | Doc cleanup | **Done for active docs.** Start-here docs, parity docs, examples, runtime-injection notes, and source comments now describe the current `BasicLogger`/`MDC`/multi-marker/formatter shape. Historical future-plan docs remain as historical context. |

The `lib_slogging` section mirrors the same tracking shape so the API decision is
made between two implementations at the same proof level.

### Implementation tiers

The W-items above are grouped into three tiers to show what landed in this branch
and what remains deliberate follow-up work.

| Tier | Scope | Items | Approx. cost |
|---|---|---|---|
| **1** | Close visible loose ends. No new design choices. | W-1 (multi-marker), W-11 (doc cleanup) | ~1 hour |
| **2** | Make `lib_logging` fully comparable as a shippable library. | Tier 1 + W-2 (logger interning, needs a `LoggerRegistry` service since `BasicLogger` is `const`) + W-3 (real `MessageFormatter` with `{}` substitution + trailing-throwable promotion) | ~half a day |
| **3** | Backend/runtime polish. | W-4 runtime fallback naming, W-5 async wrapper, W-6 base Logback-style primitives, W-7 native-bridge decision, W-8 per-container override decision, W-9 defensive-copy decision, W-10 linter deferral | mostly landed; W-4 compiler lowering and W-10 linting remain separate work |

**Decision (revised):** Tier 1, Tier 2, and the backend-primitives part of Tier 3
land in this PR for both libraries. The only remaining Tier 3 implementation work is
outside the base libraries: compiler-synthesized call-site metadata, linter support,
full config-file loading/destinations, and any optional Java bridge.

The `lib_slogging` parity translation:

| `lib_logging` (W) | `lib_slogging` analogue |
|---|---|
| W-1 multi-marker on event | n/a — slog uses repeated attrs, already supported by `Attr[]` |
| W-2 logger interning in `Logger.named` | n/a — slog has no hierarchical names; ergonomic `Logger.with(...)` already returns derived loggers |
| W-3 real `MessageFormatter` with `{}` substitution | n/a — slog has no message templating; `TextHandler` / `JSONHandler` formatting completeness instead |
| W-5 async sink | `AsyncHandler` |
| W-6 backend options / JSON / redaction | `HandlerOptions`, `JSONHandler`, `TextHandler` |
| W-11 doc cleanup | mirrored: keep `lib_slogging` doc references in sync |
| (test suite parity) | mirror `LoggingTest` → `SLoggingTest`: per-level routing, fluent-equivalent (`logger.with`), level thresholds, exception carriage, structured attrs, group nesting, async wrapper, JSON/redaction |

## slog-style library (`lib_slogging`) — parity work addressed

`lib_slogging` is now a working comparison POC at the same proof level as the
SLF4J-shaped library for the base API. The concrete parity gaps called out during
review have been closed as follows:

| # | Item | Notes |
|---|---|---|
| S-1 | Runtime injection for `@Inject slogging.Logger logger;` | **Done.** `NativeContainer` now resolves resources by `(name, requested type)`, so `logging.Logger logger` and `slogging.Logger logger` can both use the default name. `manualTests/TestLogger.x` exercises both. |
| S-2 | Production JSON handler | **Done for the base POC.** `JSONHandler` renders through `lib_json`, escapes strings through `Printer`, preserves nested groups, emits source metadata, renders exceptions structurally, and honors `HandlerOptions` redaction/field-name/source knobs. Output destinations beyond `Console` remain a future handler family. |
| S-3 | Source-location capture | **Done via explicit API.** `Logger.logAt(level, message, sourceFile, sourceLine, attrs, cause)` populates `Record.sourceFile` / `sourceLine`. Automatic compiler/runtime call-site capture remains a future enhancement. |
| S-4 | Context story | **Done.** `LoggerContext` wraps `SharedContext<Logger>` for framework/request propagation. Explicit logger passing remains the recommended default. |
| S-5 | Handler pre-resolution example | **Done.** `BoundHandler` implements the default `withAttrs` / `withGroup` semantics and shipped handlers use it; production backends can replace it with cached prefix implementations. |
| S-6 | Handler test kit | **Done.** `SLoggingTest.HandlerContract` provides a small `testing/slogtest`-style helper for third-party handler conformance. |

## Decision ordering

| Decision | Why it comes first |
|---|---|
| MDC scope (#3) | Runtime wiring needs a settled per-fiber propagation model. |
| Multiple markers per event (#4) | `LogEvent` and `BasicEventBuilder` should be stable before more sinks depend on them. |
| Defensive copy (#11) | The current decision is "document, no copy"; revisit if async sinks become default-on. |

The remaining genuinely open items are compiler/tooling work, destination-specific
production backends, and any optional Java bridge.


---

_See also [README.md](README.md) for the full doc index and reading paths._
