/**
 * Corresponds to `log/slog.Record` (`go.dev/src/log/slog/record.go`). The immutable
 * unit of "one log call's worth of data" handed to a [Handler].
 *
 * Equivalent to the SLF4J-shaped library's [logging.LogEvent], but flattened: there is
 * no separate `marker` or `keyValues` — categorisation and structured fields all live
 * in `attrs`.
 *
 * The `attrs` array carries the structured data visible to the current handler call.
 * Derived handlers may prepend attrs or wrap them in groups before forwarding to the
 * final backend. This matches Go slog's `Handler.WithAttrs` / `WithGroup` model.
 *
 * Source-location capture (`sourceFile`, `sourceLine`) is opt-in. The default is
 * `(Null, -1)`, meaning "not captured." [Logger.logAt] populates these fields
 * explicitly; future compiler/runtime sugar can lower call-site metadata into that API.
 */
const Record(
        // Time at which the logger accepted the record. Captured before calling the handler.
        Time      time,
        // Completed human-readable message. Variable data should normally live in attrs.
        String    message,
        // Open integer severity.
        Level     level,
        // Structured data after any handler derivation has been applied.
        Attr[]    attrs,
        // Ecstasy-specific convenience for a thrown exception/cause.
        Exception? exception   = Null,
        // Optional source metadata.
        String?    sourceFile  = Null,
        Int        sourceLine  = -1,
        // Placeholder for future fiber/thread identity support.
        String     threadName  = "",
        );
