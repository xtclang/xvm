package org.xtclang.plugin;

import static org.xtclang.plugin.XtcBuildException.resolveEllipsis;

import org.gradle.api.GradleException;

@SuppressWarnings("serial")
public class XtcBuildRuntimeException extends GradleException {

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
        this(cause, resolveEllipsis(msg, args));
    }
}
