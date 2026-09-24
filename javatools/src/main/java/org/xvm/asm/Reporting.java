package org.xvm.asm;

import static java.util.Objects.requireNonNull;

/**
 * A diagnostic destination that can be diverted for a lexical scope.
 *
 * The parser and name resolver use this when callbacks cannot take a listener parameter.
 * Scopes must be closed in reverse order, normally by try-with-resources. Each scope restores
 * its predecessor even when the operation throws. An inactive resolver has a null destination;
 * an active scope always has a listener.
 *
 * This holder controls lifetime, not ownership or concurrency. Owners remain confined to one
 * compiler operation; sharing a holder across threads is not supported.
 */
public final class Reporting {
    /**
     * @param errs  where diagnostics go to begin with; null if the owner has no destination
     *              until one is given to it
     */
    public Reporting(ErrorListener errs) {
        m_errs = errs;
    }

    /**
     * @return where diagnostics go right now, or null if the owner has none
     */
    public ErrorListener get() {
        return m_errs;
    }

    /**
     * Send diagnostics to the given listener until the returned scope is closed.
     *
     * @param errs  the listener to report to for the duration of the scope
     *
     * @return the scope, which restores the previous destination when closed
     */
    public Scope to(ErrorListener errs) {
        return new Scope(errs);
    }

    /**
     * The scope opened by {@link #to}.
     */
    public final class Scope
            implements AutoCloseable {
        private Scope(ErrorListener errs) {
            f_errsPrev = m_errs;
            m_errs     = requireNonNull(errs, "errs");
        }

        @Override
        public void close() {
            m_errs = f_errsPrev;
        }

        private final ErrorListener f_errsPrev;
    }

    @Override
    public String toString() {
        return "Reporting(" + m_errs + ")";
    }

    private ErrorListener m_errs;
}
