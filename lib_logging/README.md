# lib_logging

Experimental SLF4J-shaped logging library for Ecstasy.

> **Status:** Stub / research branch. The API is intended to be the long-lived shape;
> implementations and the runtime injection plumbing are not wired up yet. See the design
> docs under [`doc/logging/`](../doc/logging) at the repo root.

## What this is

A logging library for Ecstasy that is **instantly familiar** to anyone who has used SLF4J 2.x
in Java: named loggers, levels, parameterized messages with `{}`, exception attachment,
markers, MDC, the SLF4J 2.x fluent event builder. Acquired by injection:

```ecstasy
@Inject Logger logger;
logger.info("processed {} records in {}ms", count, elapsed);
```

The default sink writes to the platform `Console`. The `LogSink` SPI is the API↔impl
boundary — drop in a richer sink (file, network, JSON, logback-style configuration tree)
without touching caller code.

## Documentation

All design docs live at the repo root under [`doc/logging/`](../doc/logging):

- [`PLAN.md`](../doc/logging/PLAN.md) — master plan, scope, ordering of work
- [`design.md`](../doc/logging/design.md) — architecture, module layout, API↔impl boundary
- [`slf4j-parity.md`](../doc/logging/slf4j-parity.md) — every SLF4J 2.x type and method, mapped
- [`ecstasy-vs-java-examples.md`](../doc/logging/ecstasy-vs-java-examples.md) — Java SLF4J
  example, then the same thing in Ecstasy, for every API
- [`custom-sinks.md`](../doc/logging/custom-sinks.md) — guide to writing your own sink
- [`logback-integration.md`](../doc/logging/logback-integration.md) — how a future
  logback-style configuration-driven backend would fit
- [`native-bridge.md`](../doc/logging/native-bridge.md) — could we plug real Java logging
  libraries in via native code? Investigation and recommendation
- [`open-questions.md`](../doc/logging/open-questions.md) — things still to decide
