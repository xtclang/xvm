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
 * # Why this handler is a `const`, not a `service`
 *
 * Same rule as `lib_logging`'s `ConsoleLogSink`: stateless forwarder over `@Inject
 * Console`. Threshold fixed at construction. No accumulation, no shared mutable state.
 * See `doc/logging/design/design.md` ("Sink type: `const` vs `service`") for the full rule.
 */
const TextHandler(HandlerOptions options, String groupPrefix)
        implements Handler {

    /**
     * No-arg convenience: threshold `Info`, no group prefix. See parallel comment on
     * `lib_logging.ConsoleLogSink` for why we use explicit delegating constructors
     * instead of default-arg synthesis.
     */
    construct() {
        construct TextHandler(new HandlerOptions(), "");
    }

    /**
     * Single-arg convenience: configurable threshold, no group prefix.
     */
    construct(Level rootLevel) {
        construct TextHandler(new HandlerOptions(rootLevel), "");
    }

    /**
     * Production-options convenience.
     */
    construct(HandlerOptions options) {
        construct TextHandler(options, "");
    }

    @Inject Console console;

    /**
     * Cheap threshold check. This is intentionally only arithmetic on the level's
     * severity; no formatting or attribute walking belongs on the fast path.
     */
    @Override
    Boolean enabled(Level level) {
        return level.severity >= options.rootLevel.severity;
    }

    /**
     * Render a record as one line of text. This is the POC equivalent of Go
     * `slog.TextHandler.Handle`: the message is quoted and attrs are appended as
     * `key=value` pairs.
     */
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

        if (options.includeSource) {
            if (String file ?= record.sourceFile) {
                buf.append(" source=")
                   .append(file);
                if (record.sourceLine >= 0) {
                    buf.append(':').append(record.sourceLine);
                }
            }
        }

        console.print(buf.toString());

        if (Exception e ?= record.exception) {
            // TODO(impl): pretty-print stack frames; for now rely on Exception.toString().
            console.print(e.toString());
        }
    }

    @Override
    Handler withAttrs(Attr[] attrs) {
        return attrs.empty ? this : new BoundHandler(this, attrs);
    }

    @Override
    Handler withGroup(String name) {
        return name == "" ? this : new BoundHandler(this, name);
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
            buf.append(key).append('=')
               .append(options.redacts(a.key) ? options.redaction : a.value.toString());
        }
    }

    /**
     * POC string quoting. Production text output should escape quotes, newlines, and
     * separators; that belongs with the production handler, not the API sketch.
     */
    private String quote(String s) = $"\"{s}\"";
}
