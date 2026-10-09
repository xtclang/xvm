package org.xvm.asm;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import org.xvm.compiler.ast.AstNode;

import org.xvm.util.Severity;

/**
 * An operation's diagnostic buffer with deduplication, severity state, and an optional count budget.
 *
 * <p>The first report for each {@link ErrorInfo#genUID()} is retained in insertion order. Each
 * retained ERROR or FATAL counts toward the budget; warnings and informational reports do not.
 * {@link #isAbortDesired()} becomes true when a positive budget is reached, or after any FATAL.
 * Logging still records subsequent reports until the caller honors that stop request.
 *
 * <p>Use this for compilation results that must retain diagnostics, and
 * {@link ErrorListener#collecting(java.util.function.Consumer)} when a host callback should receive
 * every report without deduplication. Instances are mutable and are not thread-safe.
 */
public class ErrorList
        implements ErrorListener {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a buffer that requests an abort on its {@link ErrorListener#DEFAULT_MAX_ERRORS}-th serious report.
     *
     * <p>Use {@link #ErrorList(int)} with {@link #FIRST_ERROR}, {@link #UNLIMITED}, or a caller-specific
     * positive budget when that policy is required.
     */
    public ErrorList() {
        this(ErrorListener.DEFAULT_MAX_ERRORS);
    }

    /**
     * Construct a buffer with the requested serious-error budget.
     *
     * @param cMaxErrors  request an abort when this many retained ERROR/FATAL reports have arrived;
     *                    non-positive values disable the count limit, conventionally {@link #UNLIMITED}
     */
    public ErrorList(int cMaxErrors) {
        f_cMaxErrors = cMaxErrors;
    }

    /**
     * Disable the count budget. A retained FATAL still requests an abort.
     */
    public static final int UNLIMITED = 0;

    /**
     * Request an abort after the first retained ERROR or FATAL.
     */
    public static final int FIRST_ERROR = 1;

    // ----- ErrorListener methods -----------------------------------------------------------------

    /**
     * Create a diagnostic buffer with this list's count budget and parent abort policy.
     *
     * <p>The child counts its own retained reports; it does not subtract the parent's count from its
     * budget. It requests an abort when either its own policy or the parent's policy says to stop.
     * Reports reach this list only when the child is merged.
     *
     * @param node  the optional source context for structure-site diagnostics
     *
     * @return a new child buffer
     */
    @Override
    public ErrorListener branch(AstNode node) {
        return new BranchedErrorListener(this, f_cMaxErrors, node);
    }

    /**
     * Record the first report for this diagnostic's UID and update severity and budget state.
     *
     * <p>Duplicate UIDs do not add entries or spend the count budget. This method does not stop the
     * caller or refuse reports after the budget is reached; query {@link #isAbortDesired()} explicitly.
     *
     * @param err  the diagnostic to record
     */
    @Override
    public void log(ErrorInfo err) {
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

    /**
     * Test whether a retained FATAL or the configured serious-error count requests an abort.
     *
     * @return true for FATAL, or when a positive budget has been reached
     */
    @Override
    public boolean isAbortDesired() {
        return m_severity == Severity.FATAL || f_cMaxErrors > 0 &&
                m_severity.compareTo(Severity.ERROR) >= 0 && m_cErrors >= f_cMaxErrors;
    }

    @Override
    public boolean hasSeriousErrors() {
        return hasEncountered(Severity.ERROR);
    }

    @Override
    public boolean hasError(String sCode) {
        return f_list.stream().anyMatch(info -> info.getCode().equals(sCode));
    }

    // ----- accessors -----------------------------------------------------------------------------

    /**
     * @return the severity of the ErrorList, which is the severity of the worst error encountered
     */
    public Severity getSeverity() {
        return m_severity;
    }

    /**
     * @return the count of serious errors encountered
     */
    public int getSeriousErrorCount() {
        return m_cErrors;
    }

    /**
     * @return maximum number of serious errors encountered before attempting to abort the process
     *         reporting the errors
     */
    public int getSeriousErrorMax() {
        return f_cMaxErrors;
    }

    /**
     * @return true iff there are errors of any severity
     */
    public boolean hasErrors() {
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
    public boolean hasEncountered(Severity sev) {
        return m_severity != null && m_severity.compareTo(sev) >= 0;
    }

    /**
     * @return the list of ErrorInfo objects
     */
    public List<ErrorInfo> getErrors() {
        return f_list;
    }

    /**
     * Clear the retained reports, serious-error count, and worst severity.
     *
     * <p>Previously seen diagnostic UIDs remain recorded, so logging the same diagnostic after
     * clearing still suppresses it. Use a new ErrorList for a new independent operation.
     */
    public void clear() {
        f_list.clear();
        m_cErrors  = 0;
        m_severity = Severity.NONE;
    }

    /**
     * Replay all retained diagnostics to another listener in insertion order.
     *
     * <p>This neither clears this list nor queries the destination's abort policy. Repeated calls
     * replay the same reports; deduplication, if desired, is the destination's responsibility.
     *
     * @param errs  the destination for the retained reports
     */
    public void logTo(ErrorListener errs) {
        for (ErrorInfo err : getErrors()) {
            errs.log(err);
        }
    }

    @Override
    public String toString() {
        if (m_cErrors == 0) {
            return "Empty";
        }

        return "Count=" + m_cErrors
                + ", Severity=" +  m_severity.name()
                + ", Last=" + f_list.get(f_list.size()-1);
    }

    // ----- inner class: BranchedErrorListener ----------------------------------------------------

    /**
     * A temporary diagnostic buffer whose accepted reports can be merged into a parent listener.
     *
     * <p>It retains its own deduplication and count state while observing the parent's abort request.
     * Discarding the branch leaves the parent's diagnostics untouched. Merging forwards reports and
     * returns the parent without clearing the branch, so callers should merge an accepted branch once.
     */
    public static class BranchedErrorListener
            extends ErrorList {
        /**
         * Construct a speculative buffer associated with a parent and optional source node.
         *
         * @param listener   the parent destination and additional abort policy
         * @param cMaxErrors  this branch's serious-error budget, or {@link ErrorList#UNLIMITED}
         * @param node        the optional source context for structure-site reports
         */
        public BranchedErrorListener(ErrorListener listener, int cMaxErrors, AstNode node) {
            super(cMaxErrors);

            f_listener = listener;
            f_node     = node;
        }

        @Override
        public ErrorListener branch(AstNode node) {
            return new BranchedErrorListener(this, getSeriousErrorMax(),
                    node == null ? f_node : node);
        }

        @Override
        public void log(Severity severity, String sCode, Site site, Object... aoParam) {
            // a branch taken for a particular node re-anchors at that node: a diagnostic raised
            // against a structure carries no source location of its own, and the node is the
            // location the brancher knew about
            if (f_node != null && site instanceof Site.At) {
                site = ErrorListener.in(f_node.getSource(), f_node.getStartPosition(), f_node.getEndPosition());
            }
            super.log(severity, sCode, site, aoParam);
        }

        @Override
        public ErrorListener merge() {
            if (hasErrors()) {
                logTo(f_listener);
            }

            return f_listener;
        }

        @Override
        public boolean isAbortDesired() {
            return super.isAbortDesired() || f_listener.isAbortDesired();
        }

        @Override
        public boolean isSilent() {
            return f_listener.isSilent();
        }

        @Override
        public String toString() {
            return "Branched: " + super.toString();
        }

        private final ErrorListener f_listener;
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
