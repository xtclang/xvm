import convert.formats.Base64Format;

import json.Doc;
import json.JsonArray;
import json.JsonObject;
import json.Printer;

/**
 * Corresponds to `log/slog.JSONHandler` (`go.dev/src/log/slog/json_handler.go`). The
 * structured-output handler — emits one compact JSON object per record on its own line.
 *
 * Rendering is delegated to `lib_json`, so strings are escaped by the platform JSON
 * printer rather than by ad-hoc concatenation. Groups are preserved as nested objects,
 * source metadata is emitted under `"source"`, and exceptions are represented
 * structurally.
 */
const JSONHandler
        implements Handler {

    /**
     * Create a [JSONHandler].
     *
     * @param options    (optional) the handler's options
     * @param groupName  (optional) the handler's group name
     * @param consumer   (optional) the consumer that will process the [JsonObject] produced from
     *                   the log [Record] (the default will print the json to the console)
     */
    construct(HandlerOptions? options = Null, String groupName = "", JsonConsumer? consumer = Null) {
        this.options   = options ?: new HandlerOptions();
        this.groupName = groupName;
        this.consumer  = consumer ?: defaultConsumer;
    }

    typedef function void (JsonObject) as JsonConsumer;

    HandlerOptions options;

    String groupName;

    JsonConsumer consumer;

    /**
     * Cheap threshold check; no JSON work happens for disabled records.
     */
    @Override
    Boolean enabled(Level level) = level.enabledAtThreshold(options.rootLevel);

    /**
     * Render and print one JSON line.
     */
    @Override
    void handle(Record record) = consumer(toJson(record));

    private static void defaultConsumer(JsonObject obj) {
        @Inject Console console;
        console.print(Printer.DEFAULT.render(obj));
    }

    /**
     * Convert a record into a JSON document. This is the handler's semantic core; `handle`
     * only prints the rendered document.
     */
    JsonObject toJson(Record record) {
        JsonObject obj   = json.newObject();
        IntLiteral nanos = (record.timestamp.epochPicos / 1000).toIntLiteral(); // epoch nanos
        obj.put(options.timeKey,    nanos);
        obj.put(options.levelKey,   record.level.label);
        obj.put(options.messageKey, encodeAnyValue(record.message));

        JsonObject attrTarget = groupName.empty ? obj : ensureObject(obj, groupName);
        addAttributes(attrTarget, record.attributes);

        if (Exception e ?= record.exception) {
            obj.put(options.exceptionKey, exceptionJson(e));
        }

        if (options.includeSource) {
            if (String file ?= record.sourceFile) {
                JsonObject source = json.newObject();
                source.put("file", file);
                if (record.sourceLine >= 0) {
                    source.put("line", record.sourceLine.toIntLiteral());
                }
                obj.put(options.sourceKey, source.makeImmutable());
            }
        }

        if (!record.threadName.empty) {
            obj.put("thread", record.threadName);
        }

        return obj.makeImmutable();
    }

    /**
     * Return a handler with pre-bound attributes. This implementation uses [BoundHandler];
     * a lower-level production sink could override this method to cache a serialized
     * prefix instead.
     */
    @Override
    Handler withAttributes(Attributes attributes)
            = attributes.empty ? this : new BoundHandler(delegate=this, attributes=attributes);

    /**
     * Return a handler that nests subsequent attributes under `name`.
     */
    @Override
    Handler withGroup(String name)
            = name.empty ? this : new BoundHandler(delegate=this, groupName=name);

    /**
     * Add all attributes into a JSON object, preserving slog groups as nested objects.
     */
    private void addAttributes(JsonObject obj, Attributes attributes) {
        for ((String key, AnyValue value) : attributes) {
            obj.put(key, options.redacts(key) ? options.redaction : encodeAnyValue(value));
        }
    }

    /**
     * Convert an [AnyValue] value to a JSON document.
     */
    private Doc encodeAnyValue(AnyValue value) {
        if (String s := value.is(String)) {
            return s;
        }
        if (Boolean b := value.is(Boolean)) {
            return b;
        }
        if (IntLiteral n := value.is(IntLiteral)) {
            return n;
        }
        if (FPLiteral n := value.is(FPLiteral)) {
            return n;
        }
        if (IntNumber n := value.is(IntNumber)) {
            return n.toIntLiteral();
        }
        if (FPNumber n := value.is(FPNumber)) {
            return n.toFPLiteral();
        }
        if (value.is(Byte[])) {
            return Base64Format.Instance.encode(value);
        }
        if (value.is(AnyValue[])) {
            JsonArray array = json.newArray();
            for (AnyValue element : value) {
                array.add(encodeAnyValue(element));
            }
            return array;
        }
        if (value.is(Map<String, AnyValue>)) {
            JsonObject entries = json.newObject();
            for ((String k, AnyValue v) : value) {
                entries.put(k, encodeAnyValue(v));
            }
            return entries;
        }
        assert as $"Unhandled AnyValue to JSON conversion {&value.type}";
    }

    /**
     * Represent an exception structurally. Stack frames are intentionally omitted from
     * the POC because `Exception.formatStackTrace()` is still a TODO in `lib_ecstasy`.
     */
    private JsonObject exceptionJson(Exception e) {
        JsonObject obj = json.newObject();
        obj.put("type",    &e.class.name);
        obj.put("message", e.message);
        if (Exception cause ?= e.cause) {
            obj.put("cause", exceptionJson(cause));
        }
        return obj.makeImmutable();
    }

    /**
     * Ensure a nested object path exists. Used only for the compatibility `groupName`
     * constructor; normal grouping flows through [BoundHandler] as nested attribute maps.
     */
    private JsonObject ensureObject(JsonObject root, String path) {
        JsonObject target = root;
        for (String part : path.split('.')) {
            if (Doc existing := target.get(part), existing.is(JsonObject)) {
                target = existing.as(JsonObject);
            } else {
                JsonObject child = json.newObject();
                target.put(part, child);
                target = child;
            }
        }
        return target;
    }
}
