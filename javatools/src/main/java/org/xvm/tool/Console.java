package org.xvm.tool;

import java.text.MessageFormat;

import org.xvm.util.Severity;

/**
 * An interface representing the launcher tool's interaction with output streams.
 * <p>
 * Console provides simple output methods for displaying messages. It does NOT track severity state
 * or perform filtering - that is the responsibility of the {@link Launcher}.
 */
public interface Console {
    /**
     * Print a blank line.
     */
    default String out() {
        return out("");
    }

    /**
     * Print the String value of some object.
     *
     * @param o  the object to print
     */
    default String out(Object o) {
        var str = String.valueOf(o);
        System.out.println(o);
        return str;
    }

    /**
     * Print a blank line to error stream.
     */
    default String err() {
        return err("");
    }

    /**
     * Print the String value of some object to error stream.
     *
     * @param o  the object to print to error stream
     */
    default String err(Object o) {
        var str = String.valueOf(o);
        System.err.println(str);
        return str;
    }

    /**
     * Log a message of a specified severity. Default implementation formats as
     * "{severity}: {message}".
     */
    default String log(Severity sev, String template, Object... params) {
        return log(sev, null, template, params);
    }

    /**
     * Log a message with template substitution (SLF4J-style). Use {} placeholders in the template
     * for parameter substitution.
     *
     * @param sev       the severity level
     * @param cause     the cause exception; can be null
     * @param template  the message template with {} placeholders
     * @param params    parameters to substitute into the template
     */
    default String log(Severity sev, Throwable cause, String template, Object... params) {
        var sb = new StringBuilder(sev.desc())
            .append(": ")
            .append(formatTemplate(template, params));

        if (cause != null) {
            sb.append("\n[")
              .append(cause.getClass().getSimpleName());
            if (cause.getMessage() != null) {
              sb.append(": ")
                .append(cause.getMessage());
            }
            sb.append(']');
        }
        return out(sb.toString());
    }

    /**
     * Format a message template by substituting {} placeholders with parameters.
     * Converts SLF4J-style {} placeholders to MessageFormat-style {0}, {1}, etc.
     * If the template already contains "{0", assume it's MessageFormat and use as-is.
     *
     * @param template  the template string with {} or {n} placeholders
     * @param params    parameters to substitute
     *
     * @return formatted message
     */
    static String formatTemplate(String template, Object... params) {
        if (template == null || params == null || params.length == 0 || params[0] == null) {
            return template;
        }

        // Quick check: if template contains "{0", assume it's already MessageFormat
        if (template.contains("{0")) {
            return MessageFormat.format(template, params);
        }

        // Convert SLF4J-style {} to MessageFormat-style {0}, {1}, etc.
        var sb = new StringBuilder(template.length() + params.length * 3);
        int paramIndex = 0;
        for (int pos = 0; pos < template.length(); pos++) {
            int openBrace = template.indexOf('{', pos);
            if (openBrace == -1) {
                sb.append(template.substring(pos));
                break;
            }
            sb.append(template, pos, openBrace);
            boolean isPlaceholder = openBrace + 1 < template.length() && template.charAt(openBrace + 1) == '}';
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
