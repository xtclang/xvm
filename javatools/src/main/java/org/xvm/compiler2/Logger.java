package org.xvm.compiler2;

/**
 * SLF4J-style logging interface for the compiler.
 * <p>
 * This provides a simple, familiar API for logging that can be implemented
 * by any logging backend (SLF4J, java.util.logging, console, etc.).
 * <p>
 * The interface uses "{}" placeholder syntax compatible with SLF4J.
 */
public interface Logger {

    /**
     * A logger that discards all messages.
     */
    Logger NULL = new Logger() {
        @Override
        public boolean isTraceEnabled() {
            return false;
        }

        @Override
        public boolean isDebugEnabled() {
            return false;
        }

        @Override
        public boolean isInfoEnabled() {
            return false;
        }

        @Override
        public boolean isWarnEnabled() {
            return false;
        }

        @Override
        public boolean isErrorEnabled() {
            return false;
        }

        @Override
        public void trace(String msg) {}

        @Override
        public void trace(String format, Object... args) {}

        @Override
        public void debug(String msg) {}

        @Override
        public void debug(String format, Object... args) {}

        @Override
        public void info(String msg) {}

        @Override
        public void info(String format, Object... args) {}

        @Override
        public void warn(String msg) {}

        @Override
        public void warn(String format, Object... args) {}

        @Override
        public void error(String msg) {}

        @Override
        public void error(String format, Object... args) {}

        @Override
        public void error(String msg, Throwable t) {}
    };

    /**
     * @return true if trace level is enabled
     */
    boolean isTraceEnabled();

    /**
     * @return true if debug level is enabled
     */
    boolean isDebugEnabled();

    /**
     * @return true if info level is enabled
     */
    boolean isInfoEnabled();

    /**
     * @return true if warn level is enabled
     */
    boolean isWarnEnabled();

    /**
     * @return true if error level is enabled
     */
    boolean isErrorEnabled();

    /**
     * Log a trace message.
     *
     * @param msg the message
     */
    void trace(String msg);

    /**
     * Log a trace message with format arguments.
     * Uses "{}" as placeholder.
     *
     * @param format the format string
     * @param args   the arguments
     */
    void trace(String format, Object... args);

    /**
     * Log a debug message.
     *
     * @param msg the message
     */
    void debug(String msg);

    /**
     * Log a debug message with format arguments.
     *
     * @param format the format string
     * @param args   the arguments
     */
    void debug(String format, Object... args);

    /**
     * Log an info message.
     *
     * @param msg the message
     */
    void info(String msg);

    /**
     * Log an info message with format arguments.
     *
     * @param format the format string
     * @param args   the arguments
     */
    void info(String format, Object... args);

    /**
     * Log a warning message.
     *
     * @param msg the message
     */
    void warn(String msg);

    /**
     * Log a warning message with format arguments.
     *
     * @param format the format string
     * @param args   the arguments
     */
    void warn(String format, Object... args);

    /**
     * Log an error message.
     *
     * @param msg the message
     */
    void error(String msg);

    /**
     * Log an error message with format arguments.
     *
     * @param format the format string
     * @param args   the arguments
     */
    void error(String format, Object... args);

    /**
     * Log an error message with an exception.
     *
     * @param msg the message
     * @param t   the exception
     */
    void error(String msg, Throwable t);
}
