package org.xvm.tool;

import java.text.MessageFormat;

import org.xvm.util.Severity;

/**
 * An interface representing the launcher tool's interaction with the terminal.
 * Provides methods for output, error reporting, and formatted logging.
 */
public interface Console {
    /**
     * Print a blank line to the terminal.
     */
    default void out() {
        out("");
    }

    /**
     * Print the String value of some object to the terminal.
     */
    default void out(final Object o) {
        System.out.println(o);
    }

    /**
     * Print a blank line to the terminal.
     */
    default void err() {
        err("");
    }

    /**
     * Print the String value of some object to the terminal.
     */
    default void err(final Object o) {
        System.err.println(o);
    }

    /**
     * Log a message of a specified severity.
     *
     * @param sev   the severity (may indicate an error)
     * @param sMsg  the message or error to display
     */
    default void log(final Severity sev, final String sMsg) {
        out(sev.desc() + ": " + sMsg);
    }

    /**
     * Log a message with template substitution (SLF4J-style).
     * Use {} placeholders in the template for parameter substitution.
     * <p>
     * Example: log(ERROR, "Command {} not found in {}", cmdName, location)
     *
     * @param sev       the severity (may indicate an error)
     * @param template  the message template with {} placeholders
     * @param params    parameters to substitute into the template
     */
    default void log(final Severity sev, final String template, final Object... params) {
        out(sev.desc() + ": " + formatTemplate(template, params));
    }

    /**
     * Format a message template by substituting {} placeholders with parameters.
     * Converts SLF4J-style {} placeholders to MessageFormat-style {0}, {1}, etc.
     * and delegates to MessageFormat for actual formatting.
     *
     * @param template  the template string with {} placeholders
     * @param params    parameters to substitute
     * @return formatted message
     */
    static String formatTemplate(final String template, final Object... params) {
        assert template != null && params != null;
        final var sb = new StringBuilder(template.length() + params.length * 3);
        int paramIndex = 0;
        for (int pos = 0; pos < template.length(); pos++) {
            final int openBrace = template.indexOf('{', pos);
            if (openBrace == -1) {
                sb.append(template.substring(pos));
                break;
            }
            sb.append(template, pos, openBrace);
            final boolean isPlaceholder = openBrace + 1 < template.length() && template.charAt(openBrace + 1) == '}';
            if (isPlaceholder) {
                sb.append('{').append(paramIndex++).append('}');
                pos = openBrace + 1;
                continue;
            }
            sb.append('{');
        }
        return MessageFormat.format(sb.toString(), params);
    }
}
