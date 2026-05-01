# Ecstasy logging — start here

This directory holds the design and discussion docs for the two experimental Ecstasy
logging libraries on the `lagergren/logging` branch:

- **`lib_logging`** — modelled on **SLF4J 2.x + Logback** (the dominant JVM ecosystem).
- **`lib_slogging`** — modelled on **Go's `log/slog`** (the modern cloud-native idiom).

Both libraries cover the same end-to-end logging functionality with different API
shapes, so reviewers can compare them side-by-side. **Start with
[`LIB_LOGGING_VS_LIB_SLOGGING.md`](LIB_LOGGING_VS_LIB_SLOGGING.md)** for the
comparison and the explicit list of what we want reviewer feedback on.

If you are looking at the project for the first time, **this README is the right place
to land first**: it explains what the project is, who it is for, and where to go next
depending on what you want to do.

## What `lib_logging` is in one paragraph

A logging library for Ecstasy that is *instantly familiar* to anyone who has used SLF4J
2.x in Java — same level set, same `{}`-substituted parameterized messages, markers,
MDC, the SLF4J 2.x fluent event builder — but acquired the Ecstasy way: by
`@Inject Logger logger;`. The default sink writes to the platform `Console`; a clean
SPI (`LogSink`) is the API/impl boundary so richer backends (file, network, JSON,
configuration-driven Logback-style) can be dropped in without touching caller code.

The corresponding code is in [`lib_logging/`](../../lib_logging) at the repo root.

## What you'll find in this directory

The docs are organised by question. Pick the one that matches what you want to know:

### "Does this look like the rest of the XDK?"

- **[`XDK_ALIGNMENT.md`](XDK_ALIGNMENT.md)** — surveys other XDK libs (`lib_ecstasy`,
  `lib_cli`, `lib_crypto`, `lib_json`, etc.) and their conventions for injection, the
  shareable-value mixins (`Freezable`/`Stringable`/`Hashable`/`Orderable`), and the
  `const`/`service`/`class` split — and shows how `lib_logging` was aligned.

### "What is this and why are we doing it?"

- **[`PLAN.md`](PLAN.md)** — the master plan: scope, non-goals, ordering of work,
  validation checklist. Read this first if you want the executive summary.
- **[`WHY_SLF4J_AND_INJECTION.md`](WHY_SLF4J_AND_INJECTION.md)** — the rationale
  doc. Argues at length why modeling on SLF4J *and* on Ecstasy injection is the right
  combination, and why the alternatives (JUL, log4j, slog, "roll our own") all fall
  short in specific, predictable ways.
- **[`DESIGN.md`](DESIGN.md)** — the architecture. Module layout, the API↔impl
  boundary, what each type does, how the runtime injection flows.

### "How does this look in practice?"

- **[`INJECTED_LOGGER_EXAMPLE.md`](INJECTED_LOGGER_EXAMPLE.md)** — a complete working
  example of `@Inject Logger` end to end, with a runnable companion at
  `manualTests/src/main/x/TestLogger.x`. Read this before everything else if you
  just want to see what it looks like.
- **[`ECSTASY_VS_JAVA_EXAMPLES.md`](ECSTASY_VS_JAVA_EXAMPLES.md)** — for every
  feature, a working SLF4J Java example followed immediately by the equivalent
  Ecstasy code. Use this to onboard SLF4J users.
- **[`SLF4J_PARITY.md`](SLF4J_PARITY.md)** — exhaustive type-by-type and method-by-
  method mapping from SLF4J 2.x to `lib_logging`. Reference document, not narrative.
- **[`PLATFORM_AND_EXAMPLES_ADAPTATION.md`](PLATFORM_AND_EXAMPLES_ADAPTATION.md)** —
  surveys the existing `~/src/platform` and `~/src/examples` repos and shows
  concretely how their current ad-hoc `console.print($"... Info :")` patterns would
  migrate to `lib_logging`.

