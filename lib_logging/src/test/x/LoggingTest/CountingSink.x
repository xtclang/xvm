import logging.Level;
import logging.LogEvent;
import logging.LogSink;
import logging.Marker;

/**
 * Test-only sink: counts events by level. Useful as a reference example for users
 * writing their own custom sinks — see `doc/logging/CUSTOM_SINKS.md`.
 */
service CountingSink
        implements LogSink {

    public/private Map<Level, Int> counts = new HashMap();

    @Override
    Boolean isEnabled(String loggerName, Level level, Marker? marker = Null) {
        return True;
    }

    @Override
    void log(LogEvent event) {
        counts.process(event.level, e -> {
            e.value = e.exists ? e.value + 1 : 1;
            return Null;
        });
    }
}
