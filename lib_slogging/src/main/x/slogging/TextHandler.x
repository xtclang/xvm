/**
 * Corresponds to `log/slog.TextHandler` (`go.dev/src/log/slog/text_handler.go`). The
 * default human-readable handler — emits one line per record in `key=value` format.
 *
 * Format:
 *
 *      2026-04-29T11:23:45.012Z INFO  msg="processed request" requestId=r_42 user=u_3
 *
 * If the record carries an exception, it is rendered on the following line.
 *
 * # Skeleton status
 *
 * The implementation here is intentionally minimal — enough to demonstrate the slog
 * shape end to end (`Logger.with(...)`, attribute folding into namespaced keys, level
 * threshold filtering) but not yet a production formatter. Specifically, no escaping
 * of `=` / `"` in attribute values, no group nesting collapse beyond one level, no
 * timestamp formatting options. Tracked in `open-questions.md` § "lib_slogging work
 * not yet implemented" once the parity table is added.
 *
 * # Why this handler is a `const`, not a `service`
 *
 * Same rule as `lib_logging`'s `ConsoleLogSink`: stateless forwarder over `@Inject
 * Console`. Threshold fixed at construction. No accumulation, no shared mutable state.
 * See `doc/logging/design/design.md` ("Sink type: `const` vs `service`") for the full rule.
 */
const TextHandler(Level rootLevel, String groupPrefix)
        implements Handler {

    /**
     * No-arg convenience: threshold `Info`, no group prefix. See parallel comment on
     * `lib_logging.ConsoleLogSink` for why we use explicit delegating constructors
     * instead of default-arg synthesis.
     */
    construct() {
        construct TextHandler(Level.Info, "");
    }

    /**
     * Single-arg convenience: configurable threshold, no group prefix.
     */
    construct(Level rootLevel) {
        construct TextHandler(rootLevel, "");
    }

    @Inject Console console;

    @Override
    Boolean enabled(Level level) {
        return level.severity >= rootLevel.severity;
    }

    @Override
    void handle(Record record) {
        StringBuffer buf = new StringBuffer();
        buf.append(record.time.toString())
           .append(' ')
           .append(record.level.label.leftJustify(5, ' '))
           .append(' ')
           .append("msg=")
           .append(quote(record.message));

        for (Attr a : record.attrs) {
            buf.append(' ');
            renderAttr(buf, groupPrefix, a);
        }

        console.print(buf.toString());

        if (Exception e ?= record.exception) {
            // TODO(impl): pretty-print stack frames; for now rely on Exception.toString().
            console.print(e.toString());
        }
    }

    @Override
    Handler withAttrs(Attr[] attrs) {
        // Stateless variant: the Logger already carries the attrs list and merges them
        // into every record before calling handle(). A richer handler would pre-resolve
        // here.
        return this;
    }

    @Override
    Handler withGroup(String name) {
        String combined = groupPrefix == "" ? name : $"{groupPrefix}.{name}";
        return new TextHandler(rootLevel, combined);
    }

    /**
     * Render one attr `key=value`, prefixing with the active group path if any. Nested
     * groups (an `Attr` whose value is `Attr[]`) are flattened with a dot separator —
     * matches `slog.TextHandler`.
     */
    private void renderAttr(StringBuffer buf, String prefix, Attr a) {
        String key = prefix == "" ? a.key : $"{prefix}.{a.key}";
        if (a.value.is(Attr[])) {
            Boolean first = True;
            for (Attr child : a.value.as(Attr[])) {
                if (!first) {
                    buf.append(' ');
                }
                first = False;
                renderAttr(buf, key, child);
            }
        } else {
            buf.append(key).append('=').append(a.value.toString());
        }
    }

    private String quote(String s) = $"\"{s}\"";
}
