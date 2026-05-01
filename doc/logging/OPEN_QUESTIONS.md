# Open questions for `lib_logging`

This is the running list of unresolved design and implementation questions, kept
deliberately short so it stays readable as the project moves.

It is split into two sections:

- **Addressed by the runtime plan** — questions that *had* been open, but
  [`RUNTIME_IMPLEMENTATION_PLAN.md`](RUNTIME_IMPLEMENTATION_PLAN.md) now prescribes a
  concrete fix. Those are summarized in one line each, with a link.
- **Still genuinely open** — questions whose answer the plan does not commit to. These
  retain their full trade-off discussion, because someone will have to decide.

When an "addressed by the plan" item lands in code, delete its row.

---

## Addressed by the runtime implementation plan

| # | Question | Resolution |
|---|---|---|
| 1 | **Wildcard-name injection in `nMainInjector`** — `@Inject("any.name") Logger` doesn't resolve because the existing resource map is exact-match. | **Rejected.** Single fixed-name `("logger", loggerType)` supplier; per-name loggers come from `Logger.named(String)` instead. No special-case in the injector. See [Stage 1.4](RUNTIME_IMPLEMENTATION_PLAN.md#14--single-fixed-name-supplier-no-wildcard-injection). |
| 2 | **Default logger name when no `resourceName` supplied** — should `@Inject Logger logger;` (no name) get `"default"`, the enclosing module's name, or the class name? | The XTC compiler substitutes the enclosing module's qualified name. Optional for the demo. See [Stage 4](RUNTIME_IMPLEMENTATION_PLAN.md#stage-4--compiler-side-default-name-optional-but-high-impact). |
| 3 | **MDC scope: per-fiber, per-service, or per-call?** | Recommend per-fiber if the runtime gives us fiber-locals; per-service-instance otherwise as a known-incorrect-for-concurrency v0 fallback. Decision required *before* `RTMDC.java` is written. See [Stage 3.1](RUNTIME_IMPLEMENTATION_PLAN.md#31--decide-the-scope). |
| 5 | **Throwable promotion: where does it happen, and who wins when both are supplied?** | `MessageFormatter.format` does the promotion; explicit `cause=` always wins over a promoted-from-args throwable. See [Stage 2.1](RUNTIME_IMPLEMENTATION_PLAN.md#21--real-messageformatterformat). |
| 9 | **Where does the runtime live?** | `javatools_jitbridge/src/main/java/org/xtclang/_native/logging/`, registered from `nMainInjector.addNativeResources()`. See [Stage 1.1–1.5](RUNTIME_IMPLEMENTATION_PLAN.md#stage-1--native-side-make-inject-logger-resolve). |
| 10 | **Bootstrap: do we need a tiny native fallback for early-runtime logging before `Console` is registered?** | Yes — `RTConsoleLogSink.java` falls back to `System.err.println` if `Console` is not yet available. See [Stage 1.2](RUNTIME_IMPLEMENTATION_PLAN.md#12--rtconsolelogsinkjava). |

---

## Still genuinely open

These need a decision before the API is locked in. Numbered to preserve traceability
to earlier discussion.

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
`service` according to the rule documented in `DESIGN.md` ("Sink type: `const` vs
`service`"):

- stateless forwarders / pure adapters → `const`
- stateful collectors, resource owners, async workers → `service`

`BasicLogger` is a `const`, so every concrete sink must be `Passable` (either
`immutable` or a `service`). Forcing `LogSink extends Service` in the interface
signature was rejected because it would prohibit the legitimate stateless cases
(`NoopLogSink`, `ConsoleLogSink`) and contradicts the `class ConsoleAppender` /
`class ConsoleLog` pattern already used in `lib_ecstasy/src/main/x/ecstasy/io/`.

This rule is checked for parity against patterns in `platform/common`,
`platform/host`, and `lib_xunit_engine` — see DESIGN.md for the citations.

### 7. Async / batched sinks

Where does the async-wrapper sink live?

**Options:**

- **A. Ship `AsyncLogSink` in `lib_logging`.** Caller wraps a slow sink: `new
  AsyncLogSink(new ConsoleLogSink())`. Worker fiber drains a bounded queue.
- **B. Defer to `lib_logging_logback`.** Keeps the base lib minimal.

**Recommendation:** B. The configurable backend is the natural home for async; basics
ship in v0 unchanged.

### 8. Per-container override convenience — *resolved*

**Resolution.** Adopted option A — document the pattern, add no API. A host that
wants a nested container to use a different sink configures the child container's
injector explicitly to resolve the `("logger", Logger)` key against the alternate
sink. Pattern documented in `DESIGN.md` § "Per-container sink override". No
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

We landed on a rule (see DESIGN.md, "Sink type") that lets `LogSink` accept both shapes,
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

### Q-D6. SLF4J vs slog as the API shape — design preference

We're maintaining two parallel libraries (`lib_logging` modeled after SLF4J 2.x and
`lib_slogging` modeled after Go's `slog`) so the design tradeoffs can be evaluated
side-by-side. See `LIB_LOGGING_VS_LIB_SLOGGING.md` for the comparison and the explicit
list of things we want reviewer feedback on.

**Question:** which API style is a better long-term fit for Ecstasy idioms (services,
consts, `SharedContext`, fluent builders), and is there appetite for shipping both,
shipping one, or shipping a third synthesis?

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

## SLF4J-style library (`lib_logging`) — work not yet implemented

Tracking list so the SLF4J-shaped library stays comparable to `lib_slogging` in
feature scope. Each item is a concrete deliverable with a one-line summary.

| # | Item | Notes |
|---|---|---|
| W-1 | Multiple markers per event | `LogEvent.marker: Marker?` → `LogEvent.markers: Marker[]`; accumulate in `BasicEventBuilder`; render `[markers=A,B]` in `ConsoleLogSink`. Tracked as task #2; see open question 4 above. |
| W-2 | Logger interning in `Logger.named` | **Done.** Implemented `service LoggerRegistry(LogSink sink)` keyed by logger name; `BasicLogger.registry: LoggerRegistry?` (optional, defaulted `Null` so unattached loggers stay allocation-light); `LoggerFactory` lazily constructs a registry over its `defaultSink` and routes both `getLogger(...)` and `named(...)` through it. Tests: `NamedLoggerTest.shouldInternChildrenWhenRegistryAttached`, `NamedLoggerTest.shouldNotInternWhenRegistryAbsent`. |
| W-3 | Real `MessageFormatter` | **Done.** Full SLF4J ParameterFormatter parity: `{}` substitution, `\{}` literal escape, `\\{}` double-escape, trailing-throwable promotion (with explicit-cause precedence enforced in `BasicLogger.emit`), defensive `safeToString`. 12 tests in `MessageFormatterTest`. The "stub" claim in earlier docs was stale. |
| W-4 | Compiler-side default name from module | `@Inject Logger logger;` (no resourceName) should fall back to the enclosing module's qualified name; see plan Stage 4. |
| W-5 | Async / batched sink (`AsyncLogSink`) | Bounded queue + worker fiber; lives in `lib_logging_logback` not in the base lib. Open question 7. |
| W-6 | Logback-shaped backend (`lib_logging_logback`) | Configuration-driven sink with per-logger thresholds, multiple appenders, layouts, filters. Sketched in `LOGBACK_INTEGRATION.md`. |
| W-7 | Native bridge (`RTLogbackSink.java`) | Optional escape hatch wrapping real Logback via `javatools_jitbridge`. Sketched in `NATIVE_BRIDGE.md`. |
| W-8 | Per-container override convenience | Helper for child containers wanting a different sink. Open question 8. |
| W-9 | Defensive copy of caller-supplied `Object[] arguments` | Open question 11. Decision: B (document, no copy) for v0. Listed here so it's not forgotten if v0 changes. |
| W-10 | Compiler/tooling lints for log statements | Out of scope for the library; would be an XTC linter feature. Open question 12. |
| W-11 | Doc cleanup | `DESIGN.md` "What isn't here yet" still lists items that have since landed (RTLogger removed, MessageFormatter partial, tests now exist); `RUNTIME_IMPLEMENTATION_PLAN.md` and `INJECTED_LOGGER_EXAMPLE.md` may reference the now-removed `xRTLogger`/`RTLogger.x`. |

The same tracking shape will live for `lib_slogging` in
`LIB_LOGGING_VS_LIB_SLOGGING.md` so reviewers can see the two libraries reach feature
parity at the same waterline before we ask "which API do you prefer?"

### Implementation tiers

The W-items above are grouped into three tiers so we can decide how far to push
`lib_logging` before reviewer feedback determines which library survives.

| Tier | Scope | Items | Approx. cost |
|---|---|---|---|
| **1** | Close visible loose ends. No new design choices. | W-1 (multi-marker), W-11 (doc cleanup) | ~1 hour |
| **2** | Make `lib_logging` fully comparable as a shippable library. | Tier 1 + W-2 (logger interning, needs a `LoggerRegistry` service since `BasicLogger` is `const`) + W-3 (real `MessageFormatter` with `{}` substitution + trailing-throwable promotion) | ~half a day |
| **3** | Out of scope for this PR — separate change(s). | W-4 (compiler default-name), W-5 (`AsyncLogSink`), W-6 (`lib_logging_logback`), W-7 (native bridge), W-8 (per-container override), W-9 (defensive copy), W-10 (linter) | per-item; multiple PRs |

**Decision (revised):** Tier 1 *and* Tier 2 land in this PR for both libraries.
Reviewers asked for two **comparable** libraries — i.e. parity in implementation
maturity, not just in design intent — so they can give a feedback judgment without
discounting one side as "skeleton vs production." Tier 3 items remain out of scope
(separate PRs).

The `lib_slogging` parity translation:

| `lib_logging` (W) | `lib_slogging` analogue |
|---|---|
| W-1 multi-marker on event | n/a — slog uses repeated attrs, already supported by `Attr[]` |
| W-2 logger interning in `Logger.named` | n/a — slog has no hierarchical names; ergonomic `Logger.with(...)` already returns derived loggers |
| W-3 real `MessageFormatter` with `{}` substitution | n/a — slog has no message templating; `TextHandler` / `JSONHandler` formatting completeness instead |
| W-11 doc cleanup | mirrored: keep `lib_slogging` doc references in sync |
| (test suite parity) | mirror `LoggingTest` → `SLoggingTest`: per-level routing, fluent-equivalent (`logger.with`), level thresholds, exception carriage, structured attrs, group nesting |

## Decisions required to land in this order

1. **MDC scope (#3)** — must be made before Stage 1.3 of the plan. Without an answer
   the `RTMDC.java` impl can't be written.
2. **Multiple markers per event (#4)** — should be made *before or with* Stage 1, so
   `LogEvent` and `BasicEventBuilder` shapes are stable when the runtime starts
   resolving them.
3. **Defensive copy (#11)** — make alongside Stage 1.1; the `RTLogger` Java side
   needs to know whether to copy or not.

The remaining still-open items (#6, #7, #8, #12) can wait until there are real users.
