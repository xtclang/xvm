package org.xtclang.ecstasy;

import org.xvm.javajit.Ctx;

/**
 * Native implementation for `ecstasy.Exception`.
 */
public class Exception extends RuntimeException implements Object {
    public Exception(Throwable cause) {
        super(cause);
        $ctx = Ctx.get();
    }

    public Exception(java.lang.String message) {
        super(message);
        $ctx = Ctx.get();
    }

    public Exception(java.lang.String message, Throwable cause) {
        super(message, cause);
        $ctx = Ctx.get();
    }

    public Ctx $ctx;

    public static Exception $ro(java.lang.String text, Exception cause) {
        // TODO - how to create an Ecstasy ReadOnly exception?
        text = text == null ? ("ReadOnly: " + text) : "ReadOnly";
        return cause == null ? new Exception(text) : new Exception(text, cause);
    }

    public static Exception $oob(java.lang.String text, Exception cause) {
        // TODO - how to create an Ecstasy OutOfBounds exception?
        text = text == null ? ("OutOfBounds: " + text) : "OutOfBounds";
        return cause == null ? new Exception(text) : new Exception(text, cause);
    }

    public static Exception $unassigned(java.lang.String text, Exception cause) {
        // TODO - ditto
        text = text == null ? ("Unassigned: " + text) : "Unassigned";
        return cause == null ? new Exception(text) : new Exception(text, cause);
    }
}
