/**
 * Corresponds, as a whole module, to Go 1.21+'s `log/slog` package — plus a minimum
 * viable handler so the library is usable out of the box. Provided as a parallel design
 * exercise alongside [`logging.xtclang.org`][lib_logging] (the SLF4J-shaped library) so
 * reviewers can compare the two designs side-by-side.
 *
 * Ecstasy structured-logging library, slog-shaped.
 *
 * The module is intentionally shaped to be _instantly familiar_ to anyone who has used
 * `log/slog` in Go: a top-level `Logger` carrying a `Handler` and a list of attached
 * `Attr`s, derived loggers via `Logger.with(attrs)`, no thread-local context (you carry
 * a logger), no "marker" concept (categorisation is just attributes), open-ended
 * integer-based `Level` values.
 *
 * The primary entry point is injection:
 *
 *      @Inject Logger logger;
 *
 * Acquiring loggers without injection is supported by constructing one directly:
 *
 *      Logger logger = new Logger(new TextHandler());
 *
 * Derived loggers are pure construction:
 *
 *      Logger requestLogger = logger.with([
 *              Attr.of("requestId", req.id),
 *              Attr.of("user",      req.userId),
 *      ]);
 *
 * # Status
 *
 * **This module is currently a skeleton** — interfaces, the `Logger` const, and stub
 * handlers (`TextHandler`, `NopHandler`, `MemoryHandler`, `JSONHandler`). Full
 * implementation is gated on reviewer feedback in
 * `doc/logging/LIB_LOGGING_VS_LIB_SLOGGING.md` so we don't sink effort into both shapes
 * before deciding which one Ecstasy should adopt.
 *
 * # API / Implementation boundary
 *
 * The public API consists of:
 *      - [Logger]     — the user-facing concrete `const`
 *      - [Level]      — open-ended severity (Int + String label)
 *      - [Attr]       — single key/value pair carried as structured data
 *      - [Record]     — immutable record of a single log call (LogEvent equivalent)
 *      - [Handler]    — the SPI that backends implement (LogSink equivalent)
 *
 * The implementation side of that boundary contains:
 *      - [TextHandler]   — default human-readable text handler, writes via `@Inject Console`
 *      - [JSONHandler]   — JSON-Lines structured handler (skeleton)
 *      - [NopHandler]    — drops every record
 *      - [MemoryHandler] — captures records in memory; useful in tests
 *
 * # See also
 *
 *      doc/logging/LIB_LOGGING_VS_LIB_SLOGGING.md  — the design comparison document
 *      doc/logging/OPEN_QUESTIONS.md               — list of reviewer questions (Q-D6)
 *      lib_logging/src/main/x/logging.x            — the SLF4J-shaped sibling library
 */
module slogging.xtclang.org {
}
