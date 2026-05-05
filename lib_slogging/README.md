# lib_slogging

Experimental Go `log/slog`-shaped structured logging library for Ecstasy.

> **Status:** Working comparison POC. The core `Logger` / `Handler` / `Record` /
> `Attr` / `Level` shape, text/JSON/no-op/memory handlers, derived loggers, groups,
> custom levels, lazy message/attr suppliers, source metadata, `LoggerContext`,
> runtime injection, async handler wrapping, handler options/redaction, and unit tests
> are in this branch.
> `JSONHandler` renders through `lib_json`.

> **POC naming note:** this module exists beside `lib_logging` only so the branch can
> compare two API shapes. The XDK should eventually ship one canonical injectable
> logging library named `lib_logging`; `lib_slogging` is not intended as a permanent
> second default facade.

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

requestLog.debug("payload", [
        Attr.lazy("json", () -> serializer.dump(payload)),
]);
```

There are no markers and no MDC in the base shape. Categorisation, errors, and
structured payload all travel as `Attr` values. Request context is normally explicit
(`logger.with([...])`), with `LoggerContext` available when framework code needs
implicit propagation. Backends implement the small `Handler` interface.

## Documentation

Start with the repo-level docs:

| Doc | Purpose |
|---|---|
| [`doc/logging/README.md`](../doc/logging/README.md) | Start-here guide and reading paths. |
| [`doc/logging/lib-logging-vs-lib-slogging.md`](../doc/logging/lib-logging-vs-lib-slogging.md) | Side-by-side design comparison and reviewer questions. |
| [`doc/logging/api-cross-reference.md`](../doc/logging/api-cross-reference.md) | Official Go `log/slog` links mapped to each Ecstasy type and the local differences. |
| [`doc/logging/usage/slog-parity.md`](../doc/logging/usage/slog-parity.md) | Go `log/slog` method/type mapping and intentional Ecstasy differences. |
| [`doc/logging/usage/lazy-logging.md`](../doc/logging/usage/lazy-logging.md) | Lazy message and attribute construction in both POC APIs. |
| [`doc/logging/usage/custom-handlers.md`](../doc/logging/usage/custom-handlers.md) | Guide to writing custom handlers. |
| [`doc/logging/cloud-integration.md`](../doc/logging/cloud-integration.md) | How the API shape maps to cloud logging systems. |

The source files under [`src/main/x/slogging/`](src/main/x/slogging/) are intentionally
small and each names its Go `log/slog` counterpart in the doc comment.
