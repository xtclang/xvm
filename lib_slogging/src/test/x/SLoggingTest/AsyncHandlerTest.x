import slogging.AsyncHandler;
import slogging.Logger;

/**
 * Tests the slog-shaped async handler wrapper.
 */
class AsyncHandlerTest {

    @Test
    void shouldDrainQueuedRecordsToDelegate() {
        ListHandler  delegate = new ListHandler();
        AsyncHandler async    = new AsyncHandler(delegate, 10);
        Logger       logger   = new Logger(async);

        logger.info("queued");
        async.flush();

        assert delegate.records.size == 1;
        assert delegate.records[0].message == "queued";
    }

    @Test
    void shouldDropRecordsAfterCloseWithoutFlush() {
        ListHandler  delegate = new ListHandler();
        AsyncHandler async    = new AsyncHandler(delegate, 10);
        Logger       logger   = new Logger(async);

        async.close(flush=False);
        logger.info("dropped");

        assert async.droppedCount == 1;
        assert delegate.records.empty;
    }
}
