package org.xvm.asm;


import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import org.xvm.compiler.Compiler;

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
        m_cMaxErrors = cMaxErrors;
        }


    // ----- ErrorListener methods -----------------------------------------------------------------

    @Override
    public ErrorListener branch()
        {
        return new BranchedErrorListener(this, m_cMaxErrors);
        }

    @Override
    public boolean log(ErrorInfo err)
        {
        Object uid = err.genUID();
        if (m_setUID.add(uid))
            {
            // remember the highest severity encountered
            Severity severity = err.getSeverity();
            if (severity.ordinal() > m_severity.ordinal())
                {
                m_severity = severity;
                }

            // accumulate all the errors in a list
            m_list.add(err);

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
        return m_severity == Severity.FATAL || m_cMaxErrors > 0 &&
                m_severity.compareTo(Severity.ERROR) >= 0 && m_cErrors >= m_cMaxErrors;
        }

    @Override
    public boolean hasSeriousErrors()
        {
        return hasEncountered(Severity.ERROR);
        }

    @Override
    public boolean hasError(String sCode)
        {
        return m_list.stream().anyMatch(info -> info.getCode().equals(sCode));
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
        return m_cMaxErrors;
        }

    /**
     * @return true iff there are errors of any severity
     */
    public boolean hasErrors()
        {
        return !m_list.isEmpty();
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
        return m_list;
        }

    /**
     * Clear the list of errors, resetting the error collection state.
     */
    public void clear()
        {
        m_list.clear();
        m_cErrors  = 0;
        m_severity = Severity.NONE;
        }

    /**
     * Log the errors from this ErrorList into another ErrorListener.
     *
     * @param errs  the ErrorListener to log all of the errors from this ErrorList to
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
                + ", Last=" + m_list.get(m_list.size()-1);
        }


    // ----- inner class: BranchedErrorListener ----------------------------------------------------

    /**
     * The ErrorListener that can be used to capture errors that may or may not be reported.
     */
    public static class BranchedErrorListener
            extends ErrorList
        {
        public BranchedErrorListener(ErrorListener listener, int cMaxErrors)
            {
            super(cMaxErrors);

            f_listener = listener;
            }

        @Override
        public ErrorListener branch()
            {
            return new BranchedErrorListener(this, getSeriousErrorMax());
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
        }


    // ----- data members --------------------------------------------------------------------------

    /**
     * Maximum number of serious errors to tolerate before abandoning the process.
     */
    private int m_cMaxErrors;

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
    private ArrayList<ErrorInfo> m_list = new ArrayList<>();

    /**
     * The UIDs of previously logged errors.
     */
    private HashSet m_setUID = new HashSet();
    }
