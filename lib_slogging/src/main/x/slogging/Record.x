/**
 * Corresponds to `log/slog.Record` (`go.dev/src/log/slog/record.go`). The immutable
 * unit of "one log call's worth of data" handed to a [Handler].
 *
 * Equivalent to the SLF4J-shaped library's [logging.LogEvent], but flattened: there is
 * no separate `marker` or `keyValues` — categorisation and structured fields all live
 * in `attrs`.
 *
 * The `attrs` array carries both the "always-on" attributes attached to the source
 * `Logger` (via `Logger.with(...)`) and the per-call extras passed to `info(...)` etc.,
 * concatenated by the `Logger`. Handlers see one flat list.
 *
 * Source-location capture (`sourceFile`, `sourceLine`) is opt-in. The default is
 * `(Null, -1)`, meaning "not captured." A future `Logger` configuration switch will
 * turn it on.
 */
const Record(
        Time      time,
        String    message,
        Level     level,
        Attr[]    attrs,
        Exception? exception   = Null,
        String?    sourceFile  = Null,
        Int        sourceLine  = -1,
        String     threadName  = "",
        );
