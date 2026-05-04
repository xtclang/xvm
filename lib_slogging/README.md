# lib_slogging

Experimental Go `log/slog`-shaped structured logging library for Ecstasy.

> **Status:** Working comparison POC. The core `Logger` / `Handler` / `Record` /
> `Attr` / `Level` shape, text/JSON/no-op/memory handlers, derived loggers, groups,
> custom levels, source metadata, `LoggerContext`, runtime injection, and unit tests
> are in this branch. `JSONHandler` renders through `lib_json`.

## What this is

`lib_slogging` is the attribute-first alternative to the SLF4J-shaped
[`lib_logging`](../lib_logging). It is familiar to Go engineers:

```ecstasy
Logger logger = new Logger(new TextHandler());

Logger requestLog = logger.with([
        Attr.of("requestId", req.id),
        Attr.of("user",      req.userId),
]);

requestLog.info("payment processed", [
        Attr.of("amount",   amount),
        Attr.of("currency", currency),
]);
```

There are no markers and no MDC in the base shape. Categorisation, errors, and
structured payload all travel as `Attr` values. Request context is normally explicit
(`logger.with([...])`), with `LoggerContext` available when framework code needs
implicit propagation. Backends implement the small `Handler` interface.

## Documentation

Start with the repo-level docs:

- [`doc/logging/README.md`](../doc/logging/README.md) — start-here guide and reading paths
- [`doc/logging/api-cross-reference.md`](../doc/logging/api-cross-reference.md) — official Go
  `log/slog` links mapped to each Ecstasy type and the local differences
- [`doc/logging/lib-logging-vs-lib-slogging.md`](../doc/logging/lib-logging-vs-lib-slogging.md) —
  side-by-side design comparison and reviewer questions
- [`doc/logging/usage/slog-parity.md`](../doc/logging/usage/slog-parity.md) — Go `log/slog`
  method/type mapping and intentional Ecstasy differences
- [`doc/logging/usage/custom-handlers.md`](../doc/logging/usage/custom-handlers.md) — guide to
  writing custom handlers
- [`doc/logging/cloud-integration.md`](../doc/logging/cloud-integration.md) — why both
  shapes map cleanly to cloud logging systems

The source files under [`src/main/x/slogging/`](src/main/x/slogging/) are intentionally
small and each names its Go `log/slog` counterpart in the doc comment.
