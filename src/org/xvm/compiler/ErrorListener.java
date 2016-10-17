package org.xvm.compiler;


import org.xvm.util.Severity;


/**
 * A listener for errors being reported in the compilation process.
 *
 * @author cp 2015.11.13
 */
public interface ErrorListener
    {
    /**
     * Handles the logging of an error.
     *
     * @param severity    the severity level of the error; one of
     *                    {@link Severity#INFO}, {@link Severity#WARNING,
     *                    {@link Severity#ERROR}, or {@link Severity#FATAL}
     * @param sCode       the error code that identifies the error message
     * @param aoParam     the parameters for the error message; may be null
     * @param iLineStart  the line number where the error was detected
     * @param ofStart     the offset in the line where the error was detected
     * @param iLineEnd    the line number at which the error concluded
     * @param ofEnd       the offset in the line where the error concluded
     *
     * @return true to attempt to abort the process that reported the error, or
     *         false to attempt continue the process
     */
    public boolean log(Severity severity, String sCode, Object[] aoParam,
            int iLineStart, int ofStart, int iLineEnd, int ofEnd);
    }
