# lib_logging

SLF4J 2.x-shaped injectable logging library for Ecstasy. Acquired by injection:

```ecstasy
@Inject Logger logger;
logger.info("processed {} records in {}ms", [count, elapsed]);
```

Named loggers, levels, parameterized `{}` messages, exception attachment,
markers, MDC, and the SLF4J 2.x fluent event builder. The default
`ConsoleLogSink` writes to the platform `Console`; richer sinks (JSON, async,
fanout, hierarchical-level, file/network) plug in behind the same API.

## Documentation

All design docs live at the repo root under [`doc/logging/`](../doc/logging).
Start at [`doc/logging/README.md`](../doc/logging/README.md) — it carries the
goals, requirements, status, reading paths, and the full doc index.
