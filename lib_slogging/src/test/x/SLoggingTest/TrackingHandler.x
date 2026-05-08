import slogging.Attributes;
import slogging.Handler;
import slogging.Level;
import slogging.Record;

/**
 * Test-only handler that records derivation hooks. It makes the `Handler.withAttributes` /
 * `Handler.withGroup` part of the slog contract observable without depending on a
 * concrete renderer.
 */
service TrackingHandler
        implements Handler {

    public/private Int        withAttributesCalls = 0;
    public/private Int        withGroupCalls = 0;
    public/private Attributes lastAttributes      = Map:[];
    public/private String[]   groups         = [];
    private Record[] recordList = new Record[];

    @RO Record[] records.get() = recordList.toArray(Constant);

    @Override
    Boolean enabled(Level level) = True;

    @Override
    void handle(Record record) = recordList.add(record);

    @Override
    Handler withAttributes(Attributes attributes) {
        ++withAttributesCalls;
        lastAttributes = attributes.makeImmutable();
        return this;
    }

    @Override
    Handler withGroup(String name) {
        ++withGroupCalls;
        groups = (groups + [name]).toArray(Constant);
        return this;
    }
}
