package org.xtclang.ecstasy;

/**
 * Native support for `ecstasy.Exception`.
 */
public class nException extends RuntimeException {
    /** Never Java-serialized; present so the serial lint stays clean. */
    private static final long serialVersionUID = 1L;

    public nException(Throwable cause, Exception exception) {
        super(cause);

        this.f_exception = exception;
    }

    /**
     * @return the Ecstasy exception this wraps
     */
    public Exception getException() {
        return f_exception;
    }

    /**
     * {@link Exception} is an Ecstasy value and is deliberately not {@code Serializable}, so this
     * field would fail a Java serialization of this wrapper. Nothing serializes it - the class is
     * {@code Serializable} only because {@link RuntimeException} is. Marking it {@code transient}
     * would be worse: {@link #getException} would silently answer null instead of failing loudly,
     * for a path that does not exist.
     */
    @SuppressWarnings("serial")
    private final Exception f_exception;
}
