/**
 * Corresponds to `log/slog.JSONHandler` (`go.dev/src/log/slog/json_handler.go`). The
 * structured-output handler — emits one JSON object per record on its own line.
 *
 * # Skeleton status
 *
 * Stub implementation only — emits a tiny hand-rolled JSON, not RFC-compliant. Real
 * implementation will use `lib_json` once we wire it as a dependency. Listed here so
 * the comparison document can refer to it as a real type, not just a future name.
 */
const JSONHandler(Level rootLevel, String groupPrefix)
        implements Handler {

    /**
     * No-arg convenience. See parallel comment on `lib_slogging.TextHandler`.
     */
    construct() {
        construct JSONHandler(Level.Info, "");
    }

    /**
     * Single-arg convenience: configurable threshold, no group prefix.
     */
    construct(Level rootLevel) {
        construct JSONHandler(rootLevel, "");
    }

    @Inject Console console;

    @Override
    Boolean enabled(Level level) {
        return level.severity >= rootLevel.severity;
    }

    @Override
    void handle(Record record) {
        // TODO(impl): use lib_json. The skeleton here renders a flat, NOT-rfc-compliant
        // approximation so the comparison document has something to point at. The
        // real implementation will:
        //   - properly escape strings;
        //   - nest groups instead of dot-flattening;
        //   - render timestamps as RFC3339;
        //   - render exception traces structurally.
        StringBuffer buf = new StringBuffer();
        buf.append("{\"time\":\"").append(record.time.toString()).append("\"")
           .append(",\"level\":\"").append(record.level.label).append("\"")
           .append(",\"msg\":\"").append(record.message).append("\"");
        for (Attr a : record.attrs) {
            buf.append(",\"");
            String key = groupPrefix == "" ? a.key : $"{groupPrefix}.{a.key}";
            buf.append(key).append("\":\"").append(a.value.toString()).append("\"");
        }
        buf.append("}");
        console.print(buf.toString());
    }

    @Override
    Handler withAttrs(Attr[] attrs) = this;

    @Override
    Handler withGroup(String name) {
        String combined = groupPrefix == "" ? name : $"{groupPrefix}.{name}";
        return new JSONHandler(rootLevel, combined);
    }
}
