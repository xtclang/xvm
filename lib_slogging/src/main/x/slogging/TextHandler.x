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
     * Create a [JSONHandler].
     *
     * @param options    (optional) the handler's options
     * @param groupName  (optional) the handler's group name
     * @param handler    (optional) the consumer that will process the String produced from the log
     *                   [Record] (the default will print the json to the console)
     */
    construct (HandlerOptions? options = Null, String groupName = "", TextConsumer? consumer = Null)
    {
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

        appendAttrs(buf, groupName, record.attrs);
        appendSource(buf, record);

        consumer(buf.toString());

        if (Exception e ?= record.exception) {
            // TODO(impl): pretty-print stack frames; for now rely on Exception.toString().
            consumer(e.toString());
        }
    }

    @Override
    Handler withAttrs(Attributes attrs) {
        return attrs.empty ? this : new BoundHandler(delegate=this, attrs=attrs);
    }

    @Override
    Handler withGroup(String name) {
        return name == "" ? this : new BoundHandler(delegate=this, groupName=name);
    }

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
        String fullKey = prefix == "" ? key : $"{prefix}.{key}";
        if (value.is(Map<String, AnyValue>)) {
            appendNestedAttrs(buf, fullKey, value);
        } else {
            buf.append(fullKey).append('=')
               .append(options.redacts(key) ? options.redaction : value.toString());
        }
    }

    /**
     * Append top-level attrs. Each attr starts with a leading space because the base
     * record fields have already been rendered.
     */
    private void appendAttrs(StringBuffer buf, String prefix, Attributes attrs) {
        for ((String key, AnyValue value) : attrs) {
            buf.append(' ');
            renderAttr(buf, prefix, key, value);
        }
    }

    /**
     * Append children of an attr group. The first child continues in the current
     * position; later children are separated with spaces.
     */
    private void appendNestedAttrs(StringBuffer buf, String prefix, Map<String, AnyValue> attrs) {
        Boolean first = True;
        for ((String key, AnyValue value) : attrs) {
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

    /**
     * POC string quoting. Production text output should escape quotes, newlines, and
     * separators; that belongs with the production handler, not the API sketch.
     */
    private String quote(String s) = $"\"{s}\"";
}
