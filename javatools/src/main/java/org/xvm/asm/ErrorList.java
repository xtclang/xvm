package org.xvm.asm;


import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import org.xvm.compiler.ast.AstNode;

import org.xvm.util.Severity;

import static java.util.Objects.requireNonNull;

import org.jetbrains.annotations.NotNull;


/**
 * Represents a list of errors collected from a process such as compilation, assembly, or the
 * verifier, with an option to abort the process should a maximum number of errors be exceeded.
 *
 * <p><b>Safe for concurrent use.</b> This is the listener a host naturally passes to
 * {@code XtcEngine.compile(errsCaller, ...)}, and that one object is then handed to every compile
 * thread the engine runs - so the shared terminal sink has to tolerate concurrent {@code log}. It
 * did not: {@code log} did an unguarded {@code HashSet.add}, a read-modify-write on the worst
 * severity, an {@code ArrayList.add} and a {@code ++}, and a caller iterating {@link #getErrors}
 * could take a {@code ConcurrentModificationException} from a sibling compile.
 *
 * <p>Locking rather than a rule, because a rule nobody enforces is the same failure as the mutable
 * listener field this framework spent its effort deleting - correct only for as long as everyone
 * remembers. Contention is not a concern: diagnostics are rare next to the work that produces them,
 * and each compile still collects into its own list, so this monitor is only ever contended on the
 * host's shared sink.
 *
 * <p>Speculative work does not need any of this. {@link #branch} still gives a private, unshared
 * listener, and {@link ErrorListener#merge} still promotes it at the end - merge-at-end remains the
 * idiom for "might fail", and this makes the terminal sink safe for the case where it cannot be.
 */
