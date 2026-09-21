package org.xvm.asm;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import org.xvm.compiler.ast.AstNode;

import org.xvm.util.Severity;

/**
 * Represents a list of errors collected from a process such as compilation, assembly, or the
 * verifier, with an option to abort the process should a maximum number of errors be exceeded.
 */
public class ErrorList
        implements ErrorListener {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a list that tolerates {@link #DEFAULT_MAX_ERRORS} serious errors, for a caller
     * with no reason to choose a number. A caller that has one says so with the other
     * constructor, naming {@link #UNLIMITED} or {@link #FIRST_ERROR} where those are what it
     * means.
     */
    public ErrorList() {
        this(DEFAULT_MAX_ERRORS);
    }

    /**
     * @param cMaxErrors  the number of serious errors to tolerate before asking for the process to
     *                    be abandoned, or {@link #UNLIMITED} to tolerate any number
     */
    public ErrorList(int cMaxErrors) {
        f_cMaxErrors = cMaxErrors;
    }

    /**
     * Tolerate any number of serious errors: only a FATAL asks for the process to be abandoned.
     */
    public static final int UNLIMITED = 0;

    /**
     * The budget for a caller that wants to stop at the first serious error.
     */
    public static final int FIRST_ERROR = 1;

    /**
     * How many serious errors to tolerate when the caller has no reason to choose a number.
     *
     * Enough that a file with a genuine spread of problems reports them all, and few enough that
     * source which has gone badly wrong - a mismatched brace early on, say - stops rather than
     * producing a page of consequences.
     */
    public static final int DEFAULT_MAX_ERRORS = 100;

    // ----- ErrorListener methods -----------------------------------------------------------------

    @Override
    public ErrorListener branch(AstNode node) {
        return new BranchedErrorListener(this, f_cMaxErrors, node);
    }

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
     * @return the severity of the ErrorList, which is the severity of the worst
     *         error encountered
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
     * @return maximum number of serious errors encountered before attempting to
     *         abort the process reporting the errors
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
     * Clear the list of errors, resetting the error collection state.
     */
    public void clear() {
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
     * The ErrorListener that can be used to capture errors that may or may not be reported.
     */
    public static class BranchedErrorListener
            extends ErrorList {
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
