/**
 * Shared production options for text/JSON handlers.
 *
 * The slog API keeps caller semantics in `Logger`/`Record`/`Attr`; formatting policy
 * belongs in handlers. This const captures the first production knobs without turning
 * the POC into a full configuration system.
 */
const HandlerOptions(
        /**
         * Lowest level a record must have to pass the handler.
         */
        Level    rootLevel,
        /**
         * Attribute keys whose values should be replaced with [redaction].
         */
        String[] redactedKeys,
        /**
         * Replacement value emitted for redacted attributes.
         */
        String   redaction,
        /**
         * True to render `Record.sourceFile` / `sourceLine` when present.
         */
        Boolean  includeSource,
        /**
         * Field name for the timestamp.
         */
        String   timeKey,
        /**
         * Field name for the level label.
         */
        String   levelKey,
        /**
         * Field name for the human message.
         */
        String   messageKey,
        /**
         * Field name for source metadata.
         */
        String   sourceKey,
        /**
         * Field name for structured exception data.
         */
        String   exceptionKey,
        ) {

    construct() {
        construct HandlerOptions(Level.Info, [], "***", True,
                "time", "level", "msg", "source", "exception");
    }

    construct(Level rootLevel) {
        construct HandlerOptions(rootLevel, [], "***", True,
                "time", "level", "msg", "source", "exception");
    }

    construct(Level rootLevel, String[] redactedKeys) {
        construct HandlerOptions(rootLevel, redactedKeys, "***", True,
                "time", "level", "msg", "source", "exception");
    }

    /**
     * True iff the supplied key should be rendered as [redaction].
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
