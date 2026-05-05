import slogging.Attr;
import slogging.Level;
import slogging.Logger;

/**
 * Tests for the per-level emission methods. Mirrors `LoggingTest.EmissionTest`.
 */
class EmissionTest {

    @Test
    void shouldRouteInfo() {
        ListHandler handler = new ListHandler();
        Logger      logger  = new Logger(handler);

        logger.info("hello", [Attr.of("count", 1)]);

        assert handler.records.size == 1;
        assert handler.records[0].level   == Level.Info;
        assert handler.records[0].message == "hello";
        assert handler.records[0].attrs.size == 1;
        assert handler.records[0].attrs[0].key == "count";
    }

    @Test
    void shouldRouteAllLevels() {
        ListHandler handler = new ListHandler();
        Logger      logger  = new Logger(handler);

        logger.debug("d");
        logger.info ("i");
        logger.warn ("w");
        logger.error("e");

        assert handler.records.size == 4;
        assert handler.records[0].level == Level.Debug;
        assert handler.records[1].level == Level.Info;
        assert handler.records[2].level == Level.Warn;
        assert handler.records[3].level == Level.Error;
    }

    @Test
    void shouldCarryException() {
        ListHandler handler = new ListHandler();
        Logger      logger  = new Logger(handler);
        Exception   boom    = new Exception("boom");

        logger.error("failed", [Attr.of("status", 500)], cause=boom);

        assert handler.records.size == 1;
        assert handler.records[0].exception?.text == "boom" : assert;
    }

    @Test
    void shouldRouteCustomLevelThroughLog() {
        ListHandler handler = new ListHandler();
        Logger      logger  = new Logger(handler);
        Level       notice  = new Level(2, "NOTICE");

        logger.log(notice, "user signed in");

        assert handler.records.size == 1;
        assert handler.records[0].level == notice;
        assert handler.records[0].message == "user signed in";
    }
}
