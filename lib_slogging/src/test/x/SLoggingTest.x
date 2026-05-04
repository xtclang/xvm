/**
 * Unit tests for the `slogging.xtclang.org` library — the slog-shaped sibling of
 * `lib_logging`. The structure mirrors `LoggingTest` so a reviewer comparing the two
 * libraries can see equivalent test coverage at the same waterline.
 *
 * The tests build small, in-memory `Handler` implementations (`ListHandler`,
 * `MemoryHandler`) and assert that:
 *   - the level check fast-path elides emission of disabled records;
 *   - the per-level methods (`debug`, `info`, `warn`, `error`) and the open `log(level,
 *     ...)` route correctly;
 *   - exceptions arrive at the handler intact;
 *   - `Logger.with(...)` accumulates always-on attributes that flow into every record;
 *   - `Logger.withGroup(...)` namespaces subsequent attributes;
 *   - `Logger.logAt(...)` populates explicit source metadata;
 *   - `LoggerContext` propagates request-scoped loggers;
 *   - `JSONHandler` renders parseable `lib_json` documents;
 *   - `HandlerContract` checks `withAttrs` / `withGroup` conformance;
 *   - `Attr.group(name, [...])` renders nested structure;
 *   - custom `Level` values comparable to / between the canonical four work.
 */
module SLoggingTest {
    package json     import json.xtclang.org;
    package slogging import slogging.xtclang.org;
    package xunit    import xunit.xtclang.org;
}
