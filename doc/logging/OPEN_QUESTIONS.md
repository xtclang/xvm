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
| 1 | **Wildcard-name injection in `nMainInjector`** — `@Inject("any.name") Logger` doesn't resolve because the existing resource map is exact-match. | `nMainInjector.supplierOf` falls back to a wildcard `(loggerType, "*")` entry. See [Stage 1.4](RUNTIME_IMPLEMENTATION_PLAN.md#14--wildcard-name-resolution-in-nmaininjector). |
| 2 | **Default logger name when no `resourceName` supplied** — should `@Inject Logger logger;` (no name) get `"default"`, the enclosing module's name, or the class name? | The XTC compiler substitutes the enclosing module's qualified name. Optional for the demo. See [Stage 4](RUNTIME_IMPLEMENTATION_PLAN.md#stage-4--compiler-side-default-name-optional-but-high-impact). |
| 3 | **MDC scope: per-fiber, per-service, or per-call?** | Recommend per-fiber if the runtime gives us fiber-locals; per-service-instance otherwise as a known-incorrect-for-concurrency v0 fallback. Decision required *before* `RTMDC.java` is written. See [Stage 3.1](RUNTIME_IMPLEMENTATION_PLAN.md#31--decide-the-scope). |
| 5 | **Throwable promotion: where does it happen, and who wins when both are supplied?** | `MessageFormatter.format` does the promotion; explicit `cause=` always wins over a promoted-from-args throwable. See [Stage 2.1](RUNTIME_IMPLEMENTATION_PLAN.md#21--real-messageformatterformat). |
| 9 | **Where does the runtime live?** | `javatools_jitbridge/src/main/java/org/xtclang/_native/logging/`, registered from `nMainInjector.addNativeResources()`. See [Stage 1.1–1.5](RUNTIME_IMPLEMENTATION_PLAN.md#stage-1--native-side-make-inject-logger-resolve). |
| 10 | **Bootstrap: do we need a tiny native fallback for early-runtime logging before `Console` is registered?** | Yes — `RTConsoleLogSink.java` falls back to `System.err.println` if `Console` is not yet available. See [Stage 1.2](RUNTIME_IMPLEMENTATION_PLAN.md#12--rtconsolelogsinkjava). |

---

## Still genuinely open

These need a decision before the API is locked in. Numbered to preserve traceability
to earlier discussion.

### 4. Multiple markers per event

SLF4J 2.x's `LoggingEventBuilder.addMarker` accepts repeated calls and stores all of
them on the event. `BasicEventBuilder` currently keeps only the most recently added
one.

**Options:**

- **A. Match SLF4J — list of markers per event.** Requires `LogEvent.marker: Marker?`
  → `LogEvent.markers: Marker[]`. Affects every sink that reads markers.
- **B. Keep one marker, document the limitation.** Sinks that need multiple markers
  can use child references on a single marker.

**Recommendation:** A. Cheap to do now (a few lines, plus updating two test
assertions); breaking later is much more work.

### 6. Service vs class for sinks

Most `LogSink` implementations are services (mutable thread-safe state); some are not.

**Options:**

- **A. Recommend, don't require.** `LogSink` stays an interface; impls choose.
- **B. Require.** Force `LogSink extends Service` in the interface signature.

**Recommendation:** A. Forcing service-hood breaks `NoopLogSink` and complicates test
helpers; users who need state will reach for `service` naturally.

### 7. Async / batched sinks

Where does the async-wrapper sink live?

**Options:**

- **A. Ship `AsyncLogSink` in `lib_logging`.** Caller wraps a slow sink: `new
  AsyncLogSink(new ConsoleLogSink())`. Worker fiber drains a bounded queue.
- **B. Defer to `lib_logging_logback`.** Keeps the base lib minimal.

**Recommendation:** B. The configurable backend is the natural home for async; basics
ship in v0 unchanged.

### 8. Per-container override convenience

Ecstasy's container model gives each container its own injector. A host could in
principle want a different sink for one nested container.

**Options:**

- **A. Document the pattern.** "Configure the child container's injector explicitly."
  No API addition.
- **B. Provide a helper.** Something like `ContainerLoggingConfig.set(child, sink)`.

**Recommendation:** A. Wait for demand.

### 11. Defensive copy of caller-supplied `Object[] arguments`

`BasicEventBuilder` already converts its accumulated `args` to `Constant`-mode (frozen)
before crossing the sink boundary. The per-level methods (`info`, `debug`, …) accept
`Object[]` from the caller directly. If the caller mutates that array between the
return of `info(...)` and an async sink consuming the event, the sink may see the
mutation.

**Options:**

- **A. Defensive copy at the `BasicLogger.emit` boundary.** One allocation per
  emission.
- **B. Document that callers must not mutate the array.** Match SLF4J's posture.

**Recommendation:** B for v0. Reconsider if/when async sinks become common.

### 12. Compiler/tooling support for log statements

SLF4J has linters that flag `info("count: " + n)` (eagerly formatted instead of using
`{}`) and missing exception arguments. Should Ecstasy?

**Recommendation:** Out of scope for `lib_logging`. If we want it, it's an XTC linter
feature, not a library feature.

---

## Decisions required to land in this order

1. **MDC scope (#3)** — must be made before Stage 1.3 of the plan. Without an answer
   the `RTMDC.java` impl can't be written.
2. **Multiple markers per event (#4)** — should be made *before or with* Stage 1, so
   `LogEvent` and `BasicEventBuilder` shapes are stable when the runtime starts
   resolving them.
3. **Defensive copy (#11)** — make alongside Stage 1.1; the `RTLogger` Java side
   needs to know whether to copy or not.

The remaining still-open items (#6, #7, #8, #12) can wait until there are real users.
