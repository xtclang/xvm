package org.xvm.compiler.ast;


import java.util.Map;
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

    public interface Breakable
        {
        Label getBreakLabel();
        }

    public interface Continuable
        {
        Label getContinueLabel();
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
        if (fReachable)
            {
            ctx.updateLineNumber(code, Source.calculateLine(getStartPosition()));
            }
        else
            {
            code = code.blackhole();
            }

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
        return fCompletes || fReachable && m_fShortCircuited;
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
    public abstract static class Context
        {
        public Context(Context ctxOuter)
            {
            m_ctxOuter = ctxOuter;
            }

        /**
         * @return the MethodStructure that the context represents
         */
        public MethodStructure getMethod()
            {
            return m_ctxOuter.getMethod();
            }

        /**
         * @return the ConstantPool
         */
        public ConstantPool pool()
            {
            return m_ctxOuter.pool();
            }

        /**
         * TODO
         * <p/>
         * Note: This can only be used during the validate() stage.
         *
         * @return the new (forked) context
         */
        Context fork()
            {
            return new NestedContext(this);
            }

        /**
         * TODO
         * <p/>
         * Note: This can only be used during the validate() stage.
         *
         * @param contexts  the previously forked contexts
         */
        void join(Context... contexts)
            {
            // TODO
            }

        /**
         * Used in the validation phase to track scopes.
         * <p/>
         * Note: This can only be used during the validate() stage.
         */
        public void enterScope()
            {
            // TODO
            }

        /**
         * TODO
         * <p/>
         * Note: This can only be used during the validate() stage.
         *
         * @param sName
         * @param reg
         * @param errs
         */
        public void registerVar(String sName, Register reg, ErrorListener errs)
            {
            // TODO
            }

        /**
         * TODO
         * <p/>
         * Note: This can only be used during the validate() stage.
         *
         * @param sName
         *
         * @return
         */
        public Argument resolveName(String sName)
            {
            // TODO
            return null;
            }

        /**
         * Used in the validation phase to track scopes.
         * <p/>
         * Note: This can only be used during the validate() stage.
         */
        public void exitScope()
            {
            // TODO
            }

        /**
         * Determine the current line number
         * <p/>
         * Note: This can only be used during the emit() stage.
         *
         * @return the current line number
         */
        public int getLineNumber()
            {
            return m_nLine;
            }

        /**
         * Update the line number in the source code.
         * <p/>
         * Note: This can only be used during the emit() stage.
         *
         * @param code   the code being emitted to
         * @param nLine  the new line number
         */
        public void updateLineNumber(Code code, int nLine)
            {
            if (nLine != m_nLine)
                {
                code.add(new Nop(nLine - m_nLine));
                m_nLine = nLine;
                }
            }

        private MethodStructure m_method;

        Context         m_ctxOuter;
        Map<String, Register> m_mapVars;

        private int m_nLine = 0;
        }

    /**
     * Compiler context for compiling a method body.
     */
    public abstract static class Context
        {
        public Context(Context ctxOuter)
            {
            m_ctxOuter = ctxOuter;
            }

        /**
         * @return the MethodStructure that the context represents
         */
        public MethodStructure getMethod()
            {
            return m_ctxOuter.getMethod();
            }

        /**
         * @return the ConstantPool
         */
        public ConstantPool pool()
            {
            return m_ctxOuter.pool();
            }

        /**
         * TODO
         * <p/>
         * Note: This can only be used during the validate() stage.
         *
         * @return the new (forked) context
         */
        Context fork()
            {
            return new NestedContext(this);
            }

        /**
         * TODO
         * <p/>
         * Note: This can only be used during the validate() stage.
         *
         * @param contexts  the previously forked contexts
         */
        void join(Context... contexts)
            {
            // TODO
            }

        /**
         * Used in the validation phase to track scopes.
         * <p/>
         * Note: This can only be used during the validate() stage.
         */
        public void enterScope()
            {
            // TODO
            }

        /**
         * TODO
         * <p/>
         * Note: This can only be used during the validate() stage.
         *
         * @param sName
         * @param reg
         * @param errs
         */
        public void registerVar(String sName, Register reg, ErrorListener errs)
            {
            // TODO
            }

        /**
         * TODO
         * <p/>
         * Note: This can only be used during the validate() stage.
         *
         * @param sName
         *
         * @return
         */
        public Argument resolveName(String sName)
            {
            // TODO
            return null;
            }

        /**
         * Used in the validation phase to track scopes.
         * <p/>
         * Note: This can only be used during the validate() stage.
         */
        public void exitScope()
            {
            // TODO
            }

        /**
         * Determine the current line number
         * <p/>
         * Note: This can only be used during the emit() stage.
         *
         * @return the current line number
         */
        public int getLineNumber()
            {
            return m_nLine;
            }

        /**
         * Update the line number in the source code.
         * <p/>
         * Note: This can only be used during the emit() stage.
         *
         * @param code   the code being emitted to
         * @param nLine  the new line number
         */
        public void updateLineNumber(Code code, int nLine)
            {
            if (nLine != m_nLine)
                {
                code.add(new Nop(nLine - m_nLine));
                m_nLine = nLine;
                }
            }

        private MethodStructure m_method;

        Context         m_ctxOuter;
        Map<String, Register> m_mapVars;

        private int m_nLine = 0;
        }

    /**
     * A nested context, representing a separate scope and/or code path.
     */
    public static class NestedContext
            extends Context
        {
        public NestedContext(Context ctxOuter)
            {
            super(ctxOuter.getMethod());
            m_ctxOuter = ctxOuter;
            }

        @Override
        public int getLineNumber()
            {
            throw new IllegalStateException();
            }

        @Override
        public void updateLineNumber(Code code, int nLine)
            {
            throw new IllegalStateException();
            }

        Context m_ctxOuter;
        }


    // ----- fields --------------------------------------------------------------------------------

    private Label   m_labelBegin;
    private Label   m_labelEnd;
    private boolean m_fShortCircuited;
    private boolean m_fEmitted;
    }
