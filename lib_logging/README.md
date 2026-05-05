# lib_logging

Experimental SLF4J-shaped logging library for Ecstasy.

> **Status:** Working POC. The core API, default sinks, MDC, markers, structured
> key/value events, logger interning, runtime `@Inject Logger logger;` wiring, and
> unit tests are in this branch. The base backend layer now includes async,
> composite, hierarchical-level, and JSON/redaction sinks. JIT-side injection and
> full configuration-file loading remain future work; see [`doc/logging/`](../doc/logging).

> **POC naming note:** `lib_slogging` exists beside this module only for comparison.
> The XDK should settle on one injectable logging design and ship it as `lib_logging`.

## What this is

A logging library for Ecstasy with the SLF4J 2.x shape: named loggers, levels,
parameterized messages with `{}`, exception attachment, markers, MDC, and the SLF4J
2.x fluent event builder. Acquired by injection:

```ecstasy
@Inject Logger logger;
logger.info("processed {} records in {}ms", [count, elapsed]);
```

The default sink writes to the platform `Console`. The `LogSink` SPI is the API↔impl
boundary — drop in a richer sink (file, network, JSON, logback-style configuration tree)
without touching caller code.

## Documentation

All design docs live at the repo root under [`doc/logging/`](../doc/logging):

| Doc | Purpose |
|---|---|
| [`README.md`](../doc/logging/README.md) | Start-here guide and reading paths. |
| [`lib-logging-vs-lib-slogging.md`](../doc/logging/lib-logging-vs-lib-slogging.md) | Side-by-side comparison with the slog-shaped sibling library. |
| [`api-cross-reference.md`](../doc/logging/api-cross-reference.md) | Official SLF4J links mapped to each Ecstasy type and the local differences. |
| [`design.md`](../doc/logging/design/design.md) | Architecture, module layout, and API/implementation boundary. |
| [`slf4j-parity.md`](../doc/logging/usage/slf4j-parity.md) | SLF4J 2.x type and method mapping. |
| [`ecstasy-vs-java-examples.md`](../doc/logging/usage/ecstasy-vs-java-examples.md) | Java SLF4J examples next to equivalent Ecstasy code. |
| [`custom-sinks.md`](../doc/logging/usage/custom-sinks.md) | Guide to writing a custom sink. |
| [`open-questions.md`](../doc/logging/open-questions.md) | Decision tracker and remaining runtime/compiler/backend follow-up. |
