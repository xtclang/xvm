package org.xvm.plugin;

import org.gradle.api.GradleException;
import org.gradle.api.logging.Logger;

@SuppressWarnings("serial")
public class XtcBuildException extends GradleException {
    public XtcBuildException(final String msg) {
        this(msg, null);
    }

    public XtcBuildException(final String msg, final Throwable cause) {
        super(msg, cause);
    }

    public static XtcBuildException buildException(final Logger logger, final String msg) {
        return buildException(logger, msg, null);
    }

    public static XtcBuildException buildException(final Logger logger, final String msg, final Throwable cause) {
        logger.error(msg);
        return new XtcBuildException(msg, cause);
    }
}
