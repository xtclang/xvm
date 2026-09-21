package org.xvm.asm;

import static java.util.Objects.requireNonNull;

/**
 * Where something's diagnostics go, and a way to send them somewhere else for a while.
 *
 * Several things in the compiler report through a destination rather than through a parameter -
 * the parser, because threading a listener through two hundred parse methods is not a refactor;
 * a file structure, because an interned TypeConstant asked to build a TypeInfo has no caller to
 * ask; a name resolver, because the callbacks it makes have no listener of their own. Each of
 * them needs to point that destination somewhere else for a stretch of work and then give it
 * back.
 *
 * Each used to do that with a mutable field and its own copy of the save-and-restore, which is
 * three chances to forget the restoring and three fields that could not be final. The mutation
 * lives here instead: holders are final fields, the destination moves only inside a scope, and
 * the scope puts it back on every exit path including an exception.
 *
 * This is deliberately not a fix for ownership. The destination is still reachable by anything
 * holding the owner, so two threads working through one owner still interfere; what a scope
 * fixes is lifetime, not who decides. Ownership is fixed by passing a listener as a parameter,
 * which is what the rest of the compiler does and what these three cannot yet afford.
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
     * Answer the way another holder does. For a structure being copied, which has no scope of its
     * own to inherit but must still report where the structure it was copied from reports.
     */
    void adoptFrom(Reporting that) {
        m_errs = that.m_errs;
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
