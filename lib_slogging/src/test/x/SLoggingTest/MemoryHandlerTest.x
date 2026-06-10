import slogging.Level;
import slogging.Logger;
import slogging.MemoryHandler;

/**
 * Tests the shipped `MemoryHandler` (the test-helper handler that's part of the
 * library's own surface, parallel to `lib_logging.MemoryLogSink`).
 */
class MemoryHandlerTest {

    @Test
    void shouldCaptureRecordWithAttributes() {
        MemoryHandler handler = new MemoryHandler();
        Logger        logger  = new Logger(handler);

        logger.info("processed", Map:["count"=42]);

        assert handler.records.size == 1;
        assert handler.records[0].level   == Level.Info;
        assert handler.records[0].message == "processed";
        assert handler.records[0].attributes.size == 1;
        assert handler.records[0].attributes.contains("count");
    }

    @Test
    void shouldResetClearsRecords() {
        MemoryHandler handler = new MemoryHandler();
        Logger        logger  = new Logger(handler);

        logger.info("a");
        logger.info("b");
        assert handler.records.size == 2;

        handler.reset();
        assert handler.records.empty;
    }
}
