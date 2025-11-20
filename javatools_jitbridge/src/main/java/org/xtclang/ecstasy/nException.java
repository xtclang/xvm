package org.xtclang.ecstasy;

/**
 * Native support for `ecstasy.Exception`.
 */
public class nException extends RuntimeException {
    public nException(Throwable cause, Exception exception) {
        super(cause);

        this.exception = exception;
    }
    public final Exception exception;
}
