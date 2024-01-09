package org.xtclang.plugin;

import org.gradle.api.GradleException;
import org.gradle.api.logging.Logger;

import java.io.Serial;
import java.util.Arrays;

public class XtcBuildException extends GradleException {
    @Serial
    private static final long serialVersionUID = 1L;

    @SuppressWarnings("unused")
    XtcBuildException(final String msg) {
        this(null, msg);
    }

    XtcBuildException(final Throwable cause, final String msg) {
        super(msg, cause);
    }

    XtcBuildException(final String msg, final Object... args) {
        this(resolveEllipsis(msg, args));
    }

    XtcBuildException(final Throwable cause, final String msg, final Object... args) {
        this(cause, resolveEllipsis(msg, args));
    }

    @SuppressWarnings("unused")
    public static XtcBuildException buildException(final Logger logger, final String msg) {
        return buildException(logger, null, msg);
    }

    public static XtcBuildException buildException(final Logger logger, final Throwable cause, final String msg, final Object... args) {
        logger.error(msg);
        return new XtcBuildException(cause, msg, args);
    }

    private static String resolveEllipsis(final String msg, final Object... args) {
        final var template = msg.replace("{}", "#");
        final var list = Arrays.asList(args);
        final var sb = new StringBuilder();
        for (int i = 0, pos = 0; i < template.length(); i++) {
            final var c = template.charAt(i);
            if (c == '#') {
                if (pos >= list.size()) {
                    throw new IllegalArgumentException("More ellipses than tokens in expansion.");
                }
                sb.append(list.get(pos++));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
