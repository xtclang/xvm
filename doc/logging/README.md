# Ecstasy logging — start here

This branch (`lagergren/logging`) adds two parallel logging libraries to the XDK
so reviewers can compare the two familiar industry shapes in real Ecstasy code
before choosing one canonical API.

Both libraries use the same XDK-facing architecture: application code receives an
injected `Logger`, the container owns the backend, and production behavior is
provided by replacing a `LogSink` or `Handler`. Both can carry structured fields
to JSON/cloud sinks without parsing message text.

| Library | Prior art | Current proof |
|---|---|---|
| [`lib_logging`](../../lib_logging/) | SLF4J 2.x + Logback | 54 focused XTC test methods and an injected manual demo. |
| [`lib_slogging`](../../lib_slogging/) | Go `log/slog` | 34 focused XTC test methods and matching injected/manual coverage. |

If you only have time to read one document, read
**[`lib-logging-vs-lib-slogging.md`](lib-logging-vs-lib-slogging.md)** — it is
the comparison, the reviewer-question list, and the recommendation in one
place.

## Review Focus

The main design question is which single API Ecstasy should make canonical.
Keeping both indefinitely would recreate the logging fragmentation these APIs were
designed to avoid. The concrete reviewer prompts live in
[`lib-logging-vs-lib-slogging.md` § 5](lib-logging-vs-lib-slogging.md#5-what-we-want-reviewer-feedback-on);
language/runtime questions are tracked as Q-D1..Q-D7 in
[`open-questions.md`](open-questions.md).

## Reading Paths

Start with [`lib-logging-vs-lib-slogging.md`](lib-logging-vs-lib-slogging.md) if
you only read one file. The rest of the tree is organized by reader:

| Reader | Start with | Then read |
|---|---|---|
| Proposal reviewer | [`lib-logging-vs-lib-slogging.md`](lib-logging-vs-lib-slogging.md) | [`api-cross-reference.md`](api-cross-reference.md), [`cloud-integration.md`](cloud-integration.md), [`open-questions.md`](open-questions.md) |
| SLF4J / Logback engineer | [`usage/ecstasy-vs-java-examples.md`](usage/ecstasy-vs-java-examples.md) | [`usage/slf4j-parity.md`](usage/slf4j-parity.md), [`usage/injected-logger-example.md`](usage/injected-logger-example.md) |
| Go `log/slog` engineer | [`usage/slog-parity.md`](usage/slog-parity.md) | [`api-cross-reference.md`](api-cross-reference.md), [`lib_slogging` source](../../lib_slogging/src/main/x/slogging/) |
| XTC language designer | [`open-questions.md`](open-questions.md) | [`design/design.md`](design/design.md), [`design/xdk-alignment.md`](design/xdk-alignment.md) |
| Sink / handler author | [`usage/custom-sinks.md`](usage/custom-sinks.md), [`usage/custom-handlers.md`](usage/custom-handlers.md) | [`usage/structured-logging.md`](usage/structured-logging.md) |
| Cloud/backend reviewer | [`cloud-integration.md`](cloud-integration.md) | [`future/logback-integration.md`](future/logback-integration.md), [`future/native-bridge.md`](future/native-bridge.md) |
| Migration reviewer | [`usage/platform-and-examples-adaptation.md`](usage/platform-and-examples-adaptation.md) | [`usage/injected-logger-example.md`](usage/injected-logger-example.md) |

## Full doc index

| Doc | One-line description |
|---|---|
| **Top-level** | |
| [`README.md`](README.md) | This file. The "start here" entry point. |
| [`lib-logging-vs-lib-slogging.md`](lib-logging-vs-lib-slogging.md) | Headline comparison + reviewer questions. **Read first.** |
| [`api-cross-reference.md`](api-cross-reference.md) | Official SLF4J / Go slog API links mapped to local Ecstasy types and difference notes. |
| [`cloud-integration.md`](cloud-integration.md) | Why the API choice is the entry point to cloud observability ecosystems. |
| [`open-questions.md`](open-questions.md) | Live tracking: open questions, designer questions (Q-D1..Q-D7), W-item parity list, implementation tiers. |
| **Design (`lib_logging` side)** | |
| [`design/design.md`](design/design.md) | `lib_logging` architecture: types, API↔impl boundary, sink-type rule, MDC mechanism, per-container override. |
| [`design/why-slf4j-and-injection.md`](design/why-slf4j-and-injection.md) | The original rationale for the SLF4J shape and injection-first acquisition. |
| [`design/xdk-alignment.md`](design/xdk-alignment.md) | Alignment with the conventions of other XDK libraries (`lib_ecstasy`, `lib_cli`, etc.). |
| **Usage / examples** | |
| [`usage/injected-logger-example.md`](usage/injected-logger-example.md) | End-to-end working example of `@Inject Logger`. |
| [`usage/ecstasy-vs-java-examples.md`](usage/ecstasy-vs-java-examples.md) | Per-feature SLF4J Java vs `lib_logging` Ecstasy. |
| [`usage/slf4j-parity.md`](usage/slf4j-parity.md) | Exhaustive type/method mapping reference. |
| [`usage/slog-parity.md`](usage/slog-parity.md) | Go `log/slog` vs `lib_slogging` mapping reference. |
| [`usage/platform-and-examples-adaptation.md`](usage/platform-and-examples-adaptation.md) | Migration survey for existing Ecstasy code bases. |
| **Extension** | |
| [`usage/custom-sinks.md`](usage/custom-sinks.md) | How to write a custom `LogSink`, with worked examples. |
| [`usage/custom-handlers.md`](usage/custom-handlers.md) | How to write a custom slog `Handler`, with worked examples. |
| [`usage/structured-logging.md`](usage/structured-logging.md) | Structured logging dive: key/value pairs, JSON output, fluent builder. |
| **Tier 3 / future work** | |
| [`future/logback-integration.md`](future/logback-integration.md) | Sketch of a configuration-driven Logback-style binding. |
| [`future/native-bridge.md`](future/native-bridge.md) | Wrapping real Java Logback through the JIT bridge — feasibility analysis. |
| [`future/lazy-logging.md`](future/lazy-logging.md) | Kotlin-style lambda emission (`logger.info { "..." }`) — exploration. |
| [`future/runtime-implementation-plan.md`](future/runtime-implementation-plan.md) | Mostly historical: the original runtime-wiring plan. Stages 1–3 have landed; the JIT-side equivalent (Stage 1) is open. |

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
    └── test/x/LoggingTest/             54 focused XTC test methods

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
    │       ├── BoundHandler.x          default with/withGroup derivation wrapper
    │       ├── Logger.x                concrete user-facing const
    │       ├── LoggerContext.x         optional SharedContext<Logger> helper
    │       ├── TextHandler.x           default human-readable handler (const)
    │       ├── JSONHandler.x           JSON-Lines handler (const, lib_json renderer)
    │       ├── NopHandler.x            drops every record (const)
    │       └── MemoryHandler.x         test-helper (service)
    └── test/x/SLoggingTest/            34 focused XTC test methods
```

## Status

**Both libraries are intended to compile and pass tests.** End-to-end demo runs in
`manualTests/TestLogger.x` and exercises both injected logger types (interpreter side;
JIT-side wiring is Tier 3).
Tier 1+2 work landed in this branch; Tier 3 (compiler default-name,
`AsyncLogSink`, `lib_logging_logback`, native bridge) is explicitly out of
scope for this PR — see `open-questions.md` for the tier breakdown. The
branch is ready for reviewer feedback on the design choice.

## Reference

The original SLF4J architecture writeup that shaped much of `lib_logging` is in
[`slf4j_full_guide.md`](../../slf4j_full_guide.md) at the repo root. It is the
source of the architectural template (`MyLangLogger` → `RuntimeLogSink` →
backend) we adapted into `Logger` → `LogSink` → backend.
