package org.xvm.compiler.ast;


import java.util.HashMap;
import java.util.Map;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Component;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.Constants.Access;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.MethodStructure.Code;
import org.xvm.asm.Op;
import org.xvm.asm.Op.Argument;
import org.xvm.asm.Parameter;
import org.xvm.asm.Register;

import org.xvm.asm.constants.IdentityConstant;
import org.xvm.asm.constants.TypeConstant;
import org.xvm.asm.constants.PropertyConstant;

import org.xvm.asm.op.Label;

import org.xvm.compiler.Compiler;
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
     * Before generating the code for the method body, resolve names and verify definite assignment,
     * etc.
     *
     * @param ctx    the compilation context for the statement
     * @param errs   the error listener to log to
     *
     * @return true iff the compilation can proceed
     */
    protected boolean validate(Context ctx, ErrorListener errs)
        {
        throw notImplemented();
        }

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
            code.updateLineNumber(Source.calculateLine(getStartPosition()));
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
        Context(Context ctxOuter)
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
        public Context fork()
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
        public void join(Context... contexts)
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
         * Determine if the specified variable name is already declared in the current scope.
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
         * Determine if the name refers to a writable variable.
         *
         * @param sName  the name to resolve
         *
         * @return true iff the name refers to a variable, and the variable can be written to
         */
        public boolean isVarWritable(String sName)
            {
            return isVarDeclaredInThisScope(sName) || m_ctxOuter.isVarWritable(sName);
            }

        /**
         * Resolve the name of a variable, structure, etc.
         * <p/>
         * Note: This can only be used during the validate() stage.
         *
         * @param name  the name token to resolve
         *
         * @return the Argument representing the meaning of the name, or null
         */
        public Argument resolveName(Token name, ErrorListener errs)
            {
            Argument arg = resolveReservedName(name, errs);
            if (arg == null)
                {
                arg = resolveRegularName(name, errs);
                }
            return arg;
            }

        /**
         * Resolve a name (other than a reserved name) to an argument.
         *
         * @param name  the potentially reserved name token
         * @param errs  the error list to log errors to
         *
         * @return an Argument iff the name resolves to a reserved name; otherwise null
         */
        public Argument resolveRegularName(Token name, ErrorListener errs)
            {
            String                sName     = name.getValue().toString();
            Map<String, Argument> mapByName = m_mapByName;
            if (mapByName != null)
                {
                Argument arg = mapByName.get(sName);
                if (arg != null)
                    {
                    return arg;
                    }
                }

            return m_ctxOuter.resolveRegularName(name, errs);
            }

        /**
         * Resolve a reserved name to an argument.
         *
         * @param name  the potentially reserved name token
         * @param errs  the error list to log errors to
         *
         * @return an Argument iff the name resolves to a reserved name; otherwise null
         */
        public Argument resolveReservedName(Token name, ErrorListener errs)
            {
            return m_ctxOuter.resolveReservedName(name, errs);
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
        public Context fork()
            {
            checkValidating();
            throw new IllegalStateException();
            }

        @Override
        public void join(Context... contexts)
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
            Argument arg = ensureMethodParameters().get(sName);
            return arg instanceof Register &&
                    (((Register) arg).getIndex() >= 0 || ((Register) arg).isUnknown());
            }

        @Override
        public boolean isVarWritable(String sName)
            {
            Argument arg = ensureMethodParameters().get(sName);
            if (arg instanceof Register)
                {
                return ((Register) arg).isWritable();
                }

            if (arg instanceof PropertyConstant)
                {
                // the context only answers the question of what the _first_ name is, so
                // in the case of "a.b.c", this method is called only for "a", so if someone
                // is asking if it's settable, then obviously there is no ".b.c" following;
                // it's obviously a local property (since there is no "target." before it),
                // so the only check to do here is to make sure that it is settable, i.e. not
                // a calculated property
                // TODO: check for a calculated property
                return true;
                }
            return false;
            }

        @Override
        public Argument resolveRegularName(Token name, ErrorListener errs)
            {
            checkValidating();

            // check if the name is a parameter name, or a global name that has already been looked
            // up and cached
            String                sName     = name.getValue().toString();
            Map<String, Argument> mapByName = ensureMethodParameters();
            Argument              arg       = mapByName.get(sName);
            if (arg == null)
                {
                // resolve the name from outside of this statement
                arg = new NameResolver(m_stmtBody, sName).forceResolve(errs);
                if (arg != null)
                    {
                    // while the name resolved to something, we need to apply the rules of what that
                    // something means from this context
                    if (arg instanceof Constant)
                        {
                        Constant constant = (Constant) arg;
                        switch (constant.getFormat())
                            {
                            case Class:
                                if (!((ClassStructure) ((IdentityConstant) constant).getComponent()).isSingleton())
                                    {
                                    break;
                                    }
                                // fall through
                            case Module:
                            case Package:
                                // these are always singletons
                                arg = pool().ensureSingletonConstConstant((IdentityConstant) constant);
                                break;


                            case ThisClass:
                            case ParentClass:
                            case ChildClass:
                                // TODO not sure how to handle these yet, but should be easy ...
                                throw new UnsupportedOperationException("constant=" + constant);
                            }
                        }

                    mapByName.put(sName, arg);
                    }
                }

            return arg;
            }

        @Override
        public Argument resolveReservedName(Token name, ErrorListener errs)
            {
            checkValidating();

            String       sName = name.getValue().toString();
            boolean      fThis = false;
            ConstantPool pool  = pool();
            TypeConstant type;
            int          nReg;
            switch (sName)
                {
                case "this":
                case "this:target":
                    type  = getThisType();
                    nReg  = Op.A_TARGET;
                    fThis = true;
                    break;

                case "this:private":
                    type  = pool.ensureAccessTypeConstant(getThisType(), Access.PRIVATE);
                    nReg  = Op.A_PRIVATE;
                    fThis = true;
                    break;

                case "this:protected":
                    type  = pool.ensureAccessTypeConstant(getThisType(), Access.PROTECTED);
                    nReg  = Op.A_PROTECTED;
                    fThis = true;
                    break;

                case "this:public":
                    type  = pool.ensureAccessTypeConstant(getThisType(), Access.PUBLIC);
                    nReg  = Op.A_PUBLIC;
                    fThis = true;
                    break;

                case "this:struct":
                    type  = pool.ensureAccessTypeConstant(getThisType(), Access.STRUCT);
                    nReg  = Op.A_STRUCT;
                    fThis = true;
                    break;

                case "this:type":
                    type  = pool.ensureParameterizedTypeConstant(pool.typeType(), getThisType());
                    nReg  = Op.A_TYPE;
                    fThis = true;
                    break;

                case "this:frame":
                    type = pool.typeFrame();
                    nReg = Op.A_FRAME;
                    break;

                case "this:module":
                    type = getModuleType();
                    nReg = Op.A_MODULE;
                    break;

                case "this:service":
                    type = pool.typeService();
                    nReg = Op.A_SERVICE;
                    break;

                case "super":
                    // TODO need to verify that there is a super
                    type = pool().typeFunction(); // TODO need the actual sig of the super function
                    nReg = Op.A_SUPER;
                    break;

                default:
                    return null;
                }

            if (fThis && isStatic() && !m_fLoggedNoThis)
                {
                name.log(errs, getSource(), Severity.ERROR, Compiler.NO_THIS, sName);
                m_fLoggedNoThis = true;
                }

            Map<String, Argument> mapByName = ensureMethodParameters();
            Argument              arg       = mapByName.get(sName);
            if (arg == null)
                {
                arg = new Register(type, nReg);
                }

            return arg;
            }

        protected Map<String, Argument> ensureMethodParameters()
            {
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

            return mapByName;
            }

        boolean isStatic()
            {
            Component parent = m_method;
            while (true)
                {
                switch (parent.getFormat())
                    {
                    case INTERFACE:
                    case CLASS:
                    case CONST:
                    case ENUM:
                    case ENUMVALUE:
                    case MIXIN:
                    case TRAIT:
                    case SERVICE:
                    case PACKAGE:
                    case MODULE:
                        return false;

                    case METHOD:
                        if (parent.isStatic())
                            {
                            return true;
                            }
                        break;

                    case PROPERTY:
                    case MULTIMETHOD:
                        break;

                    default:
                        throw new IllegalStateException();
                    }

                parent = parent.getParent();
                }
            }

        TypeConstant getThisType()
            {
            Component parent = m_method;
            while (true)
                {
                switch (parent.getFormat())
                    {
                    case INTERFACE:
                    case CLASS:
                    case CONST:
                    case ENUM:
                    case ENUMVALUE:
                    case MIXIN:
                    case TRAIT:
                    case SERVICE:
                    case PACKAGE:
                    case MODULE:
                        return ((ClassStructure) parent).getFormalType();

                    case METHOD:
                    case PROPERTY:
                    case MULTIMETHOD:
                        break;

                    default:
                        throw new IllegalStateException();
                    }

                parent = parent.getParent();
                }
            }

        TypeConstant getModuleType()
            {
            Component parent = m_method;
            while (true)
                {
                switch (parent.getFormat())
                    {
                    case MODULE:
                        return ((ClassStructure) parent).getFormalType();

                    case INTERFACE:
                    case CLASS:
                    case CONST:
                    case ENUM:
                    case ENUMVALUE:
                    case MIXIN:
                    case TRAIT:
                    case SERVICE:
                    case PACKAGE:
                    case METHOD:
                    case PROPERTY:
                    case MULTIMETHOD:
                        break;

                    default:
                        throw new IllegalStateException();
                    }

                parent = parent.getParent();
                }
            }

        @Override
        public Context exitScope()
            {
            checkValidating();
            throw new IllegalStateException();
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
        private boolean         m_fLoggedNoThis;
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
