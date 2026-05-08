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
const TextHandler
        implements Handler {

    /**
     * Create a [TextHandler].
     *
     * @param options    (optional) the handler's options
     * @param groupName  (optional) the handler's group name
     * @param consumer   (optional) the consumer that will process the String produced from the log
     *                   [Record] (the default will print the line to the console)
     */
    construct(HandlerOptions? options = Null, String groupName = "", TextConsumer? consumer = Null) {
        this.options   = options ?: new HandlerOptions();
        this.groupName = groupName;
        this.consumer  = consumer ?: defaultConsumer;
    }

    typedef function void (String) as TextConsumer;

    /**
     * Single-arg convenience: configurable threshold, no group prefix.
     */
    construct(Level rootLevel) {
        construct TextHandler(new HandlerOptions(rootLevel));
    }

    HandlerOptions options;

    String groupName;

    TextConsumer consumer;

    /**
     * Cheap threshold check. This is intentionally only arithmetic on the level's
     * severity; no formatting or attribute walking belongs on the fast path.
     */
    @Override
    Boolean enabled(Level level) = level.enabledAtThreshold(options.rootLevel);

    /**
     * Render a record as one line of text. This is the POC equivalent of Go
     * `slog.TextHandler.Handle`: the message is quoted and attributes are appended as
     * `key=value` pairs.
     */
    @Override
    void handle(Record record) {
        StringBuffer buf = new StringBuffer();
        buf.append(record.timestamp.toString())
           .append(' ')
           .append(record.level.label.leftJustify(5, ' '))
           .append(' ')
           .append(record.message);

        appendAttributes(buf, groupName, record.attributes);
        appendSource(buf, record);

        consumer(buf.toString());

        if (Exception e ?= record.exception) {
            // TODO(impl): pretty-print stack frames; for now rely on Exception.toString().
            consumer(e.toString());
        }
    }

    @Override
    Handler withAttributes(Attributes attributes)
            = attributes.empty ? this : new BoundHandler(delegate=this, attributes=attributes);

    @Override
    Handler withGroup(String name)
            = name.empty ? this : new BoundHandler(delegate=this, groupName=name);

    private static void defaultConsumer(String text) {
        @Inject Console console;
        console.print(text);
    }

    /**
     * Render one attr `key=value`, prefixing with the active group path if any. Nested
     * groups (a value that is itself a `Map<String, AnyValue>`) are flattened with a dot
     * separator — matches `slog.TextHandler`.
     */
    private void renderAttr(StringBuffer buf, String prefix, String key, AnyValue value) {
        String fullKey = prefix.empty ? key : $"{prefix}.{key}";
        if (value.is(Map<String, AnyValue>)) {
            appendNestedAttributes(buf, fullKey, value);
        } else {
            buf.append(fullKey).append('=')
               .append(options.redacts(key) ? options.redaction : value.toString());
        }
    }

    /**
     * Append top-level attributes. Each attr starts with a leading space because the base
     * record fields have already been rendered.
     */
    private void appendAttributes(StringBuffer buf, String prefix, Attributes attributes) {
        for ((String key, AnyValue value) : attributes) {
            buf.append(' ');
            renderAttr(buf, prefix, key, value);
        }
    }

    /**
     * Append children of an attr group. The first child continues in the current
     * position; later children are separated with spaces.
     */
    private void appendNestedAttributes(StringBuffer buf, String prefix, Map<String, AnyValue> attributes) {
        Boolean first = True;
        for ((String key, AnyValue value) : attributes) {
            if (!first) {
                buf.append(' ');
            }
            first = False;
            renderAttr(buf, prefix, key, value);
        }
    }

    /**
     * Append source metadata when caller-supplied source capture is enabled.
     */
    private void appendSource(StringBuffer buf, Record record) {
        if (!options.includeSource) {
            return;
        }

        if (String file ?= record.sourceFile) {
            buf.append(" source=")
               .append(file);
            if (record.sourceLine >= 0) {
                buf.append(':').append(record.sourceLine);
            }
        }
    }
}
