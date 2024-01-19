package org.xtclang.plugin;

import org.gradle.api.GradleException;

import java.io.Serial;

public class XtcBuildRuntimeException extends GradleException {
    @Serial
    private static final long serialVersionUID = 1L;

    @SuppressWarnings("unused")
    XtcBuildRuntimeException(final String msg) {
        this(null, msg);
    }

    @SuppressWarnings("unused")
    XtcBuildRuntimeException(final String msg, final Object... args) {
        this(null, msg, args);
    }

    XtcBuildRuntimeException(final Throwable cause, final String msg) {
        super(msg, cause);
    }

    XtcBuildRuntimeException(final Throwable cause, final String msg, final Object... args) {
        this(cause, XtcBuildException.resolveEllipsis(msg, args));
    }
}
