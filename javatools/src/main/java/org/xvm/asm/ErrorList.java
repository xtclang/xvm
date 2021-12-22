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
        implements ErrorListener
    {
    // ----- constructors --------------------------------------------------------------------------

    public ErrorList(int cMaxErrors)
        {
        f_cMaxErrors = cMaxErrors;
        }


    // ----- ErrorListener methods -----------------------------------------------------------------

    @Override
    public ErrorListener branch(AstNode node)
        {
        return new BranchedErrorListener(this, f_cMaxErrors, node);
        }

    @Override
    public boolean log(ErrorInfo err)
        {
        String uid = err.genUID();
        if (f_setUID.add(uid))
            {
            // remember the highest severity encountered
            Severity severity = err.getSeverity();
            if (severity.ordinal() > m_severity.ordinal())
                {
                m_severity = severity;
                }

            // accumulate all the errors in a list
            f_list.add(err);

            // keep track of the number of serious errors; quit the process once
            // that number grows too large
            if (severity.compareTo(Severity.ERROR) >= 0)
                {
                ++m_cErrors;
                }
            }

        return isAbortDesired();
        }

    @Override
    public boolean isAbortDesired()
        {
        return m_severity == Severity.FATAL || f_cMaxErrors > 0 &&
                m_severity.compareTo(Severity.ERROR) >= 0 && m_cErrors >= f_cMaxErrors;
        }

    @Override
    public boolean hasSeriousErrors()
        {
        return hasEncountered(Severity.ERROR);
        }

    @Override
    public boolean hasError(String sCode)
        {
        return f_list.stream().anyMatch(info -> info.getCode().equals(sCode));
        }

    @Override
    public boolean isSilent()
        {
        return false;
        }


    // ----- accessors -----------------------------------------------------------------------------

    /**
     * @return the severity of the ErrorList, which is the severity of the worst
     *         error encountered
     */
    public Severity getSeverity()
        {
        return m_severity;
        }

    /**
     * @return the count of serious errors encountered
     */
    public int getSeriousErrorCount()
        {
        return m_cErrors;
        }

    /**
     * @return maximum number of serious errors encountered before attempting to
     *         abort the process reporting the errors
     */
    public int getSeriousErrorMax()
        {
        return f_cMaxErrors;
        }

    /**
     * @return true iff there are errors of any severity
     */
    public boolean hasErrors()
        {
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
    public boolean hasEncountered(Severity sev)
        {
        return m_severity != null && m_severity.compareTo(sev) >= 0;
        }

    /**
     * @return the list of ErrorInfo objects
     */
    public List<ErrorInfo> getErrors()
        {
        return f_list;
        }

    /**
     * Clear the list of errors, resetting the error collection state.
     */
    public void clear()
        {
        f_list.clear();
        m_cErrors  = 0;
        m_severity = Severity.NONE;
        }

    /**
     * Log the errors from this ErrorList into another ErrorListener.
     *
     * @param errs  the ErrorListener to log all the errors from this ErrorList to
     */
    public void logTo(ErrorListener errs)
        {
        for (ErrorInfo err : getErrors())
            {
            errs.log(err);
            }
        }

    @Override
    public String toString()
        {
        if (m_cErrors == 0)
            {
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
            extends ErrorList
        {
        public BranchedErrorListener(ErrorListener listener, int cMaxErrors, AstNode node)
            {
            super(cMaxErrors);

            f_listener = listener;
            f_node     = node;
            }

        @Override
        public ErrorListener branch(AstNode node)
            {
            return new BranchedErrorListener(this, getSeriousErrorMax(),
                    node == null ? f_node : node);
            }

        @Override
        public boolean log(Severity severity, String sCode, Object[] aoParam, XvmStructure xs)
            {
            return f_node == null
                ? super.log(severity, sCode, aoParam, xs)
                : log(severity, sCode, aoParam,
                        f_node.getSource(), f_node.getStartPosition(), f_node.getEndPosition());
            }

        @Override
        public ErrorListener merge()
            {
            logTo(f_listener);

            return f_listener;
            }

        @Override
        public boolean isSilent()
            {
            return f_listener.isSilent();
            }

        @Override
        public String toString()
            {
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
