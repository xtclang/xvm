import logging.Level;

/**
 * Tests for the `Level` enum and its severity ordering.
 */
class LevelTest {

    @Test
    void shouldOrderBySeverity() {
        assert logging.Level.Trace.severity < logging.Level.Debug.severity;
        assert logging.Level.Debug.severity < logging.Level.Info.severity;
        assert logging.Level.Info.severity  < logging.Level.Warn.severity;
        assert logging.Level.Warn.severity  < logging.Level.Error.severity;
        assert logging.Level.Error.severity < logging.Level.Off.severity;
    }

    @Test
    void shouldEnableEventsAtOrAboveThreshold() {
        assert logging.Level.Info.enabledAtThreshold(logging.Level.Info);
        assert logging.Level.Warn.enabledAtThreshold(logging.Level.Info);
        assert logging.Level.Error.enabledAtThreshold(logging.Level.Info);
    }

    @Test
    void shouldDisableEventsBelowThreshold() {
        assert !logging.Level.Trace.enabledAtThreshold(logging.Level.Info);
        assert !logging.Level.Debug.enabledAtThreshold(logging.Level.Info);
    }

    @Test
    void shouldSilenceEverythingAtOff() {
        assert !logging.Level.Trace.enabledAtThreshold(logging.Level.Off);
        assert !logging.Level.Error.enabledAtThreshold(logging.Level.Off);
    }
}
