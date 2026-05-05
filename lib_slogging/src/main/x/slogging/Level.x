/**
 * Corresponds to `log/slog.Level` (`go.dev/src/log/slog/level.go`). Severity is a plain
 * integer; well-known constants are named (`DEBUG=-4`, `INFO=0`, `WARN=4`, `ERROR=8`)
 * but callers may construct intermediate or beyond-canonical levels.
 *
 * The `slog`-style level model: an open integer line, in contrast to the SLF4J-shaped
 * sibling library's closed `Trace / Debug / Info / Warn / Error / Off` enum. The
 * canonical four levels are spaced four apart so callers can interject custom levels
 * (`Notice` between `Info` and `Warn`, `Critical` past `Error`) without colliding with
 * a library's choices.
 *
 *      Level NOTICE = new Level(2, "NOTICE");
 *      logger.log(NOTICE, "user signed in");
 *
 * For symmetry with the SLF4J library the four canonical names are exposed as `static`
 * constants on this type. Comparisons go through `severity` directly.
 *
 *      if (level.severity >= threshold.severity) { ... }
 */
const Level(Int severity, String label)
        implements Orderable {

    /**
     * Canonical levels. Spacing matches `log/slog`'s `LevelDebug=-4`, `LevelInfo=0`,
     * `LevelWarn=4`, `LevelError=8` — four apart so user-defined levels (e.g. `NOTICE`
     * between `Info` and `Warn`) can be interjected without re-spacing.
     */
    static Level Debug = new Level(-4, "DEBUG");
    static Level Info  = new Level( 0, "INFO");
    static Level Warn  = new Level( 4, "WARN");
    static Level Error = new Level( 8, "ERROR");

    /**
     * `True` iff a record at this level should be emitted given the supplied
     * `threshold`. Matches the SLF4J library's [logging.Level.enabledAtThreshold] for
     * caller convenience.
     */
    Boolean enabledAtThreshold(Level threshold) {
        return severity >= threshold.severity;
    }

    /**
     * Natural ordering is severity ordering, matching Go slog's integer-level model.
     */
    @Override
    static <CompileType extends Level> Ordered compare(CompileType a, CompileType b) {
        return a.severity <=> b.severity;
    }

    /**
     * Human-readable level label used by text/JSON handlers.
     */
    @Override
    String toString() = label;
}
