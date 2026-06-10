import slogging.Attributes;
import slogging.BoundHandler;
import slogging.Handler;
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
    Boolean enabled(Level level) = True;

    @Override
    void handle(Record record) {
        counts.process(record.level, e -> {
            e.value = e.exists ? e.value + 1 : 1;
            return Null;
        });
    }

    @Override
    Handler withAttributes(Attributes attributes)
            = attributes.empty ? this : new BoundHandler(delegate=this, attributes=attributes);

    @Override
    Handler withGroup(String name)
            = name.empty ? this : new BoundHandler(delegate=this, groupName=name);
}