### "How would I extend it?"

- **[`CUSTOM_SINKS.md`](CUSTOM_SINKS.md)** — guide to writing your own `LogSink`,
  with worked examples (counting sink, file sink, hierarchical sink, tee, marker
  filter, JSON-line sink). Read this if you're adding a backend.
- **[`STRUCTURED_LOGGING.md`](STRUCTURED_LOGGING.md)** — how SLF4J 2.x structured
  logging (key/value pairs, fluent builder) maps onto `lib_logging`, including a
  sketch of the future structured-event data model and a `JsonLineLogSink`.
- **[`LAZY_LOGGING.md`](LAZY_LOGGING.md)** — exploration of Kotlin `kotlin-logging`
  style lambda emission (`logger.info { "expensive ${x}" }`), what other languages
  do, and three options for adding it to `lib_logging`. Includes an industry survey.

### "What about a real production backend?"

- **[`LOGBACK_INTEGRATION.md`](LOGBACK_INTEGRATION.md)** — sketch of the future
  `lib_logging_logback` module: appenders, layouts, filters, per-logger thresholds,
  programmatic and file-based configuration, hot reload, async appenders, and a
  migration table for SLF4J + Logback users.
- **[`NATIVE_BRIDGE.md`](NATIVE_BRIDGE.md)** — could we instead plug *real Java
  Logback* in via the JIT bridge? Investigation, evidence, trade-offs, and a
  recommendation. Short answer: feasible but not the primary path.

### "Two libraries — what's the comparison?"

- **[`LIB_LOGGING_VS_LIB_SLOGGING.md`](LIB_LOGGING_VS_LIB_SLOGGING.md)** — the design
  comparison between `lib_logging` (SLF4J-shaped) and `lib_slogging` (slog-shaped).
  Per-axis table, deep dives on markers and on `MDC` vs `With`, side-by-side code
  for the common patterns, Ecstasy-idiom fit notes, the **eleven explicit reviewer
  questions** we want feedback on, and a tentative recommendation. **Read this
  first** if you are reviewing the project.
- **[`CLOUD_INTEGRATION.md`](CLOUD_INTEGRATION.md)** — explains why API choice is
  the entry point to GCP / AWS / Azure observability ecosystems (drop-in adapters,
  schema conventions, dashboards-that-already-work). Written for readers whose
  deployment intuition is "ship a CLI installer" and need a tour of cloud-native
  logging conventions.

### "Could the design have looked different?"

- **[`ALTERNATIVE_DESIGN_SLOG_STYLE.md`](ALTERNATIVE_DESIGN_SLOG_STYLE.md)** —
  exploratory predecessor to `lib_slogging`: what an SLF4J-shaped library would look
  like if modeled on Go's `log/slog` instead. Now superseded by the actual
  `lib_slogging` module + `LIB_LOGGING_VS_LIB_SLOGGING.md` comparison; kept for
  history.

### "What's still uncertain?"

- **[`RUNTIME_IMPLEMENTATION_PLAN.md`](RUNTIME_IMPLEMENTATION_PLAN.md)** — the
  actionable plan for turning the v0 stub into a demo-worthy end-to-end round
  trip. Stages, ordering, time estimates, definition of "demo worthy".
- **[`OPEN_QUESTIONS.md`](OPEN_QUESTIONS.md)** — running list of unresolved design
  and implementation questions, with the trade-offs and a tentative recommendation
  for each. None block the v0 stub; they block "v0 working end-to-end".

## Where the actual code lives

In the [`lib_logging/`](../../lib_logging) directory at the repo root.

