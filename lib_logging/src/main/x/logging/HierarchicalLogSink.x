/**
 * Logback-style per-logger threshold wrapper.
 *
 * The sink keeps a mutable map of logger-name prefixes to levels. The longest matching
 * prefix wins; when no prefix matches, [rootLevel] applies. Events that pass the
 * threshold are forwarded to [delegate].
 */
service HierarchicalLogSink(LogSink delegate, Level rootLevel)
        implements LogSink {

    /**
     * Convenience: root threshold `Info`.
     */
    construct(LogSink delegate) {
        construct HierarchicalLogSink(delegate, Info);
    }

    /**
     * Mutable per-prefix configuration, e.g. `"com.example.payments" -> Debug`.
     */
    private Map<String, Level> levels = new HashMap();

    @Override
    Boolean isEnabled(String loggerName, Level level, Marker? marker = Null) {
        return level.severity >= effectiveLevel(loggerName).severity
            && delegate.isEnabled(loggerName, level, marker);
    }

    @Override
    void log(LogEvent event) {
        if (isEnabled(event.loggerName, event.level, event.marker)) {
            delegate.log(event);
        }
    }

    /**
     * Configure a logger-name prefix. The longest prefix wins.
     */
    void setLevel(String loggerPrefix, Level level) {
        levels.put(loggerPrefix, level);
    }

    /**
     * Remove a logger-name prefix override.
     */
    void clearLevel(String loggerPrefix) {
        levels.remove(loggerPrefix);
    }

    /**
     * Resolve the effective threshold for `loggerName`.
     */
    Level effectiveLevel(String loggerName) {
        String name = loggerName;
        while (True) {
            if (Level level := levels.get(name)) {
                return level;
            }

            if (Int dot := name.lastIndexOf('.')) {
                if (dot <= 0) {
                    return rootLevel;
                }
                name = name[0 ..< dot];
            } else {
                return rootLevel;
            }
        }
    }
}
