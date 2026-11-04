/**
 * An XUnit ExecutionListener that reports the execution as events for tools such as IDEs. Each
 * event is a line of test process output: [Marker] followed by a JSON object, interleaved with
 * output from the tests. Each object's `event` identifies the event:
 *
 * - `run` comes first, with the `version` of the events' format, which stays the same while the
 *   format only gains events and fields; readers ignore unknown events and fields;
 * - `started`: the execution of a test or container started. Its `id` is the path of the model's
 *   unique id, `parent` the `id` of its nearest active ancestor, absent for the root, `name` its
 *   display name, `selector` its [Model.selector], and `container` whether it holds tests, as a
 *   module, package, class or templated test does;
 * - `finished`: the execution of the model `id` ended, with the `status` `passed`, `failed`,
 *   `aborted` (an exception other than a failed assertion) or `skipped`, after `durationMillis`;
 *   when an exception is present, `message` and `details` describe it. A container's `status`
 *   is that of its own execution, such as its `@BeforeAll` and `@AfterAll`; its tests are in
 *   their own events;
 * - `skipped`: a test or container that did not run, with the same fields as `started` and the
 *   reason as `message`.
 */
service JsonExecutionListener
        implements ExecutionListener {

    /**
     * Prefix for each JSON event line.
     */
    static String Marker = "##xunit ";

    /**
     * The version of the events' format.
     */
    static Int Version = 1;

    @Inject Console console;

    /**
     * Whether the `run` event, which comes first, has been printed.
     */
    private Boolean begun = False;

    /**
     * IDs of active tests and containers, used to find each model's nearest active ancestor.
     */
    private Set<String> running = new HashSet();

    @Override
    void onStarted(Model model) {
        StringBuffer buf = begin("started");
        describe(buf, model);
        end(buf);
        running.add(model.uniqueId.path);
    }

    @Override
    void onCompleted(Model model, Result result) {
        running.remove(model.uniqueId.path);
        StringBuffer buf = begin("finished");
        field(buf, "id", model.uniqueId.path);
        field(buf, "status", status(result.status));
        Exception? ex = result.exception;
        if (ex.is(Exception)) {
            field(buf, "message", ex.message);
            field(buf, "details", ex.toString());
        }
        buf.append(",\"durationMillis\":").append(result.duration.milliseconds.toString());
        end(buf);
    }

    @Override
    void onSkipped(Model model, String reason) {
        StringBuffer buf = begin("skipped");
        describe(buf, model);
        field(buf, "message", reason);
        end(buf);
    }

    /**
     * Start an event, emitting the `run` header first if needed.
     */
    private StringBuffer begin(String event) {
        if (!begun) {
            begun = True;
            StringBuffer run = new StringBuffer();
            run.append(Marker).append("{\"event\":\"run\",\"version\":").append(Version.toString());
            end(run);
        }
        StringBuffer buf = new StringBuffer();
        buf.append(Marker).append("{\"event\":");
        quote(buf, event);
        return buf;
    }

    /**
     * Finish and print an event line.
     */
    private void end(StringBuffer buf) {
        buf.append('}');
        console.print(buf.toString());
    }

    /**
     * Append the common fields of a `started` or `skipped` event.
     */
    private void describe(StringBuffer buf, Model model) {
        field(buf, "id", model.uniqueId.path);
        if (String parent := parentOf(model)) {
            field(buf, "parent", parent);
        }
        field(buf, "name", model.displayName);
        field(buf, "selector", model.selector);
        buf.append(",\"container\":").append(model.isContainer ? "true" : "false");
    }

    /**
     * @return the nearest active ancestor's ID; a template iteration may skip levels in the
     *         unique-ID hierarchy
     */
    private conditional String parentOf(Model model) {
        UniqueId id = model.uniqueId;
        while (UniqueId parent := id.parent()) {
            if (running.contains(parent.path)) {
                return True, parent.path;
            }
            id = parent;
        }
        return False;
    }

    /**
     * @return the serialized name of a result status
     */
    private static String status(Result.Status status) {
        switch (status) {
        case Successful:
            return "passed";

        case Aborted:
            return "aborted";

        case Failed:
            return "failed";

        case Skipped:
            return "skipped";
        }
    }

    /**
     * Append a field with a string value to an event's object.
     */
    private static void field(StringBuffer buf, String name, String value) {
        buf.append(",\"").append(name).append("\":");
        quote(buf, value);
    }

    /**
     * Append a JSON-escaped string value.
     */
    private static void quote(StringBuffer buf, String value) {
        buf.append('"');
        for (Char ch : value) {
            switch (ch) {
            case '"':
                buf.append("\\\"");
                break;

            case '\\':
                buf.append("\\\\");
                break;

            case '\n':
                buf.append("\\n");
                break;

            case '\r':
                buf.append("\\r");
                break;

            case '\t':
                buf.append("\\t");
                break;

            default:
                if (ch < ' ') {
                    Int code = ch.toInt();
                    buf.append("\\u00").append(Hex[code >> 4]).append(Hex[code & 0xF]);
                } else {
                    buf.append(ch);
                }
                break;
            }
        }
        buf.append('"');
    }

    /**
     * The hexadecimal digits.
     */
    private static String Hex = "0123456789abcdef";
}