```
lib_logging/
├── README.md                       short module-level intro, links back here
├── build.gradle.kts                standard XTC plugin + xunit-engine for tests
└── src/
    ├── main/x/
    │   ├── logging.x               module declaration
    │   └── logging/
    │       ├── Level.x             severity enum
    │       ├── Marker.x            marker interface (org.slf4j.Marker)
    │       ├── BasicMarker.x       default marker impl
    │       ├── MarkerFactory.x     factory service (org.slf4j.MarkerFactory)
    │       ├── MDC.x               mapped diagnostic context
    │       ├── LogEvent.x          immutable event const
    │       ├── LogSink.x           SPI — the API/impl boundary
    │       ├── Logger.x            user-facing facade (org.slf4j.Logger)
    │       ├── LoggingEventBuilder.x   SLF4J 2.x fluent builder
    │       ├── LoggerFactory.x     non-injection acquisition path
    │       ├── MessageFormatter.x  {}-substitution + throwable promotion
    │       ├── BasicLogger.x       canonical Logger implementation
    │       ├── BasicEventBuilder.x canonical builder implementation
    │       ├── ConsoleLogSink.x    default sink — writes through @Inject Console
    │       ├── NoopLogSink.x       drops every event
    │       └── MemoryLogSink.x     captures events in memory (test helper)
    └── test/x/
        └── LoggingTest/
            ├── ListLogSink.x       linked-list-backed test sink
            ├── LevelTest.x
            ├── EmissionTest.x      end-to-end per-level routing
            ├── LevelCheckTest.x    threshold / *Enabled property semantics
            ├── MarkerTest.x        marker containment + propagation
            ├── FluentBuilderTest.x atInfo()/atError() builder tests
            └── CountingSinkTest.x  shows how to plug in a custom sink
```

Every `.x` source file has a header comment naming what it corresponds to in SLF4J,
Logback, Log4j 2, or JUL — so an SLF4J reader knows exactly which Java type each
Ecstasy type plays the role of.

## How to read this doc tree depending on who you are

| You are... | Start with | Then read |
|---|---|---|
| New to the project, want the gist | `PLAN.md` | `WHY_SLF4J_AND_INJECTION.md`, `DESIGN.md` |
| An SLF4J/Logback Java engineer | `ECSTASY_VS_JAVA_EXAMPLES.md` | `SLF4J_PARITY.md`, `LOGBACK_INTEGRATION.md` |
| A Go/slog engineer | `ALTERNATIVE_DESIGN_SLOG_STYLE.md` | `WHY_SLF4J_AND_INJECTION.md`, `STRUCTURED_LOGGING.md` |
| Adding a backend / sink | `CUSTOM_SINKS.md` | `STRUCTURED_LOGGING.md`, `LOGBACK_INTEGRATION.md` |
| Investigating performance / hot paths | `LAZY_LOGGING.md` | `OPEN_QUESTIONS.md` (entries on async/copy) |
| Reviewing the proposal | `WHY_SLF4J_AND_INJECTION.md` | `OPEN_QUESTIONS.md`, `DESIGN.md` |
| Implementing the next chunk | `OPEN_QUESTIONS.md` | `PLAN.md` (ordering of work), `NATIVE_BRIDGE.md` |

## Status

`lib_logging` is wired into the XDK build and ships its module in the distribution. The
public API surface and default impls compile (modulo Ecstasy syntax review — see
`OPEN_QUESTIONS.md`). The runtime side that resolves `@Inject Logger` to a real
`BasicLogger` instance is **not yet wired up**; that is the next step (entry 9 in
`OPEN_QUESTIONS.md`).

The unit tests under `lib_logging/src/test/x/LoggingTest/` exercise the API by
constructing `BasicLogger` instances directly against an in-memory `ListLogSink`. They
do not depend on the runtime injection plumbing.

## Reference

The original SLF4J provider/architecture writeup that shaped much of this design is in
[`slf4j_full_guide.md`](../../slf4j_full_guide.md) at the repo root. It is a Java-side
guide to building a custom SLF4J binding and is the source of the architectural
template (`MyLangLogger` → `RuntimeLogSink` → backend) we adapted into
`Logger` → `LogSink` → backend.
