package org.xvm.asm;


import org.xvm.util.Severity;


/**
 * A listener for errors being reported about XVM structures, such as would
 * be reported by an assembler, a linker, or a runtime XVM verification
 * process.
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
     * @param xs          the XvmStructure that the error is related to; may
     *                    be null
     *
     * @return true to attempt to abort the process that reported the error, or
     *         false to attempt continue the process
     */
    public boolean log(Severity severity, String sCode, Object[] aoParam, XvmStructure xs);

    /**
     * A simple implementation of the ErrorListener that can be used at runtime. Errors will throw,
     * and non-errors will go to standard out.
     */
    public static final ErrorListener RUNTIME = new ErrorListener()
        {
        @Override
        public boolean log(Severity severity, String sCode, Object[] aoParam, XvmStructure xs)
            {
            String s = new ErrorList.ErrorInfo(severity, sCode, aoParam, xs).toString();
            if (severity.ordinal() >= Severity.ERROR.ordinal())
                {
                throw new IllegalStateException(s);
                }
            else
                {
                System.out.println(s);
                return false;
                }
            }
        };
    }
