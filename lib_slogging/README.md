# lib_slogging

Go `log/slog`-shaped structured logging library for Ecstasy. Comparison POC
that lives beside [`lib_logging`](../lib_logging) so reviewers can pick between
two API shapes:

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

Attribute-first events: no markers, no MDC, no `{}` interpolation in the base
shape. Categorisation, errors, and structured payload all travel as `Attr`
values. Backends implement the small `Handler` interface.

> **POC name.** This module exists only to compare the slog shape against the
> SLF4J shape. Whichever wins ships as `lib_logging`; this module is not
> intended as a permanent second facade.

## Documentation

All design docs live at the repo root under [`doc/logging/`](../doc/logging).
Start at [`doc/logging/README.md`](../doc/logging/README.md) for goals,
requirements, status, reading paths, and the full doc index.
