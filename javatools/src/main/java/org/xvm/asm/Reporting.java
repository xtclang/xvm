package org.xvm.asm;

import java.util.Objects;

/**
 * The diagnostic destination of one parser or name resolver, with scoped replacement.
 *
 * <p>A parser starts with its request listener. A resolver starts inactive and opens a scope
 * for each resolution. Closing a scope restores the previous destination, including after an
 * exception; an inactive resolver retains no request listener. Calls to {@link #get()} are valid
 * only while a destination is active. Files, pools and cached metadata never own this holder.
 *
 * <p>The holder belongs to one operation and is not shared between concurrent compilations.
 */
public final class Reporting {
    /** Create an inactive holder; open {@link #to(ErrorListener)} before reporting. */
    public Reporting() {}

    /**
     * Create a holder active for the lifetime of its parser.
     *
     * @param listener  the request's non-null diagnostic listener
     */
    public Reporting(ErrorListener listener) {
        destination = Objects.requireNonNull(listener, "listener");
    }

    /**
     * @return the active, non-null diagnostic listener
     *
     * @throws IllegalStateException if a resolver attempts to report outside its request scope
     */
    public ErrorListener get() {
        if (destination == null) {
            throw new IllegalStateException("No active diagnostic reporting scope");
        }
        return destination;
    }

    /**
     * Open a nested reporting scope. Use try-with-resources and close in reverse opening order.
     *
     * @param listener  the non-null listener to use until the scope closes
     *
     * @return a scope restoring the previous destination, or the inactive state
     */
    public Scope to(ErrorListener listener) {
        return new Scope(Objects.requireNonNull(listener, "listener"));
    }

    /** A reporting lifetime nested within its owning operation. */
    public final class Scope implements AutoCloseable {
        private Scope(ErrorListener listener) {
            previous = destination;
            destination = listener;
        }

        @Override
        public void close() {
            destination = previous;
        }

        private final ErrorListener previous;
    }

    @Override
    public String toString() {
        return "Reporting(" + destination + ")";
    }

    private ErrorListener destination;
}
