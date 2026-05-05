# Ecstasy logging — start here

This branch (`lagergren/logging`) adds two parallel logging libraries to the XDK
so reviewers can compare two familiar industry shapes in real Ecstasy code. The
branch now recommends `lib_logging` as the canonical API shape and keeps
`lib_slogging` as the comparison/reference POC.

The two libraries coexist **only for this POC**. Application and XDK code should use
one logging shape or the other, not both in the same design. Once the XDK settles on an
injectable logging model, the winning API should be the XDK's `lib_logging`; the losing
POC should either disappear or become an explicitly named adapter/comparison module.

## First-pass summary

Read this file first. It gives the whole design without requiring any deep dive:

1. Application code asks for a logger with `@Inject Logger logger;`.
2. The injected logger is a small facade. Caller code logs messages, exceptions,
   structured fields, markers/categories, and request context.
3. The host/container owns the backend. It can replace the active `LogSink` or `Handler`
   with console, JSON, async, fanout, hierarchical-level, file, cloud, or test capture
   implementations without changing application code.
4. `lib_logging` is the recommended canonical shape because it gives Ecstasy the
   SLF4J/Logback model most JVM users expect: named loggers, `{}` formatting, markers,
   MDC, fluent event builders, and a narrow `LogSink` backend SPI.
5. `lib_slogging` exists to compare the Go `log/slog` model: one structured carrier
   (`Attr`), derived loggers via `with(...)`, open integer levels, grouped attrs, and a
   `Handler` backend SPI.
6. The POC already implements both facades, injection, JSON output, redaction knobs,
   async wrappers, memory test capture, and backend extension points. Production config
   loading, rolling files, provider-specific cloud clients, automatic source capture, and
   compiler-generated logger names are follow-up modules/runtime work.

For a first review pass, read this README and then the top summaries of
[`lib-logging-vs-lib-slogging.md`](lib-logging-vs-lib-slogging.md),
[`usage/structured-logging.md`](usage/structured-logging.md), and
[`usage/configuration.md`](usage/configuration.md). Those documents keep their long
rationale and implementation details below the fast path.

## Requirements

The accepted XDK logging API **MUST** satisfy these requirements:

- **One canonical facade:** there MUST be one injectable logging API, published as `lib_logging`.
- **Familiar call shape:** the call shape MUST be instantly recognizable to either SLF4J/Logback or slog users,
  with migration examples for Java/Go teams.
- **Injection-first acquisition:** `@Inject Logger logger;` MUST let the host/container own
  backend policy; library code MUST NOT choose global process logging.
- **Structured events:** events MUST carry first-class key/value fields, context fields, exceptions,
  markers/categories where applicable, and JSON/cloud output without parsing messages.
- **Dynamically pluggable backends:** sinks/handlers MUST be swappable, composable, wrappable
  with async queues, and replaced by a host-controlled reload service.
- **Logback-equivalent operations:** the backend MUST support root level, per-logger/category overrides,
  multi-destination fanout, async output, JSON/text formatting, redaction, and
  configuration-file reload.
- **Production safety:** backend failures MUST NOT break callers; redaction,
  output destinations, formatting knobs, and shutdown/flush behavior MUST be explicit.
- **Testability:** the API MUST provide in-memory capture sinks/handlers and contract tests for custom
  backends.
- **Runtime/compiler path:** default logger naming and source-location capture MUST
  have a clear lowering target even if full compiler sugar lands later.

Both libraries use the same XDK-facing architecture: application code receives an
injected `Logger`, the container owns the backend, and production behavior is
provided by replacing a `LogSink` or `Handler`. Both can carry structured fields
to JSON/cloud sinks without parsing message text.

| Library | Prior art | Current proof |
|---|---|---|
| [`lib_logging`](../../lib_logging/) | SLF4J 2.x + Logback | 64 focused XTC test methods, injected manual demo, async/composite/hierarchical/JSON backend building blocks. |
| [`lib_slogging`](../../lib_slogging/) | Go `log/slog` | 37 focused XTC test methods, injected/manual coverage, async handler, handler options, and JSON/redaction support. |

## Recommendation

