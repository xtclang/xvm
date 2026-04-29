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
- [`DESIGN.md`](../doc/logging/DESIGN.md) — architecture, module layout, API↔impl boundary
- [`SLF4J_PARITY.md`](../doc/logging/SLF4J_PARITY.md) — every SLF4J 2.x type and method, mapped
- [`ECSTASY_VS_JAVA_EXAMPLES.md`](../doc/logging/ECSTASY_VS_JAVA_EXAMPLES.md) — Java SLF4J
  example, then the same thing in Ecstasy, for every API
- [`CUSTOM_SINKS.md`](../doc/logging/CUSTOM_SINKS.md) — guide to writing your own sink
- [`LOGBACK_INTEGRATION.md`](../doc/logging/LOGBACK_INTEGRATION.md) — how a future
  logback-style configuration-driven backend would fit
- [`NATIVE_BRIDGE.md`](../doc/logging/NATIVE_BRIDGE.md) — could we plug real Java logging
  libraries in via native code? Investigation and recommendation
- [`OPEN_QUESTIONS.md`](../doc/logging/OPEN_QUESTIONS.md) — things still to decide
