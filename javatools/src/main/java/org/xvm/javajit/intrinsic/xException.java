package org.xvm.javajit.intrinsic;

import org.xvm.javajit.Ctx;

/**
 * Native implementation for ecstasy.Exception
 */
public class xException extends RuntimeException {
    public xException(Throwable cause) {
        super(cause);
        $ctx = Ctx.get();
    }

    public xException(String message) {
        super(message);
        $ctx = Ctx.get();
    }

    public xException(String message, Throwable cause) {
        super(message, cause);
        $ctx = Ctx.get();
    }

    public Ctx $ctx;

    public static xException newReadOnly(String text, xException cause) {
        // TODO - how to create an Ecstasy ReadOnly exception?
        text = text == null ? ("ReadOnly: " + text) : "ReadOnly";
        return cause == null ? new xException(text) : new xException(text, cause);
    }

    public static xException newOutOfBounds(String text, xException cause) {
        // TODO - how to create an Ecstasy OutOfBounds exception?
        text = text == null ? ("OutOfBounds: " + text) : "OutOfBounds";
        return cause == null ? new xException(text) : new xException(text, cause);
    }
}
