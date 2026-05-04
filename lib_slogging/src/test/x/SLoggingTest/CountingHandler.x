import slogging.Handler;
import slogging.Attr;
import slogging.Level;
import slogging.Record;

/**
 * Test-only handler mirroring `LoggingTest.CountingSink`: counts records by level.
 * This proves that a user-defined slog handler can replace the shipped handlers without
 * changing caller code.
 */
service CountingHandler
        implements Handler {

    public/private Map<Level, Int> counts = new HashMap();

    @Override
    Boolean enabled(Level level) {
        return True;
    }

    @Override
    void handle(Record record) {
        counts.process(record.level, e -> {
            e.value = e.exists ? e.value + 1 : 1;
            return Null;
        });
    }

    @Override
    Handler withAttrs(Attr[] attrs) {
        return attrs.empty ? this : new slogging.BoundHandler(this, attrs);
    }

    @Override
    Handler withGroup(String name) {
        return name == "" ? this : new slogging.BoundHandler(this, name);
    }
}
