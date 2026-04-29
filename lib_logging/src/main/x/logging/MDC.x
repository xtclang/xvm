/**
 * Corresponds to `org.slf4j.MDC` (and the underlying `org.slf4j.spi.MDCAdapter`).
 * Logback's `LogbackMDCAdapter` and Log4j 2's `ThreadContext` cover the same concept.
 *
 * Mapped Diagnostic Context — a per-fiber/thread string-keyed scratchpad that sinks can include
 * in formatted output. Mirrors `org.slf4j.MDC`.
 *
 * The default implementation provided by this module is a no-op; values are stored but never
 * read by `ConsoleLogSink`. Sinks that want MDC-aware output (a logback-style backend would)
 * read `copyOfContextMap` on each event.
 *
 * Note: the storage is "context-local" in the Ecstasy sense — each fiber sees its own map.
 * The exact mechanism is a runtime detail; see `docs/OPEN_QUESTIONS.md` for the design choice.
 */
service MDC {

    private Map<String, String> map = new HashMap();

    /**
     * Store a value under `key`. Setting `Null` removes the key.
     */
    void put(String key, String? value) {
        if (value == Null) {
            map.remove(key);
        } else {
            map.put(key, value);
        }
    }

    /**
     * Read the value for `key`, or `Null` if no value is set.
     */
    String? get(String key) {
        return map.getOrNull(key);
    }

    /**
     * Remove `key` from the context.
     */
    void remove(String key) {
        map.remove(key);
    }

    /**
     * Remove all entries.
     */
    void clear() {
        map.clear();
    }

    /**
     * A snapshot of the current context map. Sinks call this when emitting an event, so the
     * captured state is independent of any subsequent mutation by the caller. The snapshot is
     * a fresh `HashMap`; callers should treat it as read-only.
     */
    @RO Map<String, String> copyOfContextMap.get() {
        HashMap<String, String> snapshot = new HashMap<String, String>();
        for ((String k, String v) : map) {
            snapshot.put(k, v);
        }
        return snapshot;
    }
}
