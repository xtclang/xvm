import logging.BasicLogger;
import logging.Logger;
import logging.MDC;

/**
 * Tests for [MDC]. Covers the SLF4J-style flat API (`put` / `get` / `remove` / `clear`)
 * and the snapshot-on-emit path that lands MDC contents on `LogEvent.mdcSnapshot`.
 *
 * Per-fiber isolation across spawned services is exercised end-to-end by the manualTests
 * `TestLogger.x` demo; that path needs runtime injection and a spawned service, which is
 * outside the scope of these in-process unit tests.
 *
 * Each test calls `mdc.clear()` at the start because the test harness does not reset MDC
 * state between tests and the underlying `SharedContext` is static.
 */
class MDCTest {

    @Test
    void shouldStartEmpty() {
        @Inject MDC mdc;
        mdc.clear();

        assert mdc.copyOfContextMap.empty;
        assert mdc.get("anything") == Null;
    }

    @Test
    void shouldRoundTripPutAndGet() {
        @Inject MDC mdc;
        mdc.clear();

        mdc.put("requestId", "r_42");
        assert mdc.get("requestId") == "r_42";
    }

    @Test
    void shouldOverwriteOnRepeatedPut() {
        @Inject MDC mdc;
        mdc.clear();

        mdc.put("attempt", "1");
        mdc.put("attempt", "2");
        assert mdc.get("attempt") == "2";
        assert mdc.copyOfContextMap.size == 1;
    }

    @Test
    void shouldRemoveKey() {
        @Inject MDC mdc;
        mdc.clear();

        mdc.put("user", "u_7");
        mdc.put("session", "s_9");
        mdc.remove("user");

        assert mdc.get("user") == Null;
        assert mdc.get("session") == "s_9";
    }

    @Test
    void shouldTreatPutNullAsRemove() {
        @Inject MDC mdc;
        mdc.clear();

        mdc.put("key", "value");
        mdc.put("key", Null);
        assert mdc.get("key") == Null;
    }

    @Test
    void shouldClearAllEntries() {
        @Inject MDC mdc;
        mdc.clear();

        mdc.put("a", "1");
        mdc.put("b", "2");
        mdc.put("c", "3");
        mdc.clear();

        assert mdc.copyOfContextMap.empty;
    }

    @Test
    void shouldAttachSnapshotToLogEvent() {
        @Inject MDC mdc;
        mdc.clear();

        ListLogSink sink   = new ListLogSink();
        Logger      logger = new BasicLogger("mdc.attach", sink);

        mdc.put("requestId", "r_99");
        mdc.put("user",      "u_3");

        logger.info("processing");

        assert sink.events.size == 1;
        assert sink.events[0].mdcSnapshot.size == 2;
        assert sink.events[0].mdcSnapshot.getOrNull("requestId") == "r_99";
        assert sink.events[0].mdcSnapshot.getOrNull("user")      == "u_3";
    }

    @Test
    void shouldSnapshotIndependentlyOfSubsequentMutation() {
        @Inject MDC mdc;
        mdc.clear();

        ListLogSink sink   = new ListLogSink();
        Logger      logger = new BasicLogger("mdc.snap", sink);

        mdc.put("phase", "before");
        logger.info("first");

        mdc.put("phase", "after");
        logger.info("second");

        assert sink.events.size == 2;
        assert sink.events[0].mdcSnapshot.getOrNull("phase") == "before";
        assert sink.events[1].mdcSnapshot.getOrNull("phase") == "after";
    }

    @Test
    void shouldEmitEmptyMapWhenMDCIsClear() {
        @Inject MDC mdc;
        mdc.clear();

        ListLogSink sink   = new ListLogSink();
        Logger      logger = new BasicLogger("mdc.empty", sink);

        logger.info("no context");

        assert sink.events.size == 1;
        assert sink.events[0].mdcSnapshot.empty;
    }
}
