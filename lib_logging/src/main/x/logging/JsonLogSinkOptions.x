/**
 * Configuration for [JsonLogSink].
 *
 * This is intentionally small but production-oriented: it covers thresholding,
 * field naming, MDC/marker/source inclusion, and key-based redaction without
 * committing the XDK to a full Logback XML/JSON configuration language.
 */
const JsonLogSinkOptions(
        /**
         * Lowest event level accepted by the sink.
         */
        Level    rootLevel,
        /**
         * MDC or key/value keys whose values should be replaced with [redaction].
         */
        String[] redactedKeys,
        /**
         * Replacement value emitted for redacted keys.
         */
        String   redaction,
        /**
         * True to include [LogEvent.mdcSnapshot].
         */
        Boolean  includeMdc,
        /**
         * True to include [LogEvent.markers].
         */
        Boolean  includeMarkers,
        /**
         * True to include fluent `addKeyValue(...)` payloads.
         */
        Boolean  includeKeyValues,
        /**
         * True to include [LogEvent.sourceFile] / [LogEvent.sourceLine] when present.
         */
        Boolean  includeSource,
        /**
         * Field name for the timestamp.
         */
        String   timeKey,
        /**
         * Field name for the level.
         */
        String   levelKey,
        /**
         * Field name for the logger name.
         */
        String   loggerKey,
        /**
         * Field name for the rendered message.
         */
        String   messageKey,
        /**
         * Field name for MDC data.
         */
        String   mdcKey,
        /**
         * Field name for marker names.
         */
        String   markersKey,
        /**
         * Field name for structured exception data.
         */
        String   exceptionKey,
        /**
         * Field name for source metadata.
         */
        String   sourceKey,
        ) {

    /**
     * Default production-safe options.
     */
    construct() {
        construct JsonLogSinkOptions(Info, [], "***",
                True, True, True, True,
                "time", "level", "logger", "message",
                "mdc", "markers", "exception", "source");
    }

    /**
     * Convenience: custom root threshold.
     */
    construct(Level rootLevel) {
        construct JsonLogSinkOptions(rootLevel, [], "***",
                True, True, True, True,
                "time", "level", "logger", "message",
                "mdc", "markers", "exception", "source");
    }

    /**
     * Convenience: custom root threshold and redaction keys.
     */
    construct(Level rootLevel, String[] redactedKeys) {
        construct JsonLogSinkOptions(rootLevel, redactedKeys, "***",
                True, True, True, True,
                "time", "level", "logger", "message",
                "mdc", "markers", "exception", "source");
    }

    /**
     * True iff the supplied key should be replaced with [redaction].
     */
    Boolean redacts(String key) {
        for (String candidate : redactedKeys) {
            if (candidate == key) {
                return True;
            }
        }
        return False;
    }
}
