import slogging.Attr;
import slogging.Handler;
import slogging.Level;
import slogging.Record;

/**
 * Test-only handler that records derivation hooks. It makes the `Handler.withAttrs` /
 * `Handler.withGroup` part of the slog contract observable without depending on a
 * concrete renderer.
 */
service TrackingHandler
        implements Handler {

    public/private Int      withAttrsCalls = 0;
    public/private Int      withGroupCalls = 0;
    public/private Attr[]   lastAttrs      = [];
    public/private String[] groups         = [];
    private Record[] recordList = new Record[];

    @RO Record[] records.get() {
        return recordList.toArray(Constant);
    }

    @Override
    Boolean enabled(Level level) {
        return True;
    }

    @Override
    void handle(Record record) {
        recordList.add(record);
    }

    @Override
    Handler withAttrs(Attr[] attrs) {
        ++withAttrsCalls;
        lastAttrs = attrs.toArray(Constant);
        return this;
    }

    @Override
    Handler withGroup(String name) {
        ++withGroupCalls;
        groups = (groups + [name]).toArray(Constant);
        return this;
    }
}
