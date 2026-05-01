# Ecstasy logging — start here

This branch (`lagergren/logging`) adds **two parallel logging libraries** to the
XDK. They are an experiment, not a final shipping decision: we built both so
reviewers can compare them side-by-side and tell us which API shape Ecstasy
should adopt long-term.

- **[`lib_logging`](../../lib_logging/)** — modelled on **SLF4J 2.x + Logback**.
  Familiar to anyone with JVM background. 51 unit tests, end-to-end demo.
- **[`lib_slogging`](../../lib_slogging/)** — modelled on **Go's `log/slog`**
  (the modern cloud-native idiom). 22 unit tests, parallel design.

If you only have time to read one document, read
**[`lib-logging-vs-lib-slogging.md`](lib-logging-vs-lib-slogging.md)** — it is
the comparison, the reviewer-question list, and the recommendation in one
place.

## What we want from you

There are **eleven explicit reviewer questions** in
[`lib-logging-vs-lib-slogging.md` § 5](lib-logging-vs-lib-slogging.md#5-what-we-want-reviewer-feedback-on)
and **seven language-design questions** (Q-D1..Q-D7) in
[`open-questions.md`](open-questions.md). Both are calibrated for someone who
has skimmed the headline comparison; you do not need to read the entire doc
tree to weigh in.

## Reading paths

Pick the one that matches what you are.

### Reviewing the proposal — what should I read?

1. **[`lib-logging-vs-lib-slogging.md`](lib-logging-vs-lib-slogging.md)** — the
   side-by-side comparison, deep dive on markers, eleven reviewer questions,
   tentative recommendation.
2. **[`cloud-integration.md`](cloud-integration.md)** — why the API choice is
   the entry point to GCP / AWS / Azure observability ecosystems. Explains the
   modern deployment world for readers whose intuition is "ship a CLI
   installer."
3. **[`open-questions.md`](open-questions.md)** — questions for the XTC
   language designers (Q-D1..Q-D7), tracking list of work not yet implemented,
   implementation tiers.

That is enough to weigh in on the design choice. The rest of the tree is
support material.

### I'm an SLF4J / Logback Java engineer — show me what changes

1. **[`ecstasy-vs-java-examples.md`](ecstasy-vs-java-examples.md)** — every
   SLF4J idiom with the equivalent Ecstasy code beside it.
2. **[`slf4j-parity.md`](slf4j-parity.md)** — exhaustive type-by-type and
   method-by-method mapping. Reference, not narrative.
3. **[`injected-logger-example.md`](injected-logger-example.md)** — a complete
   end-to-end example of `@Inject Logger logger;` with a runnable companion at
   `manualTests/src/main/x/TestLogger.x`.

### I'm a Go / slog engineer — show me what changes

1. **[`lib-logging-vs-lib-slogging.md`](lib-logging-vs-lib-slogging.md)** — has
   side-by-side slog → Ecstasy code throughout.
2. **The `lib_slogging` source** at
   [`lib_slogging/src/main/x/slogging/`](../../lib_slogging/src/main/x/slogging/)
   — the API surface is small (Logger, Handler, Record, Attr, Level) and each
   file has a doc-comment naming its slog counterpart.

### I'm an XTC language designer — what's open?

1. **[`open-questions.md`](open-questions.md)** §§ "Questions for the XTC
   language / runtime designers" (Q-D1..Q-D7). Each question is tied to a
   concrete piece of code we wrote and includes the workaround we adopted.
2. **[`design.md`](design.md)** — `lib_logging` architecture, including the
   `const` vs `service` rule for sinks and the per-fiber `MDC` mechanism.

### I want to write my own sink / handler

1. **[`custom-sinks.md`](custom-sinks.md)** — guide to writing a custom
   `LogSink`, with worked examples (counting, file, hierarchical, tee,
   marker-filtering, JSON-line). Includes the `const` vs `service` decision.
2. **[`structured-logging.md`](structured-logging.md)** — how SLF4J 2.x
   structured logging maps onto `lib_logging`, with a sketch of a JSON sink.

### I want to deploy this to GCP / AWS / Azure

1. **[`cloud-integration.md`](cloud-integration.md)** — adapter graphs for the
   three major clouds, with concrete examples and the table-stakes API
   features each cloud product depends on.
2. **[`logback-integration.md`](logback-integration.md)** — sketch of a future
   configuration-driven binding (Tier 3 work, not in this PR).
3. **[`native-bridge.md`](native-bridge.md)** — could we instead plug *real
   Java Logback* in via the JIT bridge? Investigation, evidence, recommendation.

### I want to migrate existing Ecstasy code that already does ad-hoc logging

1. **[`platform-and-examples-adaptation.md`](platform-and-examples-adaptation.md)**
   — surveys `~/src/platform` and `~/src/examples` and shows how their
   `console.print($"... Info :")` patterns would migrate.

## Full doc index

| Doc | One-line description |
|---|---|
| **Top-level** | |
| [`README.md`](README.md) | This file. The "start here" entry point. |
| [`lib-logging-vs-lib-slogging.md`](lib-logging-vs-lib-slogging.md) | Headline comparison + reviewer questions. **Read first.** |
| [`cloud-integration.md`](cloud-integration.md) | Why the API choice is the entry point to cloud observability ecosystems. |
| [`open-questions.md`](open-questions.md) | Live tracking: open questions, designer questions (Q-D1..Q-D7), W-item parity list, implementation tiers. |
| **Design (`lib_logging` side)** | |
| [`design.md`](design.md) | `lib_logging` architecture: types, API↔impl boundary, sink-type rule, MDC mechanism, per-container override. |
| [`why-slf4j-and-injection.md`](why-slf4j-and-injection.md) | The original rationale for the SLF4J shape and injection-first acquisition. |
| [`xdk-alignment.md`](xdk-alignment.md) | Alignment with the conventions of other XDK libraries (`lib_ecstasy`, `lib_cli`, etc.). |
| **Usage / examples** | |
| [`injected-logger-example.md`](injected-logger-example.md) | End-to-end working example of `@Inject Logger`. |
| [`ecstasy-vs-java-examples.md`](ecstasy-vs-java-examples.md) | Per-feature SLF4J Java vs `lib_logging` Ecstasy. |
| [`slf4j-parity.md`](slf4j-parity.md) | Exhaustive type/method mapping reference. |
| [`platform-and-examples-adaptation.md`](platform-and-examples-adaptation.md) | Migration survey for existing Ecstasy code bases. |
| **Extension** | |
| [`custom-sinks.md`](custom-sinks.md) | How to write a custom `LogSink`, with worked examples. |
| [`structured-logging.md`](structured-logging.md) | Structured logging dive: key/value pairs, JSON output, fluent builder. |
| **Tier 3 / future work** | |
| [`logback-integration.md`](logback-integration.md) | Sketch of a configuration-driven Logback-style binding. |
| [`native-bridge.md`](native-bridge.md) | Wrapping real Java Logback through the JIT bridge — feasibility analysis. |
| [`lazy-logging.md`](lazy-logging.md) | Kotlin-style lambda emission (`logger.info { "..." }`) — exploration. |
| [`runtime-implementation-plan.md`](runtime-implementation-plan.md) | Mostly historical: the original runtime-wiring plan. Stages 1–3 have landed; the JIT-side equivalent (Stage 1) is open. |

## Where the actual code lives

```
lib_logging/                            SLF4J-shaped library
├── build.gradle.kts
├── README.md                           module-level intro
└── src/
    ├── main/x/
    │   ├── logging.x                   module declaration
    │   └── logging/
    │       ├── Level.x                 severity enum
    │       ├── Marker.x                marker interface (org.slf4j.Marker)
    │       ├── BasicMarker.x           default marker impl
    │       ├── MarkerFactory.x         factory service
    │       ├── MDC.x                   per-fiber mapped diagnostic context (const)
    │       ├── LogEvent.x              immutable event const (Marker[] markers)
    │       ├── LogSink.x               SPI — the API/impl boundary
    │       ├── Logger.x                user-facing facade interface
    │       ├── LoggingEventBuilder.x   SLF4J 2.x fluent builder
    │       ├── LoggerFactory.x         non-injection acquisition path
    │       ├── LoggerRegistry.x        identity-stable interning of named children
    │       ├── MessageFormatter.x      {}-substitution + throwable promotion
    │       ├── BasicLogger.x           canonical Logger impl (const)
    │       ├── BasicEventBuilder.x     canonical builder impl
    │       ├── ConsoleLogSink.x        default sink (const), forwards to @Inject Console
    │       ├── NoopLogSink.x           drops every event (const)
    │       └── MemoryLogSink.x         test-helper, captures events (service)
    └── test/x/LoggingTest/             51 unit tests

lib_slogging/                           slog-shaped sibling library
├── build.gradle.kts
└── src/
    ├── main/x/
    │   ├── slogging.x                  module declaration
    │   └── slogging/
    │       ├── Level.x                 open Int severity (Level(severity, label))
    │       ├── Attr.x                  the single structured-data carrier
    │       ├── Record.x                immutable event const (the LogEvent equivalent)
    │       ├── Handler.x               SPI (the LogSink equivalent)
    │       ├── Logger.x                concrete user-facing const
    │       ├── TextHandler.x           default human-readable handler (const)
    │       ├── JSONHandler.x           JSON-Lines structured handler (const, skeleton)
    │       ├── NopHandler.x            drops every record (const)
    │       └── MemoryHandler.x         test-helper (service)
    └── test/x/SLoggingTest/            22 unit tests
```

## Status

**Both libraries compile and pass tests.** End-to-end demo runs in
`manualTests/TestLogger.x` (interpreter side; JIT-side wiring is Tier 3).
Tier 1+2 work landed in this branch; Tier 3 (compiler default-name,
`AsyncLogSink`, `lib_logging_logback`, native bridge) is explicitly out of
scope for this PR — see `open-questions.md` for the tier breakdown. The
branch is ready for reviewer feedback on the design choice.

## Reference

The original SLF4J architecture writeup that shaped much of `lib_logging` is in
[`slf4j_full_guide.md`](../../slf4j_full_guide.md) at the repo root. It is the
source of the architectural template (`MyLangLogger` → `RuntimeLogSink` →
backend) we adapted into `Logger` → `LogSink` → backend.