public class ErrorList
        implements ErrorListener {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * The number of serious errors after which a process gives up.
     *
     * <p>There is nothing special about this value; the point is that it is one value with a name,
     * rather than a different literal at each construction site. Before this existed the callers
     * passed 341, 24, 100, 10 and 5, none of which was explained and at least two of which cannot
     * have been chosen deliberately.
     */
    public static final int DEFAULT_MAX_ERRORS = 100;

    /**
     * Construct an ErrorList that gives up after {@link #DEFAULT_MAX_ERRORS} serious errors.
     */
    public ErrorList() {
        this(DEFAULT_MAX_ERRORS);
    }

    /**
     * @param cMaxErrors  the number of serious errors after which {@link #isAbortDesired} answers
     *                    true; use {@link #firstError()} or {@link #unlimited()} where that is the
     *                    intent, so the number does not have to be read as one
     */
    public ErrorList(int cMaxErrors) {
        f_cMaxErrors = cMaxErrors;
    }

    /**
     * @return an ErrorList for a speculative attempt, which only needs to know whether anything
     *         went wrong rather than collect a report
     */
    public static @NotNull ErrorList firstError() {
        return new ErrorList(1);
    }

    /**
     * @return an ErrorList that collects everything and never asks the process to abort
     */
    public static @NotNull ErrorList unlimited() {
        return new ErrorList(Integer.MAX_VALUE);
    }


    // ----- ErrorListener methods -----------------------------------------------------------------

    @Override
    public @NotNull ErrorListener branch(AstNode node) {
        return new BranchedErrorListener(this, f_cMaxErrors, node);
    }

    @Override
    public synchronized void log(ErrorInfo err) {
        String uid = err.genUID();
        if (f_setUID.add(uid)) {
            // remember the highest severity encountered
            Severity severity = err.getSeverity();
            if (severity.ordinal() > m_severity.ordinal()) {
                m_severity = severity;
            }

            // accumulate all the errors in a list
            f_list.add(err);

            // keep track of the number of serious errors; quit the process once
            // that number grows too large
            if (severity.compareTo(Severity.ERROR) >= 0) {
                ++m_cErrors;
            }
        }
    }

    @Override
    public synchronized boolean isAbortDesired() {
        return m_severity == Severity.FATAL || f_cMaxErrors > 0 &&
                m_severity.compareTo(Severity.ERROR) >= 0 && m_cErrors >= f_cMaxErrors;
    }

    @Override
    public boolean hasSeriousErrors() {
        return hasEncountered(Severity.ERROR);
    }

    @Override
    public synchronized boolean hasError(String sCode) {
        return f_list.stream().anyMatch(info -> info.getCode().equals(sCode));
    }


    // ----- accessors -----------------------------------------------------------------------------

    /**
     * @return the severity of the ErrorList, which is the severity of the worst
     *         error encountered
     */
    public synchronized Severity getSeverity() {
        return m_severity;
    }

    /**
     * @return the count of serious errors encountered
     */
    public synchronized int getSeriousErrorCount() {
        return m_cErrors;
    }

    /**
     * @return maximum number of serious errors encountered before attempting to
     *         abort the process reporting the errors
     */
    public int getSeriousErrorMax() {
        return f_cMaxErrors;
    }

    /**
     * @return true iff there are errors of any severity
     */
    public synchronized boolean hasErrors() {
        return !f_list.isEmpty();
    }

    /**
     * Compare the max encountered severity with the specified severity to see if we have
     * encountered an error of at least that severity level.
     *
     * @param sev  the severity to check for
     *
     * @return true iff an error has been logged with at least the specified severity
     */
    public synchronized boolean hasEncountered(Severity sev) {
        return m_severity != null && m_severity.compareTo(sev) >= 0;
    }

    /**
     * @return an immutable snapshot of the ErrorInfo objects logged so far
     */
    public synchronized List<ErrorInfo> getErrors() {
        return List.copyOf(f_list);
    }

    /**
     * Clear the list of errors, resetting the error collection state.
     */
    public synchronized void clear() {
        f_setUID.clear();
        f_list.clear();
        m_cErrors  = 0;
        m_severity = Severity.NONE;
    }

    /**
     * Log the errors from this ErrorList into another ErrorListener.
     *
     * @param errs  the ErrorListener to log all the errors from this ErrorList to
     */
    public void logTo(ErrorListener errs) {
        // Deliberately NOT synchronized: getErrors() takes its snapshot under the lock and this
        // then iterates outside it, so a foreign listener is never called while this monitor is
        // held. Holding a lock across a call into code somebody else wrote is how two listeners
        // teed together deadlock.
        for (ErrorInfo err : getErrors()) {
            errs.log(err);
        }
    }

    @Override
    public synchronized String toString() {
        if (m_cErrors == 0) {
            return "Empty";
        }

        return "Count=" + m_cErrors
                + ", Severity=" +  m_severity.name()
                + ", Last=" + f_list.get(f_list.size()-1);
    }


    // ----- inner class: BranchedErrorListener ----------------------------------------------------

    /**
     * The ErrorListener that can be used to capture errors that may or may not be reported.
     */
    public static class BranchedErrorListener
            extends ErrorList {
        public BranchedErrorListener(ErrorListener listener, int cMaxErrors, AstNode node) {
            super(cMaxErrors);

            f_listener = requireNonNull(listener, "listener");
            f_node     = node;
        }

        @Override
        public @NotNull ErrorListener branch(AstNode node) {
            return new BranchedErrorListener(this, getSeriousErrorMax(),
                    node == null ? f_node : node);
        }

        @Override
        public void log(Severity severity, String sCode, Site site, Object... aoParam) {
            // A branch made for a node reports AT that node: a diagnostic raised against a
            // structure during speculative work belongs where the speculation is, not where the
            // structure happens to live. Expressible now as a substitution of one Site for
            // another; before Site it needed an override of one particular overload, and said
            // nothing about the other three.
            super.log(severity, sCode,
                    f_node != null && site instanceof Site.At
                            ? ErrorListener.in(f_node.getSource(), f_node.getStartPosition(),
                                    f_node.getEndPosition())
                            : site,
                    aoParam);
        }

        @Override
        public @NotNull ErrorListener merge() {
            if (hasErrors()) {
                logTo(f_listener);
            }

            return f_listener;
        }

        @Override
        public boolean isSilent() {
            return f_listener.isSilent();
        }

        @Override
        public String toString() {
            return "Branched: " + super.toString();
        }

        private final @NotNull ErrorListener f_listener;
        private final AstNode       f_node;
    }


    // ----- data members --------------------------------------------------------------------------

    /**
     * Maximum number of serious errors to tolerate before abandoning the process.
     */
    private final int f_cMaxErrors;

    /**
     * The number of serious errors encountered.
     */
    private int m_cErrors;

    /**
     * The worst severity encountered.
     */
    private Severity m_severity = Severity.NONE;

    /**
     * The accumulated list of errors.
     */
    private final ArrayList<ErrorInfo> f_list = new ArrayList<>();

    /**
     * The UIDs of previously logged errors.
     */
    private final HashSet<String> f_setUID = new HashSet<>();
}
