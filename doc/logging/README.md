# Ecstasy logging — start here

`lib_logging` is the XDK's injectable logging library. Application code asks the
runtime for a logger and writes events; the host container chooses where the
output goes.

```ecstasy
@Inject Logger logger;
logger.info("processed {} records in {}ms", [count, elapsed]);
```

That is the whole hello-world. There is no factory call, no static initializer,
no configuration file. The default `ConsoleLogSink` writes the line; the host
can swap that for JSON, async, fanout, hierarchical levels, or a test capture
sink without changing caller code.

> **New here?** Read [`usage/injected-logger-example.md`](usage/injected-logger-example.md) next — it walks
> from the smallest possible app up to a realistic per-class logger setup. Come
> back to this README for the design context.

## Reading paths

| If you want to… | Read in this order |
|---|---|
| **Write code against `lib_logging`** | [`usage/injected-logger-example.md`](usage/injected-logger-example.md) → [`usage/structured-logging.md`](usage/structured-logging.md) → [`usage/lazy-logging.md`](usage/lazy-logging.md) |
| **Decide whether to merge this PR** | [`roadmap.md`](roadmap.md) → [`lib-logging-vs-lib-slogging.md`](lib-logging-vs-lib-slogging.md) → [`open-questions.md`](open-questions.md) |
| **Understand the API choice** | [`lib-logging-vs-lib-slogging.md`](lib-logging-vs-lib-slogging.md) → [`design/why-slf4j-and-injection.md`](design/why-slf4j-and-injection.md) → [`cloud-integration.md`](cloud-integration.md) |
| **Migrate from SLF4J** | [`usage/slf4j-parity.md`](usage/slf4j-parity.md) → [`usage/ecstasy-vs-java-examples.md`](usage/ecstasy-vs-java-examples.md) |
| **Migrate from Go `slog`** | [`usage/slog-parity.md`](usage/slog-parity.md) → [`api-cross-reference.md`](api-cross-reference.md) |
| **Write a custom backend** | [`usage/custom-sinks.md`](usage/custom-sinks.md) → [`design/design.md`](design/design.md) |
| **Read the architecture** | [`design/design.md`](design/design.md) → [`design/xdk-alignment.md`](design/xdk-alignment.md) |

