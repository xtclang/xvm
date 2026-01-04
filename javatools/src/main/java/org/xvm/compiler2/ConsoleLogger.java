package org.xvm.compiler2;

import java.io.PrintStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * A simple console-based logger implementation.
 * <p>
 * This provides basic logging to stdout/stderr without requiring
 * any external dependencies.
 */
public class ConsoleLogger implements Logger {

    /**
     * Log level enumeration.
     */
    public enum Level {
        TRACE, DEBUG, INFO, WARN, ERROR
    }

    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    private final String name;
    private final PrintStream out;
    private final PrintStream err;
    private Level level;

    /**
     * Create a console logger.
     *
     * @param name  the logger name
     * @param level the minimum log level
     */
    public ConsoleLogger(String name, Level level) {
        this.name = name;
        this.level = level;
        this.out = System.out;
        this.err = System.err;
    }

    /**
     * Create a console logger at INFO level.
     *
     * @param name the logger name
     */
    public ConsoleLogger(String name) {
        this(name, Level.INFO);
    }

    /**
     * Set the log level.
     *
     * @param level the new level
     */
    public void setLevel(Level level) {
        this.level = level;
    }

    /**
     * @return the current log level
     */
    public Level getLevel() {
        return level;
    }

    @Override
    public boolean isTraceEnabled() {
        return level.ordinal() <= Level.TRACE.ordinal();
    }

    @Override
    public boolean isDebugEnabled() {
        return level.ordinal() <= Level.DEBUG.ordinal();
    }

    @Override
    public boolean isInfoEnabled() {
        return level.ordinal() <= Level.INFO.ordinal();
    }

    @Override
    public boolean isWarnEnabled() {
        return level.ordinal() <= Level.WARN.ordinal();
    }

    @Override
    public boolean isErrorEnabled() {
        return level.ordinal() <= Level.ERROR.ordinal();
    }

    @Override
    public void trace(String msg) {
        if (isTraceEnabled()) {
            log(Level.TRACE, msg);
        }
    }

    @Override
    public void trace(String format, Object... args) {
        if (isTraceEnabled()) {
            log(Level.TRACE, formatMessage(format, args));
        }
    }

    @Override
    public void debug(String msg) {
        if (isDebugEnabled()) {
            log(Level.DEBUG, msg);
        }
    }

    @Override
    public void debug(String format, Object... args) {
        if (isDebugEnabled()) {
            log(Level.DEBUG, formatMessage(format, args));
        }
    }

    @Override
    public void info(String msg) {
        if (isInfoEnabled()) {
            log(Level.INFO, msg);
        }
    }

    @Override
    public void info(String format, Object... args) {
        if (isInfoEnabled()) {
            log(Level.INFO, formatMessage(format, args));
        }
    }

    @Override
    public void warn(String msg) {
        if (isWarnEnabled()) {
            log(Level.WARN, msg);
        }
    }

    @Override
    public void warn(String format, Object... args) {
        if (isWarnEnabled()) {
            log(Level.WARN, formatMessage(format, args));
        }
    }

    @Override
    public void error(String msg) {
        if (isErrorEnabled()) {
            logError(Level.ERROR, msg, null);
        }
    }

    @Override
    public void error(String format, Object... args) {
        if (isErrorEnabled()) {
            logError(Level.ERROR, formatMessage(format, args), null);
        }
    }

    @Override
    public void error(String msg, Throwable t) {
        if (isErrorEnabled()) {
            logError(Level.ERROR, msg, t);
        }
    }

    private void log(Level lvl, String msg) {
        PrintStream stream = (lvl == Level.ERROR || lvl == Level.WARN) ? err : out;
        stream.println(formatLine(lvl, msg));
    }

    private void logError(Level lvl, String msg, Throwable t) {
        err.println(formatLine(lvl, msg));
        if (t != null) {
            t.printStackTrace(err);
        }
    }

    private String formatLine(Level lvl, String msg) {
        String time = LocalDateTime.now().format(TIME_FORMAT);
        String levelStr = String.format("%-5s", lvl.name());
        return String.format("%s %s [%s] %s", time, levelStr, name, msg);
    }

    /**
     * Format a message with {} placeholders (SLF4J style).
     *
     * @param format the format string
     * @param args   the arguments
     * @return the formatted string
     */
    private static String formatMessage(String format, Object... args) {
        if (args == null || args.length == 0) {
            return format;
        }

        StringBuilder sb = new StringBuilder();
        int argIndex = 0;
        int start = 0;

        while (true) {
            int idx = format.indexOf("{}", start);
            if (idx < 0 || argIndex >= args.length) {
                sb.append(format.substring(start));
                break;
            }

            sb.append(format, start, idx);
            sb.append(args[argIndex++]);
            start = idx + 2;
        }

        return sb.toString();
    }
}
