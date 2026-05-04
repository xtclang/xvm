# lib_logging

Experimental SLF4J-shaped logging library for Ecstasy.

> **Status:** Working POC. The core API, default sinks, MDC, markers, structured
> key/value events, logger interning, runtime `@Inject Logger logger;` wiring, and
> unit tests are in this branch. JIT-side injection, async sinks, and the
> Logback-style backend remain future work; see [`doc/logging/`](../doc/logging).

## What this is

A logging library for Ecstasy that is **instantly familiar** to anyone who has used SLF4J 2.x
in Java: named loggers, levels, parameterized messages with `{}`, exception attachment,
markers, MDC, the SLF4J 2.x fluent event builder. Acquired by injection:

```ecstasy
@Inject Logger logger;
logger.info("processed {} records in {}ms", [count, elapsed]);
```

The default sink writes to the platform `Console`. The `LogSink` SPI is the API↔impl
boundary — drop in a richer sink (file, network, JSON, logback-style configuration tree)
without touching caller code.

## Documentation

All design docs live at the repo root under [`doc/logging/`](../doc/logging):

- [`README.md`](../doc/logging/README.md) — start-here guide and reading paths
- [`api-cross-reference.md`](../doc/logging/api-cross-reference.md) — official SLF4J links mapped
  to each Ecstasy type and the local differences
- [`lib-logging-vs-lib-slogging.md`](../doc/logging/lib-logging-vs-lib-slogging.md) —
  side-by-side comparison with the slog-shaped sibling library
- [`design.md`](../doc/logging/design/design.md) — architecture, module layout, API↔impl boundary
- [`slf4j-parity.md`](../doc/logging/usage/slf4j-parity.md) — every SLF4J 2.x type and method, mapped
- [`ecstasy-vs-java-examples.md`](../doc/logging/usage/ecstasy-vs-java-examples.md) — Java SLF4J
  example, then the same thing in Ecstasy, for every API
- [`custom-sinks.md`](../doc/logging/usage/custom-sinks.md) — guide to writing your own sink
- [`logback-integration.md`](../doc/logging/future/logback-integration.md) — how a future
  logback-style configuration-driven backend would fit
- [`native-bridge.md`](../doc/logging/future/native-bridge.md) — could we plug real Java logging
  libraries in via native code? Investigation and recommendation
- [`open-questions.md`](../doc/logging/open-questions.md) — things still to decide
