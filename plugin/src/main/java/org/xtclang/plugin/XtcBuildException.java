package org.xtclang.plugin;

import org.gradle.api.GradleException;
import org.gradle.api.logging.Logger;

import java.io.Serial;

public class XtcBuildException extends GradleException {
    @Serial
    private static final long serialVersionUID = 1L;

    @SuppressWarnings("unused")
    public XtcBuildException(final String msg) {
        this(msg, null);
    }

    public XtcBuildException(final String msg, final Throwable cause) {
        super(msg, cause);
    }

    @SuppressWarnings("unused")
    public static XtcBuildException buildException(final Logger logger, final String msg) {
        return buildException(logger, msg, null);
    }

    public static XtcBuildException buildException(final Logger logger, final String msg, final Throwable cause) {
        logger.error(msg);
        return new XtcBuildException(msg, cause);
    }
}
