/**
 * Corresponds, as a whole module, to Go 1.21+'s `log/slog` package — plus a minimum
 * viable handler so the library is usable out of the box. Provided as a parallel design
 * exercise alongside [`logging.xtclang.org`][lib_logging] (the SLF4J-shaped library) so
 * reviewers can compare the two designs side-by-side.
 *
 * Ecstasy structured-logging library, slog-shaped.
 *
 * The module is intentionally shaped to be _instantly familiar_ to anyone who has used
 * `log/slog` in Go: a top-level `Logger` carrying a `Handler`, derived loggers via
 * `Logger.with(attrs)`, optional context propagation via `LoggerContext`, no "marker"
 * concept (categorisation is just attributes), open-ended integer-based `Level` values.
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
 * **This module is a working comparison POC.** The core API, level checks, derived
 * loggers, groups, custom levels, lazy message/attr suppliers, runtime injection, source
 * metadata, context binding, and memory/text/JSON/no-op handlers are implemented and
 * covered by unit tests.
 *
 * # API / Implementation boundary
 *
 * The public API consists of:
 *      - [Logger]     — the user-facing concrete `const`
 *      - [Level]      — open-ended severity (Int + String label)
 *      - [Attr]       — single key/value pair carried as structured data
 *      - [Record]     — immutable record of a single log call (LogEvent equivalent)
 *      - [Handler]    — the SPI that backends implement (LogSink equivalent)
 *      - [LoggerContext] — optional SharedContext helper for request-scoped loggers
 *
 * The implementation side of that boundary contains:
 *      - [TextHandler]   — default human-readable text handler, writes via `@Inject Console`
 *      - [JSONHandler]   — JSON-Lines structured handler, rendered by `lib_json`
 *      - [HandlerOptions] — threshold, redaction, source, and field-name options
 *      - [AsyncHandler]  — bounded async wrapper for slow handlers
 *      - [BoundHandler]  — derivation wrapper used by handlers that do not cache prefixes
 *      - [NopHandler]    — drops every record
 *      - [MemoryHandler] — captures records in memory; useful in tests
 *
 * # See also
 *
 *      doc/logging/lib-logging-vs-lib-slogging.md  — the design comparison document
 *      doc/logging/api-cross-reference.md           — official Go slog links mapped to these types
 *      doc/logging/open-questions.md               — list of reviewer questions (Q-D6)
 *      lib_logging/src/main/x/logging.x            — the SLF4J-shaped sibling library
 */
module slogging.xtclang.org {
    package json import json.xtclang.org;

    /**
     * Lazy message supplier used by [Logger].
     *
     * The logger invokes this function only after [Handler.enabled] accepts the record's
     * level. This is the slog-shaped equivalent of Kotlin logging blocks and Java
     * supplier-based logging APIs.
     */
    typedef function String() as MessageSupplier;

    /**
     * Lazy attribute value supplier used by [Attr.lazy].
     *
     * This mirrors Go slog's `LogValuer` idea: expensive structured values can be
     * represented at the call site without constructing them for disabled records.
     */
    typedef function Object() as ObjectSupplier;
}
