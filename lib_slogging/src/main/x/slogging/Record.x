/**
 * Corresponds to `log/slog.Record` (`go.dev/src/log/slog/record.go`). The immutable
 * unit of "one log call's worth of data" handed to a [Handler].
 *
 * Equivalent to the SLF4J-shaped library's [logging.LogEvent], but flattened: there is
 * no separate `marker` or `keyValues` — categorisation and structured fields all live
 * in `attributes`.
 *
 * The `attributes` map carries the structured data visible to the current handler call.
 * Derived handlers may prepend attributes or wrap them in groups before forwarding to the
 * final backend. This matches Go slog's `Handler.WithAttributes` / `WithGroup` model.
 *
 * Source-location capture (`sourceFile`, `sourceLine`) is opt-in. The default is
 * `(Null, -1)`, meaning "not captured." [Logger.logAt] populates these fields
 * explicitly; future compiler/runtime sugar can lower call-site metadata into that API.
 *
 * @param timestamp  the time when the log event occurred as measured by the origin clock
 * @param message    value containing the body of the log record. Can be for example a
 *                   human-readable string message (including multi-line) describing the event in a
 *                   free form or it can be a structured data composed of arrays and maps of other
 *                   values
 * @param level      the severity of the log event
 * @param attributes      additional information about the specific event occurrence. Attributes can vary
 *                   for each occurrence of the event coming from the same source
 * @param exception  optional exception/cause of the log event
 * @param sourceFile optional source file name
 * @param sourceLine optional source line number
 * @param threadName optional thread name
 */
const Record(Time  timestamp,
             AnyValue   message,
             Level      level,
             Attributes attributes  = [],
             Exception? exception   = Null,
             String?    sourceFile  = Null,
             Int        sourceLine  = -1,
             String     threadName  = "");