Choose **`lib_logging`** as the canonical XDK logging facade. If reviewers choose the
slog shape instead, it should still graduate under the canonical `lib_logging` name;
`lib_slogging` is a POC name, not a proposed permanent sibling. The current
recommendation is the SLF4J-shaped implementation because it gives Ecstasy the
SLF4J/Logback-shaped surface that JVM users recognize immediately: named loggers,
`{}` formatting, markers, MDC, fluent event builders, and a `LogSink` backend
boundary. The slog-shaped library remains useful review material and an adapter
candidate, but keeping both indefinitely would recreate the logging fragmentation
these APIs were designed to avoid.

## Reading Paths

Start here, then follow only the path that matches your review:

1. **First-pass design:** this README.
2. **API choice:** [`lib-logging-vs-lib-slogging.md`](lib-logging-vs-lib-slogging.md).
3. **Structured logging requirement:** [`usage/structured-logging.md`](usage/structured-logging.md).
4. **Configuration/backend story:** [`usage/configuration.md`](usage/configuration.md).
5. **Exact API mapping:** [`api-cross-reference.md`](api-cross-reference.md).

The rest of the tree is organized by reader:

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
| [`lib-logging-vs-lib-slogging.md`](lib-logging-vs-lib-slogging.md) | API-choice summary first, then depth-first rationale on levels, context, markers, formatting, and backend shape. |
| [`api-cross-reference.md`](api-cross-reference.md) | Official SLF4J / Go slog API links mapped to local Ecstasy types and difference notes. |
| [`cloud-integration.md`](cloud-integration.md) | Why the API choice is the entry point to cloud observability ecosystems. |
| [`open-questions.md`](open-questions.md) | Decision tracker: resolved calls, designer questions (Q-D1..Q-D7), W-item parity list, and remaining follow-up. |
| [`review-prompt.md`](review-prompt.md) | Self-contained prompt for another AI agent to review the branch and documentation. |
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
| [`usage/configuration.md`](usage/configuration.md) | Logback XML vs proposed Ecstasy JSON configuration, dynamic sink/handler reload, and equivalence mapping. |
| **Follow-up backend/compiler work** | |
| [`future/logback-integration.md`](future/logback-integration.md) | Configuration-driven Logback-style backend on top of the shipped primitives. |
| [`future/native-bridge.md`](future/native-bridge.md) | Optional Java Logback bridge — feasibility analysis and why it is not the default path. |
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
    │       ├── JsonLogSink.x           JSON-Lines sink (const), rendered by lib_json
    │       ├── JsonLogSinkOptions.x    root level, redaction, inclusion, field names
    │       ├── CompositeLogSink.x      multi-destination fanout (const)
    │       ├── HierarchicalLogSink.x   longest-prefix per-logger thresholds (service)
    │       ├── AsyncLogSink.x          bounded async wrapper (service)
    │       ├── NoopLogSink.x           drops every event (const)
    │       └── MemoryLogSink.x         test-helper, captures events (service)
    └── test/x/LoggingTest/             64 focused XTC test methods

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
    │       ├── HandlerOptions.x        threshold, redaction, source, field-name knobs
    │       ├── AsyncHandler.x          bounded async wrapper (service)
    │       ├── NopHandler.x            drops every record (const)
    │       └── MemoryHandler.x         test-helper (service)
    └── test/x/SLoggingTest/            37 focused XTC test methods
```

## Status

**Both libraries are intended to compile and pass tests.** End-to-end demo runs in
`manualTests/TestLogger.x` and exercises both injected logger types on the
interpreter side. Runtime fallback naming for injected `logging.Logger` now derives
from the caller namespace when the compiler only supplies the default field name
`"logger"`. Explicit source metadata is available through `Logger.logAt(...)`;
automatic compiler call-site capture remains the next compiler/runtime polish step.

Tier 3 backend primitives have landed in the base libraries. A full
configuration-file loader, rolling-file/network destinations, and an optional Java
Logback bridge are deliberately not shipped in this branch; the docs explain where
those belong if the canonical `lib_logging` API is accepted.

## Reference

The original SLF4J architecture writeup that shaped much of `lib_logging` is in
[`slf4j_full_guide.md`](../../slf4j_full_guide.md) at the repo root. It is the
source of the architectural template (`MyLangLogger` → `RuntimeLogSink` →
backend) we adapted into `Logger` → `LogSink` → backend.
