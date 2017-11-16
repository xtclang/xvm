package org.xvm.compiler.ast;


import java.util.HashMap;
import java.util.Map;

import org.xvm.asm.ConstantPool;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.MethodStructure.Code;
import org.xvm.asm.Op.Argument;
import org.xvm.asm.Parameter;
import org.xvm.asm.Register;

import org.xvm.asm.op.Label;
import org.xvm.asm.op.Nop;

import org.xvm.compiler.Compiler;
import org.xvm.compiler.ErrorListener;
import org.xvm.compiler.Source;
import org.xvm.compiler.Token;

import org.xvm.util.Severity;


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
        /**
         * Construct a Context.
         *
         * @param ctxOuter  the context that this Context is nested within
         */
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
         * @return the source for the method
         */
        public Source getSource()
            {
            return m_ctxOuter.getSource();
            }

        /**
         * @return the ConstantPool
         */
        public ConstantPool pool()
            {
            return m_ctxOuter.pool();
            }

        /**
         * Create a nested fork of this context.
         * <p/>
         * Note: This can only be used during the validate() stage.
         *
         * @return the new (forked) context
         */
        Context fork()
            {
            checkForkable();

            m_ctxInner = this;
            return new NestedContext(this); // TODO could have a special "ForkedContext" impl if necessary
            }

        /**
         * Join multiple forks of this context back together.
         * <p/>
         * Note: This can only be used during the validate() stage.
         *
         * @param contexts  the previously forked contexts
         */
        void join(Context... contexts)
            {
            checkForked();

            for (Context ctx : contexts)
                {
                if (ctx.m_ctxOuter != this)
                    {
                    throw new IllegalStateException("not a fork of this context");
                    }
                }

            // TODO merge info

            m_ctxInner = null;
            }

        /**
         * Used in the validation phase to track scopes.
         * <p/>
         * Note: This can only be used during the validate() stage.
         */
        public Context enterScope()
            {
            checkInnermost();

            Context ctxInner = new NestedContext(this);
            m_ctxInner = ctxInner;
            return ctxInner;
            }

        /**
         * Register the specified variable name in this context.
         * <p/>
         * Note: This can only be used during the validate() stage.
         *
         * @param tokName
         * @param reg
         * @param errs
         */
        public void registerVar(Token tokName, Register reg, ErrorListener errs)
            {
            checkInnermost();

            String sName = tokName.getValue().toString();
            if (isVarDeclaredInThisScope(sName))
                {
                tokName.log(errs, getSource(), Severity.ERROR, Compiler.VAR_DEFINED, sName);
                }

            Map<String, Argument> mapByName = m_mapByName;
            if (mapByName == null)
                {
                m_mapByName = mapByName = new HashMap<>();
                }
            mapByName.put(sName, reg);
            }

        /**
         * Determine if the specified variable name is alread declared in the current scope.
         * <p/>
         * Note: This can only be used during the validate() stage.
         *
         * @param sName  the variable name
         *
         * @return true iff a variable of that name is already declared in this scope
         */
        public boolean isVarDeclaredInThisScope(String sName)
            {
            return m_mapByName != null && m_mapByName.containsKey(sName);
            }

        /**
         * Resolve the name of a variable, structure, etc.
         * <p/>
         * Note: This can only be used during the validate() stage.
         *
         * @param sName  the name to resolve
         *
         * @return the Argument representing the meaning of the name, or null
         */
        public Argument resolveName(String sName)
            {
            Map<String, Argument> mapByName = m_mapByName;
            if (mapByName != null)
                {
                Argument arg = mapByName.get(sName);
                if (arg != null)
                    {
                    return arg;
                    }
                }

            return m_ctxOuter.resolveName(sName);
            }

        /**
         * Exit the scope that was created by calling {@link #enterScope()}. Used in the validation
         * phase to track scopes.
         * <p/>
         * Note: This can only be used during the validate() stage.
         */
        public Context exitScope()
            {
            checkInnermost();

            Context ctxOuter = m_ctxOuter;
            assert ctxOuter.m_ctxInner == this;

            // TODO copy variable assignment information from this scope to outer scope

            m_ctxOuter = null;
            ctxOuter.m_ctxInner = null;
            return ctxOuter;
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
            throw new IllegalStateException();
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
            throw new IllegalStateException();
            }

        /**
         * Verify that this is the innermost context.
         */
        void checkInnermost()
            {
            if (m_ctxInner != null)
                {
                throw new IllegalStateException();
                }
            }

        /**
         * Verify that this is a forkable context.
         */
        void checkForkable()
            {
            if (m_ctxInner != null && m_ctxInner != this)
                {
                throw new IllegalStateException();
                }
            }

        /**
         * Verify that this is a forked context.
         */
        void checkForked()
            {
            if (m_ctxInner != this)
                {
                throw new IllegalStateException();
                }
            }

        Context               m_ctxOuter;
        Context               m_ctxInner;
        Map<String, Argument> m_mapByName;
        }

    /**
     * The outermost compiler context for compiling a method body. This context maintains a link
     * with the method body that is being compiled, and represents the parameters to the method and
     * the global names visible to the method.
     */
    public static class RootContext
            extends Context
        {
        public RootContext(MethodStructure method, StatementBlock stmtBody)
            {
            super(null);

            m_method   = method;
            m_stmtBody = stmtBody;
            }

        @Override
        public MethodStructure getMethod()
            {
            return m_method;
            }

        @Override
        public Source getSource()
            {
            return m_stmtBody.getSource();
            }

        @Override
        public ConstantPool pool()
            {
            return m_method.getConstantPool();
            }

        @Override
        Context fork()
            {
            checkValidating();
            throw new IllegalStateException();
            }

        @Override
        void join(Context... contexts)
            {
            checkValidating();
            throw new IllegalStateException();
            }

        @Override
        public Context enterScope()
            {
            checkValidating();
            throw new IllegalStateException();
            }

        @Override
        public void registerVar(Token tokName, Register reg, ErrorListener errs)
            {
            checkValidating();
            throw new IllegalStateException();
            }

        @Override
        public boolean isVarDeclaredInThisScope(String sName)
            {
            if (!super.isVarDeclaredInThisScope(sName))
                {
                return false;
                }

            Argument arg = m_mapByName.get(sName);
            return arg instanceof Register && ((Register) arg).getIndex() >= 0;
            }

        @Override
        public Argument resolveName(String sName)
            {
            checkValidating();

            Map<String, Argument> mapByName = m_mapByName;
            if (mapByName == null)
                {
                mapByName = new HashMap<>();

                MethodStructure method = m_method;
                for (int i = 0, c = method.getParamCount(); i < c; ++i)
                    {
                    Parameter param = method.getParam(i);
                    mapByName.put(param.getName(), new Register(param.getType(), i));
                    }

                m_mapByName = mapByName;
                }

            // check if the name is a parameter name, or a global name that has already been looked
            // up and cached
            Argument arg = mapByName.get(sName);
            if (arg == null)
                {
                // TODO - resolve name, then cache it in the map

                if (arg != null)
                    {
                    mapByName.put(sName, arg);
                    }
                }

            return arg;
            }

        @Override
        public Context exitScope()
            {
            checkValidating();
            throw new IllegalStateException();
            }

        @Override
        public int getLineNumber()
            {
            checkEmitting();
            return m_nLine;
            }

        @Override
        public void updateLineNumber(Code code, int nLine)
            {
            checkEmitting();
            if (nLine != m_nLine)
                {
                code.add(new Nop(nLine - m_nLine));
                m_nLine = nLine;
                }
            }

        /**
         * @return a Context that can be used while validating code
         */
        public Context validatingContext()
            {
            checkValidating();
            return super.enterScope();
            }

        /**
         * @return a Context that can be used while emitting code
         */
        public Context emittingContext()
            {
            checkValidating();
            m_ctxInner.exitScope();
            m_fEmitting = true;
            return this;
            }

        private void checkValidating()
            {
            if (m_fEmitting)
                {
                throw new IllegalStateException();
                }
            }

        private void checkEmitting()
            {
            if (!m_fEmitting)
                {
                throw new IllegalStateException();
                }
            }

        private MethodStructure m_method;
        private StatementBlock  m_stmtBody;
        private boolean         m_fEmitting;
        private int             m_nLine;
        }

    /**
     * A nested context, representing a separate scope and/or code path.
     */
    public static class NestedContext
            extends Context
        {
        public NestedContext(Context ctxOuter)
            {
            super(ctxOuter);
            }

        // TODO whatever info needs to be accumulated for a nested or forked context, i.e. definite assignment info
        }


    // ----- fields --------------------------------------------------------------------------------

    private Label   m_labelBegin;
    private Label   m_labelEnd;
    private boolean m_fShortCircuited;
    private boolean m_fEmitted;
    }