Each doc has a "Next →" footer that follows its column above. You can also use
the [Full doc index](#full-doc-index) at the bottom.

## Goals

Why this library exists. These are the directional aims, not contractual specs:

- **Familiar.** A JVM or Go engineer should recognize the call shape on first
  read, with no Ecstasy-specific learning curve for the common case.
- **Container-aware.** The host application — not the calling library — decides
  where logs go. Per-container override is a one-line change, not a classpath
  ceremony.
- **Cloud-ready.** Structured fields (level, message, request context, exception)
  flow into JSON output and from there into Cloud Logging / CloudWatch / App
  Insights through small, well-known adapters. See [`cloud-integration.md`](cloud-integration.md).
- **Pay-for-what-you-use.** Disabled log calls cost a level check. Expensive
  message and value construction can be deferred behind that check.
- **Testable.** Capturing log output in a test should be one line, with no
  global state to reset.

## Requirements

The narrower, verifiable list — what the accepted XDK logging API **must** do.
Status reflects what the POC in this branch ships:

| # | Requirement | Status | Evidence |
|---|---|---|---|
| R1 | One canonical injectable logger acquired by `@Inject Logger logger;`. | ✓ done | [`Logger.x`](../../lib_logging/src/main/x/logging/Logger.x), [`NativeContainer.java`](../../javatools/src/main/java/org/xvm/runtime/NativeContainer.java) `registerLoggingResources` |
| R2 | Per-name child loggers via API, not via wildcard injection. | ✓ done | `Logger.named(String)` in [`Logger.x`](../../lib_logging/src/main/x/logging/Logger.x); [`NamedLoggerTest`](../../lib_logging/src/test/x/LoggingTest/NamedLoggerTest.x) |
| R3 | Events carry first-class structured key/value fields, exception, MDC, markers. | ✓ done | [`LogEvent.x`](../../lib_logging/src/main/x/logging/LogEvent.x); [`StructuredLoggingTest`](../../lib_logging/src/test/x/LoggingTest/StructuredLoggingTest.x) |
| R4 | Disabled log calls are cheap; expensive args/values can be supplied lazily. | ✓ done | `MessageSupplier`, `addLazyArgument` in [`LoggingEventBuilder.x`](../../lib_logging/src/main/x/logging/LoggingEventBuilder.x); [`LazyLoggingTest`](../../lib_logging/src/test/x/LoggingTest/LazyLoggingTest.x) |
| R5 | Backends are swappable, composable, async-wrappable, and test-capturable. | ✓ done | [`CompositeLogSink.x`](../../lib_logging/src/main/x/logging/CompositeLogSink.x), [`AsyncLogSink.x`](../../lib_logging/src/main/x/logging/AsyncLogSink.x), [`MemoryLogSink.x`](../../lib_logging/src/main/x/logging/MemoryLogSink.x) |
| R6 | Per-logger-name level routing (Logback longest-prefix). | ✓ done | [`HierarchicalLogSink.x`](../../lib_logging/src/main/x/logging/HierarchicalLogSink.x); [`HierarchicalLogSinkTest`](../../lib_logging/src/test/x/LoggingTest/HierarchicalLogSinkTest.x) |
| R7 | JSON output with redaction. | ✓ done | [`JsonLogSink.x`](../../lib_logging/src/main/x/logging/JsonLogSink.x), [`JsonLogSinkOptions.x`](../../lib_logging/src/main/x/logging/JsonLogSinkOptions.x); [`JsonLogSinkTest`](../../lib_logging/src/test/x/LoggingTest/JsonLogSinkTest.x) |
| R8 | Backend failures must not break callers. | ✓ contract | Documented in [`LogSink.x`](../../lib_logging/src/main/x/logging/LogSink.x); enforcement still up to each sink. |
| R9 | In-memory capture sink for tests. | ✓ done | [`MemoryLogSink.x`](../../lib_logging/src/main/x/logging/MemoryLogSink.x); used throughout `lib_logging/src/test/` |
| R10 | Default logger name and source-location capture have a stable lowering target the compiler can fill in later. | ~ partial | `Logger.logAt(...)` populates source explicitly; runtime fallback derives logger name from caller namespace. Compiler-synthesized source/name still TBD — see [`roadmap.md`](roadmap.md). |
| R11 | Configuration-file driven backend (Logback-equivalent ops). | → v1+ | Sketched in [`future/logback-integration.md`](future/logback-integration.md); not in this branch. |

## Non-goals (this branch)

These are explicitly out of scope for the POC. Listing them prevents reviewer
churn over things that were never the target:

- **A configuration file format.** `JsonLogSinkOptions` covers the JSON sink's
  knobs; XML/JSON/YAML config and hot reload belong to a future configured
  backend.
- **Distributed tracing context propagation.** MDC carries strings; tracing
  IDs can ride on it. Trace/span propagation itself is a separate library.
- **Automatic compiler call-site capture.** `Logger.logAt(sourceFile, sourceLine)`
  is the lowering target; the compiler does not yet synthesize those args for
  ordinary `logger.info(...)` calls.
- **Rolling/network/cloud destinations.** They are pure-XTC sink modules built
  on the SPI; out of scope for the base library.

## Two POCs in this branch

This branch carries two parallel libraries so reviewers can compare API shapes
in real Ecstasy code:

| Library | Prior art | Tests |
|---|---|---|
| [`lib_logging`](../../lib_logging/) | SLF4J 2.x + Logback | 70 XTC test methods, [`TestLogging.x`](../../manualTests/src/main/x/TestLogging.x) demo |
| [`lib_slogging`](../../lib_slogging/) | Go `log/slog` | 41 XTC test methods, [`TestSLogging.x`](../../manualTests/src/main/x/TestSLogging.x) demo |

The branch **recommends `lib_logging`** as the canonical XDK shape on
familiarity and ecosystem grounds — the [API-choice doc](lib-logging-vs-lib-slogging.md)
explains why. Whichever shape wins should ship as `lib_logging`; `lib_slogging`
is a POC name, not a proposed permanent sibling. Both modules are deliberately
optional in the runtime: a build that only ships one of them must still start.

## Status & roadmap

The POC implements every requirement above except R11 (configuration-file
backend) and the compiler-side half of R10. The merge-blocking decisions, the
v1 scope, and the explicit-future items are tracked in
[`roadmap.md`](roadmap.md). Outstanding language/runtime questions for the XTC
designers are in [`open-questions.md`](open-questions.md) (Q-D1…Q-D7).

## Full doc index

| Doc | One-line description |
|---|---|
| **Top-level** | |
| [`README.md`](README.md) | This file. The "start here" entry point. |
| [`roadmap.md`](roadmap.md) | What blocks merge, what lands in v1, what is explicit future. |
| [`lib-logging-vs-lib-slogging.md`](lib-logging-vs-lib-slogging.md) | API-choice summary first, then depth-first rationale on levels, context, markers, formatting, and backend shape. |
| [`api-cross-reference.md`](api-cross-reference.md) | Official SLF4J / Go slog API links mapped to local Ecstasy types and difference notes. |
| [`cloud-integration.md`](cloud-integration.md) | Why the API choice is the entry point to cloud observability ecosystems. |
| [`open-questions.md`](open-questions.md) | Decision tracker: resolved calls, designer questions (Q-D1..Q-D7), W-item parity list, and remaining follow-up. |
| **Concepts** | |
| [`concepts/markers.md`](concepts/markers.md) | What a log marker is, who uses them, when they earn their API surface. |
| **Design (`lib_logging` side)** | |
| [`design/design.md`](design/design.md) | `lib_logging` architecture: types, API↔impl boundary, sink-type rule, MDC mechanism, per-container override. |
| [`design/why-slf4j-and-injection.md`](design/why-slf4j-and-injection.md) | The original rationale for the SLF4J shape and injection-first acquisition. |
| [`design/xdk-alignment.md`](design/xdk-alignment.md) | Alignment with the conventions of other XDK libraries (`lib_ecstasy`, `lib_cli`, etc.). |
| **Usage / examples** | |
| [`usage/injected-logger-example.md`](usage/injected-logger-example.md) | End-to-end working example of `@Inject Logger`. **Suggested first stop for new users.** |
| [`usage/ecstasy-vs-java-examples.md`](usage/ecstasy-vs-java-examples.md) | Per-feature SLF4J Java vs `lib_logging` Ecstasy. |
| [`usage/slf4j-parity.md`](usage/slf4j-parity.md) | Exhaustive type/method mapping reference. |
| [`usage/slog-parity.md`](usage/slog-parity.md) | Go `log/slog` vs `lib_slogging` mapping reference. |
| [`usage/platform-and-examples-adaptation.md`](usage/platform-and-examples-adaptation.md) | Migration survey for existing Ecstasy code bases. |
| **Extension** | |
| [`usage/custom-sinks.md`](usage/custom-sinks.md) | How to write a custom `LogSink`, with worked examples. |
| [`usage/custom-handlers.md`](usage/custom-handlers.md) | How to write a custom slog `Handler`, with worked examples. |
| [`usage/structured-logging.md`](usage/structured-logging.md) | Structured logging dive: key/value pairs, JSON output, fluent builder. |
| [`usage/lazy-logging.md`](usage/lazy-logging.md) | Lazy message/value construction: Kotlin blocks, Java suppliers, and the Ecstasy API. |
| [`usage/configuration.md`](usage/configuration.md) | Logback XML vs proposed Ecstasy JSON configuration, dynamic sink/handler reload, and equivalence mapping. |
| **Follow-up backend/compiler work** | |
| [`future/logback-integration.md`](future/logback-integration.md) | Configuration-driven Logback-style backend on top of the shipped primitives. |

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
    │       ├── MarkerFactory.x         stateful marker factory
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
    └── test/x/LoggingTest/             70 focused XTC test methods

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
    └── test/x/SLoggingTest/            41 focused XTC test methods
```

---

Next: [`usage/injected-logger-example.md`](usage/injected-logger-example.md) →
