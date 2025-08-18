package org.xtclang.ecstasy;

/**
 * Native implementation for `ecstasy.Exception`.
 */
public class Exception extends RuntimeException implements Object {
    public Exception(Throwable cause) {
        super(cause);
    }

    public Exception(java.lang.String message) {
        super(message);
    }

    public Exception(java.lang.String message, Throwable cause) {
        super(message, cause);
    }

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
