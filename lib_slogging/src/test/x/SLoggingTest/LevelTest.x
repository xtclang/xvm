import slogging.Level;

/**
 * Tests for `Level` — the open-ended integer-severity model.
 *
 * Mirrors `LoggingTest.LevelTest` but exercises the slog-style additions: custom levels
 * between or beyond the canonical four, ordered comparison via `severity`.
 */
class LevelTest {

    @Test
    void shouldOrderCanonicalLevels() {
        assert Level.Debug.severity <  Level.Info.severity;
        assert Level.Info.severity  <  Level.Warn.severity;
        assert Level.Warn.severity  <  Level.Error.severity;
    }

    @Test
    void shouldSupportCustomLevels() {
        // The canonical levels are spaced four apart precisely so callers can interject.
        Level notice   = new Level(2, "NOTICE");
        Level critical = new Level(12, "CRITICAL");

        assert notice.severity   > Level.Info.severity;
        assert notice.severity   < Level.Warn.severity;
        assert critical.severity > Level.Error.severity;
    }

    @Test
    void shouldRespectThresholdSemantics() {
        Level threshold = Level.Warn;
        assert  Level.Error.enabledAtThreshold(threshold);
        assert  Level.Warn.enabledAtThreshold(threshold);
        assert !Level.Info.enabledAtThreshold(threshold);
        assert !Level.Debug.enabledAtThreshold(threshold);
    }

    @Test
    void shouldRenderViaLabel() {
        assert Level.Debug.toString() == "DEBUG";
        assert Level.Info.toString()  == "INFO";
        assert Level.Warn.toString()  == "WARN";
        assert Level.Error.toString() == "ERROR";
    }
}
