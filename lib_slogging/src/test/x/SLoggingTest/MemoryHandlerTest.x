import slogging.Attr;
import slogging.Level;
import slogging.Logger;
import slogging.MemoryHandler;

/**
 * Tests the shipped `MemoryHandler` (the test-helper handler that's part of the
 * library's own surface, parallel to `lib_logging.MemoryLogSink`).
 */
class MemoryHandlerTest {

    @Test
    void shouldCaptureRecordWithAttrs() {
        MemoryHandler handler = new MemoryHandler();
        Logger        logger  = new Logger(handler);

        logger.info("processed", [Attr.of("count", 42)]);

        assert handler.records.size == 1;
        assert handler.records[0].level   == Level.Info;
        assert handler.records[0].message == "processed";
        assert handler.records[0].attrs.size == 1;
        assert handler.records[0].attrs[0].key == "count";
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
