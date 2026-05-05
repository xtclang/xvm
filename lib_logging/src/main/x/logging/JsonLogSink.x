import json.Doc;
import json.JsonArray;
import json.JsonObject;
import json.Printer;

/**
 * Production-oriented JSON-lines sink for the canonical SLF4J-shaped API.
 *
 * It renders through `lib_json`, preserves MDC, markers, structured key/value pairs,
 * exceptions, and explicit source metadata, and supports key-based redaction through
 * [JsonLogSinkOptions].
 */
const JsonLogSink(JsonLogSinkOptions options)
        implements LogSink {

    /**
     * Convenience: Info threshold, all standard fields enabled, no redacted keys.
     */
    construct() {
        construct JsonLogSink(new JsonLogSinkOptions());
    }

    /**
     * Convenience: configure only the root threshold.
     */
    construct(Level rootLevel) {
        construct JsonLogSink(new JsonLogSinkOptions(rootLevel));
    }

    @Inject Console console;

    @Override
    Boolean isEnabled(String loggerName, Level level, Marker? marker = Null) {
        return level.severity >= options.rootLevel.severity;
    }

    @Override
    void log(LogEvent event) {
        console.print(render(event));
    }

    /**
     * Render the event as one compact JSON object.
     */
    String render(LogEvent event) {
        return Printer.DEFAULT.render(toJson(event));
    }

    /**
     * Convert the event into a JSON object. Exposed for tests and custom wrappers.
     */
    JsonObject toJson(LogEvent event) {
        JsonObject obj = json.newObject();
        obj.put(options.timeKey,    event.timestamp.toString());
        obj.put(options.levelKey,   event.level.name);
        obj.put(options.loggerKey,  event.loggerName);
        obj.put(options.messageKey, event.message);

        if (options.includeMarkers && !event.markers.empty) {
            JsonArray markers = json.newArray();
            for (Marker marker : event.markers) {
                markers.add(marker.name);
            }
            obj.put(options.markersKey, markers.toArray(Constant));
        }

        if (options.includeMdc && !event.mdcSnapshot.empty) {
            JsonObject mdc = json.newObject();
            for ((String key, String value) : event.mdcSnapshot) {
                mdc.put(key, redacted(key, value));
            }
            obj.put(options.mdcKey, mdc.makeImmutable());
        }

        if (options.includeKeyValues && !event.keyValues.empty) {
            for ((String key, Object value) : event.keyValues) {
                obj.put(key, redacted(key, value));
            }
        }

        if (Exception e ?= event.exception) {
            obj.put(options.exceptionKey, exceptionJson(e));
        }

        if (options.includeSource) {
            if (String file ?= event.sourceFile) {
                JsonObject source = json.newObject();
                source.put("file", file);
                if (event.sourceLine >= 0) {
                    source.put("line", event.sourceLine.toIntLiteral());
                }
                obj.put(options.sourceKey, source.makeImmutable());
            }
        }

        return obj.makeImmutable();
    }

    private Doc redacted(String key, Object value) {
        return options.redacts(key) ? options.redaction : toJsonValue(value);
    }

    private Doc toJsonValue(Object value) {
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
        if (Exception e := value.is(Exception)) {
            return exceptionJson(e);
        }
        return value.toString();
    }

    private JsonObject exceptionJson(Exception e) {
        JsonObject obj = json.newObject();
        obj.put("type",    &e.class.name);
        obj.put("message", e.message);
        if (Exception cause ?= e.cause) {
            obj.put("cause", exceptionJson(cause));
        }
        return obj.makeImmutable();
    }
}
