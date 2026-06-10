import logging.AsyncLogSink;
import logging.BasicLogger;
import logging.Logger;

/**
 * Tests the Logback-style async wrapper.
 */
class AsyncLogSinkTest {

    @Test
    void shouldDrainQueuedEventsToDelegate() {
        ListLogSink  delegate = new ListLogSink();
        AsyncLogSink async    = new AsyncLogSink(delegate, 10);
        Logger       logger   = new BasicLogger("async.test", async);

        logger.info("queued");
        async.flush();

        assert delegate.events.size == 1;
        assert delegate.events[0].message == "queued";
    }

    @Test
    void shouldDropEventsAfterCloseWithoutFlush() {
        ListLogSink  delegate = new ListLogSink();
        AsyncLogSink async    = new AsyncLogSink(delegate, 10);
        Logger       logger   = new BasicLogger("async.closed", async);

        async.close(flush=False);
        logger.info("dropped");

        assert async.droppedCount == 1;
        assert delegate.events.empty;
    }
}
