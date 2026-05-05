import json.Doc;
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
const JSONHandler(HandlerOptions options, String groupPrefix)
        implements Handler {

    /**
     * No-arg convenience. See parallel comment on `lib_slogging.TextHandler`.
     */
    construct() {
        construct JSONHandler(new HandlerOptions(), "");
    }

    /**
     * Single-arg convenience: configurable threshold, no group prefix.
     */
    construct(Level rootLevel) {
        construct JSONHandler(new HandlerOptions(rootLevel), "");
    }

    /**
     * Production-options convenience.
     */
    construct(HandlerOptions options) {
        construct JSONHandler(options, "");
    }

    @Inject Console console;

    /**
     * Cheap threshold check; no JSON work happens for disabled records.
     */
    @Override
    Boolean enabled(Level level) {
        return level.severity >= options.rootLevel.severity;
    }

    /**
     * Render and print one JSON line.
     */
    @Override
    void handle(Record record) {
        console.print(render(record));
    }

    /**
     * Render a record to a compact JSON string. Exposed so tests and custom handlers can
     * verify the JSON shape without intercepting `Console`.
     */
    String render(Record record) {
        return Printer.DEFAULT.render(toJson(record));
    }

    /**
     * Convert a record into a JSON document. This is the handler's semantic core; `handle`
     * only prints the rendered document.
     */
    JsonObject toJson(Record record) {
        JsonObject obj = json.newObject();
        obj.put(options.timeKey,    record.time.toString());
        obj.put(options.levelKey,   record.level.label);
        obj.put(options.messageKey, record.message);

        JsonObject attrTarget = groupPrefix == "" ? obj : ensureObject(obj, groupPrefix);
        addAttrs(attrTarget, record.attrs);

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

        if (record.threadName != "") {
            obj.put("thread", record.threadName);
        }

        return obj.makeImmutable();
    }

    /**
     * Return a handler with pre-bound attrs. This implementation uses [BoundHandler];
     * a lower-level production sink could override this method to cache a serialized
     * prefix instead.
     */
    @Override
    Handler withAttrs(Attr[] attrs) {
        return attrs.empty ? this : new BoundHandler(this, attrs);
    }

    /**
     * Return a handler that nests subsequent attrs under `name`.
     */
    @Override
    Handler withGroup(String name) {
        return name == "" ? this : new BoundHandler(this, name);
    }

    /**
     * Add all attrs into a JSON object, preserving slog groups as nested objects.
     */
    private void addAttrs(JsonObject obj, Attr[] attrs) {
        for (Attr a : attrs) {
            obj.put(a.key, options.redacts(a.key) ? options.redaction : attrValue(a.value));
        }
    }

    /**
     * Convert an attribute value to a JSON document.
     */
    private Doc attrValue(Object value) {
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
        if (Attr[] attrs := value.is(Attr[])) {
            JsonObject nested = json.newObject();
            addAttrs(nested, attrs);
            return nested.makeImmutable();
        }
        if (Exception e := value.is(Exception)) {
            return exceptionJson(e);
        }

        return value.toString();
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
     * Ensure a nested object path exists. Used only for the compatibility `groupPrefix`
     * constructor; normal grouping flows through [BoundHandler] as `Attr.group(...)`.
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
