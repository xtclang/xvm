package org.xvm.compiler.ast;


import org.xvm.asm.ConstantPool;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.MethodStructure.Code;
import org.xvm.asm.Op.Argument;
import org.xvm.asm.Register;

import org.xvm.asm.op.Label;
import org.xvm.asm.op.Nop;

import org.xvm.compiler.ErrorListener;
import org.xvm.compiler.Source;


/**
 * Base class for all Ecstasy statements.
 */
public abstract class Statement
        extends AstNode
    {
    // ----- accessors -----------------------------------------------------------------------------

    /**
     * @return the label corresponding to the beginning of the Statement
     */
    public Label getBeginLabel()
        {
        Label label = m_labelBegin;
        if (label == null)
            {
            assert !m_fEmitted;
            m_labelBegin = label = new Label();
            }
        return label;
        }

    /**
     * @return the label corresponding to the ending of the Statement
     */
    public Label getEndLabel()
        {
        Label label = m_labelEnd;
        if (label == null)
            {
            assert !m_fEmitted;
            m_labelEnd = label = new Label();
            }
        return label;
        }

    /**
     * Mark the statement as completing by short-circuiting.
     */
    public void shortCircuit()
        {
        m_fShortCircuited = true;
        }

    /**
     * This method is used to indicate to the statement that it is being used by an "if" or "while"
     * statement as the condition. This method must be invoked before the statement is validated.
     */
    public void markAsIfCondition(Label labelElse)
        {
        throw new IllegalStateException("not supported by " + getClass().getSimpleName());
        }

    /**
     * This method is used to indicate to the statement that it is being used by a "for"
     * statement as the condition. This method must be invoked before the statement is validated.
     */
    public void markAsForCondition(Label labelExit)
        {
        throw new IllegalStateException("not supported by " + getClass().getSimpleName());
        }


    // ----- compilation ---------------------------------------------------------------------------

    /**
     * Generate the generic assembly code that wraps the contents of any statement.
     *
     * @param ctx         the compilation context for the statement
     * @param fReachable  true iff the statement is reachable
     * @param code        the code object to which the assembly is added
     * @param errs        the error listener to log to
     *
     * @return true iff the statement completes
     */
    protected boolean completes(Context ctx, boolean fReachable, Code code, ErrorListener errs)
        {
        ctx.updateLineNumber(code, Source.calculateLine(getStartPosition()));

        boolean fBeginLabel = m_labelBegin != null;
        if (fBeginLabel)
            {
            code.add(m_labelBegin);
            }

        boolean fCompletes = fReachable & emit(ctx, fReachable, code, errs);

        // a being label should not have been requested during the emit stage unless it had been
        // requested previously (since it's too late to add it now!)
        assert fBeginLabel == (m_labelBegin != null);

        if (m_labelEnd != null)
            {
            code.add(m_labelEnd);
            }

        m_fEmitted = true;
        return fCompletes || m_fShortCircuited;
        }

    /**
     * Generate the statement-specific assembly code.
     *
     * @param ctx         the compilation context for the statement
     * @param fReachable  true iff the statement is reachable
     * @param code        the code object to which the assembly is added
     * @param errs        the error listener to log to
     *
     * @return true iff the statement completes
     */
    protected boolean emit(Context ctx, boolean fReachable, Code code, ErrorListener errs)
        {
        // TODO temporary -- this will be abstract
        throw notImplemented();
        }


    // ----- inner class: compiler Context ---------------------------------------------------------

    /**
     * Compiler context for compiling a method body.
     */
    public static class Context
        {
        public Context(MethodStructure method)
            {
            m_method = method;
            }

        /**
         * @return the MethodStructure that the context represents
         */
        public MethodStructure getMethod()
            {
            return m_method;
            }

        /**
         * @return the ConstantPool
         */
        public ConstantPool getPool()
            {
            return m_method.getConstantPool();
            }

        Context fork()
            {
            // TODO
            return null;
            }

        void join(Context... contexts)
            {
            // TODO
            }

        /**
         * Used in the validation phase to track scopes.
         */
        public void enterScope()
            {
            // TODO
            }

        public void registerVar(String sName, Register reg, ErrorListener errs)
            {
            // TODO
            }

        public Argument resolveName(String sName)
            {
            // TODO
            return null;
            }

        /**
         * Used in the validation phase to track scopes.
         */
        public void exitScope()
            {
            // TODO
            }

        public int getLineNumber()
            {
            return m_nLine;
            }

        public void updateLineNumber(Code code, int nLine)
            {
            if (nLine != m_nLine)
                {
                code.add(new Nop(nLine - m_nLine));
                m_nLine = nLine;
                }
            }

        private MethodStructure m_method;
        private int m_nLine = 0;
        }


    // ----- fields --------------------------------------------------------------------------------

    private Label   m_labelBegin;
    private Label   m_labelEnd;
    private boolean m_fShortCircuited;
    private boolean m_fEmitted;
    }
