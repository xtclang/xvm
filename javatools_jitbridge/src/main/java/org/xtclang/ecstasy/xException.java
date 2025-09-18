package org.xtclang.ecstasy;

/**
 * Native support for `ecstasy.Exception`.
 */
public class xException extends RuntimeException {
    public xException(Throwable cause, Exception exception) {
        super(cause);

        this.exception = exception;
    }
    public final Exception exception;
}
