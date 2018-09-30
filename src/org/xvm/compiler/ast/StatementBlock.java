package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.xvm.asm.Argument;
import org.xvm.asm.ClassStructure;
import org.xvm.asm.Component;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.Constants.Access;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.MethodStructure.Code;
import org.xvm.asm.ModuleStructure;
import org.xvm.asm.Op;
import org.xvm.asm.Register;
import org.xvm.asm.Register.Assignment;

import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.MethodInfo;
import org.xvm.asm.constants.PropertyConstant;
import org.xvm.asm.constants.TypeConstant;
import org.xvm.asm.constants.TypeInfo;

import org.xvm.asm.op.Enter;
import org.xvm.asm.op.Exit;
import org.xvm.asm.op.Nop;
import org.xvm.asm.op.Return_0;
import org.xvm.asm.op.Var_C;

import org.xvm.compiler.Compiler;
import org.xvm.compiler.Source;
import org.xvm.compiler.Token;
import org.xvm.compiler.Token.Id;

import org.xvm.util.Severity;


/**
 * A block statement specifies a series of statements.
 * <p/>
 * A block statement holds a special role in compilation, in that the four forms of "compilation
 * container" all rely on the block statement as the representation of the code being compiled:
 * <ol>
 * <li>MethodDeclarationStatement - represents a method or function, with a body defined by a
 *     StatementBlock.</li>
 * <li>NewExpression - (optionally) represents an anonymous inner class, defined within a
 *     StatementBlock.</li>
 * <li>LambdaExpression - represents a lambda function, whose body is represented by a
 *     StatementBlock.</li>
 * <li>StatementExpression - represents an "inlined" lambda, represented by a StatementBlock, with
 *     the resulting value of the expression being "returned" from one or more return statements
 *     inside of the StatementBlock.</li>
 * </ol>
 */
public class StatementBlock
        extends Statement
    {
    // ----- constructors --------------------------------------------------------------------------

    public StatementBlock(List<Statement> stmts)
        {
        this(stmts, null,
                stmts.isEmpty() ? 0L : stmts.get(0).getStartPosition(),
                stmts.isEmpty() ? 0L : stmts.get(stmts.size()-1).getEndPosition());
        }

    public StatementBlock(List<Statement> stmts, long lStartPos, long lEndPos)
        {
        this(stmts, null, lStartPos, lEndPos);
        }

    public StatementBlock(List<Statement> stmts, Source source, long lStartPos, long lEndPos)
        {
        this.stmts     = stmts;
        this.source    = source;
        this.lStartPos = lStartPos;
        this.lEndPos   = lEndPos;
        }


    // ----- accessors -----------------------------------------------------------------------------

    public List<Statement> getStatements()
        {
        return stmts;
        }

    public void addStatement(Statement stmt)
        {
        stmt.setParent(this);

        boolean fHasEnclosed = containsEnclosed;
        boolean fAddEnclosed = stmt instanceof StatementBlock && ((StatementBlock) stmt).boundary;
        assert !(fHasEnclosed & fAddEnclosed);
        if (fHasEnclosed)
            {
            // insert the new statement before the "enclosed" statements
            stmts.add(stmts.size()-1, stmt);
            }
        else
            {
            stmts.add(stmt);
            containsEnclosed |= fAddEnclosed;
            }
        }

    /**
     * Mark the statement block as representing a file boundary, such that the parent (if any) and
     * each of the child (if any) statements are each assumed to be from separate files.
     */
    protected void markFileBoundary()
        {
        boundary = true;
        }

    /**
     * @return true if this StatementBlock has been marked as a file boundary
     */
    public boolean isFileBoundary()
        {
        return boundary;
        }

    /**
     * Indicate that the StatementBlock should explicitly suppress its ENTER/EXIT scope.
     */
    public void suppressScope()
        {
        m_fSuppressScope = true;
        }

    /**
     * @return true iff the StatementBlock has its own ENTER/EXIT scope
     */
    public boolean hasScope()
        {
        return !m_fSuppressScope;
        }

    /**
     * Register an import statement that occurs within this StatementBlock.
     *
     * @param stmt  the ImportStatement to register
     * @param errs  the ErrorListener to use to log any errors
     */
    protected void registerImport(ImportStatement stmt, ErrorListener errs)
        {
        if (imports == null)
            {
            imports = new HashMap<>();
            }

        // make sure that no existing import uses the same alias
        String sAlias = stmt.getAliasName();
        if (imports.containsKey(sAlias))
            {
            log(errs, Severity.ERROR, Compiler.DUPLICATE_IMPORT, sAlias);
            // fall through; don't stop compilation at this point, and just use the new import to
            // overwrite the old
            }

        imports.put(stmt.getAliasName(), stmt);
        }

    /**
     * Obtain the ImportStatement for a particular import alias. This method has different behaviors
     * depending on the phase of compilation. During the phase in which the imports are registered,
     * this will only provide an answer for the imports that have already been registered. For
     * example, the {@link AstNode#resolveNames(StageMgr, ErrorListener)} method
     * is used to resolve all global names (all names, down to the method level, but not resolving
     * within any methods), and thus imports outside of methods are all registered during that
     * phase, such that only the ones registered will be visible via this method. The reason for
     * this approach is that imports are not visible outside of a file, and furthermore, because
     * they can occur at any point within the file, only those encountered "above" some current
     * point in that file are considered to be visible at that point.
     *
     * @param sName  the import alias
     *
     * @return the ImportStatement, or null
     */
    public ImportStatement getImport(String sName)
        {
        return imports == null
                ? null
                : imports.get(sName);
        }

    @Override
    protected AstNode getCodeContainer()
        {
        AstNode parent = getParent();
        if (       parent instanceof MethodDeclarationStatement
                || parent instanceof NewExpression
                || parent instanceof LambdaExpression
                || parent instanceof StatementExpression)
            {
            return parent;
            }

        return super.getCodeContainer();
        }

    @Override
    public Source getSource()
        {
        return source == null
                ? super.getSource()
                : source;
        }

    @Override
    public long getStartPosition()
        {
        return lStartPos;
        }

    @Override
    public long getEndPosition()
        {
        return lEndPos;
        }

    @Override
    protected Field[] getChildFields()
        {
        return CHILD_FIELDS;
        }


    // ----- compilation ---------------------------------------------------------------------------

    /**
     * Generate assembly code for a method. This is the entry point for the compilation of a method.
     *
     * @param code  the code object to which the assembly is added
     * @param errs  the error listener to log to
     */
    public void compileMethod(Code code, ErrorListener errs)
        {
        RootContext ctx = new RootContext(code.getMethodStructure());

        Statement that = this.validate(ctx.validatingContext(), errs);
        if (that != null && !errs.isAbortDesired())
            {
            boolean fCompletes = that.completes(ctx.emittingContext(), true, code, errs);

            if (fCompletes)
                {
                if (code.getMethodStructure().getReturns().isEmpty())
                    {
                    // a void method has an implicit "return;" at the end of it
                    code.add(new Return_0());
                    }
                else
                    {
                    errs.log(Severity.ERROR, Compiler.RETURN_REQUIRED, null, source,
                            getEndPosition(), getEndPosition());
                    }
                }
            else
                {
                // it is is possible that there is a dangling label at the end that is unreachable,
                // and it will not have been eliminated at this point, so "cap" the op code stream
                // with a Nop that will get removed by "dead code elimination"
                code.add(new Nop());
                }
            }
        }

    @Override
    protected Statement validate(Context ctx, ErrorListener errs)
        {
        List<Statement> stmts  = this.stmts;
        boolean         fValid = true;
        if (stmts != null && !stmts.isEmpty())
            {
            if (hasScope())
                {
                ctx = ctx.enter();
                }

            if (getParent() instanceof LambdaExpression)
                {
                // go through all of the parameters looking for any implicit de-reference params
                // (a new local variable will be created for each, effectively hiding the original
                // parameter)
                for (org.xvm.asm.Parameter param : ctx.getMethod().getParamArray())
                    {
                    if (param.isImplicitDeref())
                        {
                        String     sName  = param.getName();
                        Register   regVar = (Register) ctx.getVar(sName);
                        Assignment asnVar = ctx.getVarAssignment(sName);
                        Register regVal = param.deref(regVar);
                        ctx.ensureNameMap().put(sName, regVal); // shadow using the capture
                        ctx.setVarAssignment(sName, asnVar);    // ... and copy its assignment
                        }
                    }
                }

            for (int i = 0, c = stmts.size(); i < c; ++i)
                {
                Statement stmtOld = stmts.get(i);
                Statement stmtNew = stmtOld.validate(ctx, errs);
                if (stmtNew != stmtOld)
                    {
                    if (stmtNew == null)
                        {
                        fValid = false;
                        }
                    else
                        {
                        this.stmts = ensureArrayList(stmts);
                        stmts.set(i, stmtNew);
                        }
                    }

                if (errs.isAbortDesired())
                    {
                    break;
                    }
                }

            if (hasScope())
                {
                ctx = ctx.exit();
                }
            }

        return fValid ? this : null;
        }

    @Override
    protected boolean emit(Context ctx, boolean fReachable, Code code, ErrorListener errs)
        {
        boolean fCompletable = fReachable;

        List<Statement> stmts = this.stmts;
        if (stmts != null && !stmts.isEmpty())
            {
            // there is an implicit scope for the top-most statement block of a method
            AstNode parent         = getParent();
            boolean fMethod        = parent instanceof MethodDeclarationStatement;
            boolean fLambda        = parent instanceof LambdaExpression;
            boolean fSuppressScope = fMethod | fLambda | m_fSuppressScope;

            if (fLambda)
                {
                // go through all of the parameters looking for any implicit de-reference params
                // (a new local variable will be created for each, effectively hiding the original
                // parameter)
                for (org.xvm.asm.Parameter param : ctx.getMethod().getParamArray())
                    {
                    if (param.isImplicitDeref())
                        {
                        Register regVar = (Register) ctx.getVar(param.getName());
                        Register regVal = param.deref(regVar);
                        code.add(new Var_C(regVal, regVar));
                        }
                    }
                }
            else if (!fSuppressScope)
                {
                code.add(new Enter());
                }

            for (Statement stmt : stmts)
                {
                if (fReachable && !fCompletable)
                    {
                    // this statement is the first statement that cannot be reached
                    fReachable = false;
                    stmt.log(errs, Severity.ERROR, Compiler.NOT_REACHABLE);
                    }

                fCompletable &= stmt.completes(ctx, fReachable, code, errs);
                }

            if (!fSuppressScope)
                {
                code.add(new Exit());
                }
            }

        return fCompletable;
        }


    // ----- name resolution -----------------------------------------------------------------------

    @Override
    protected ImportStatement resolveImportBySingleName(String sName)
        {
        // if this is a synthetic block statement that acts as a collection of multiple files, then
        // the search for the import has just crossed a file boundary, and nothing was found
        if (isFileBoundary())
            {
            return null;
            }

        ImportStatement stmtImport = getImport(sName);
        return stmtImport == null
                ? super.resolveImportBySingleName(sName)
                : stmtImport;
        }


    // ----- debugging assistance ------------------------------------------------------------------

    @Override
    public String toString()
        {
        if (stmts == null || stmts.isEmpty())
            {
            return "{}";
            }

        StringBuilder sb = new StringBuilder();
        sb.append('{');

        int firstNonEnum = 0;
        if (stmts.get(0) instanceof TypeCompositionStatement
                && ((TypeCompositionStatement) stmts.get(0)).category.getId() == Token.Id.ENUM_VAL)
            {
            boolean multiline = false;
            for (int i = 0, c = stmts.size(); i < c; ++i)
                {
                Statement stmt = stmts.get(i);
                if (stmt instanceof TypeCompositionStatement
                        && ((TypeCompositionStatement) stmt).category.getId() == Token.Id.ENUM_VAL)
                    {
                    TypeCompositionStatement enumStmt = (TypeCompositionStatement) stmt;
                    multiline |= enumStmt.doc != null || enumStmt.body != null;
                    ++firstNonEnum;
                    }
                }

            String sBetweenEnums = multiline ? ",\n" : ", ";
            for (int i = 0; i < firstNonEnum; ++i)
                {
                if (i == 0)
                    {
                    sb.append('\n');
                    }
                else
                    {
                    sb.append(sBetweenEnums);
                    }
                sb.append(stmts.get(i));
                }
            if (firstNonEnum < stmts.size())
                {
                sb.append(';');
                }
            }

        for (int i = firstNonEnum, c = stmts.size(); i < c; ++i)
            {
            sb.append('\n')
              .append(stmts.get(i));
            }
        sb.append("\n}");

        return sb.toString();
        }


    // ----- inner class: RootContext --------------------------------------------------------------

    /**
     * The outermost compiler context for compiling a method body. This context maintains a link
     * with the method body that is being compiled, and represents the parameters to the method and
     * the global names visible to the method.
     */
    public class RootContext
            extends Context
        {
        public RootContext(MethodStructure method)
            {
            super(null);
            m_method = method;
            }

        @Override
        public MethodStructure getMethod()
            {
            return m_method;
            }

        public StatementBlock getStatementBlock()
            {
            return StatementBlock.this;
            }

        @Override
        public Source getSource()
            {
            return StatementBlock.this.getSource();
            }

        @Override
        public ClassStructure getThisClass()
            {
            Component parent = m_method;
            while (!(parent instanceof ClassStructure))
                {
                parent = parent.getParent();
                }
            return (ClassStructure) parent;
            }

        @Override
        public ConstantPool pool()
            {
            return m_method.getConstantPool();
            }

        @Override
        public Context enterFork(boolean fWhenTrue)
            {
            checkValidating();
            throw new IllegalStateException();
            }

        @Override
        public Context enter()
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
            Argument arg = getNameMap().get(sName);
            if (arg instanceof Register)
                {
                Register reg = (Register) arg;
                return reg.getIndex() >= 0 || reg.isUnknown();
                }

            return false;
            }

        @Override
        public Assignment getVarAssignment(String sName)
            {
            if (isReservedName(sName))
                {
                return isReservedNameReadable(sName)
                        ? Assignment.AssignedOnce
                        : Assignment.Unassigned;
                }

            return super.getVarAssignment(sName);
            }

        @Override
        public boolean isVarReadable(String sName)
            {
            Assignment asn = getVarAssignment(sName);
            if (asn != null)
                {
                return asn.isDefinitelyAssigned();
                }

            // the only other readable variable names are reserved variables
            return isReservedName(sName) && isReservedNameReadable(sName);
            }

        /**
         * Determine if the specified reserved name is available (has a value) in this context.
         *
         * @param sName  the reserved name
         *
         * @return true iff the reserved name is accessible in this context
         */
        public boolean isReservedNameReadable(String sName)
            {
            checkValidating();

            switch (sName)
                {
                case "this":
                case "this:struct":
                    return isMethod() || isConstructor();

                case "this:target":
                case "this:public":
                case "this:protected":
                case "this:private":
                    return isMethod();

                case "this:module":
                case "this:service":
                    return true;

                case "super":
                {
                TypeConstant   typePro    = pool().ensureAccessTypeConstant(getThisType(), Access.PROTECTED);
                TypeInfo       info       = typePro.ensureTypeInfo();
                MethodConstant idMethod   = getMethod().getIdentityConstant();
                MethodInfo     infoMethod = info.getMethodById(idMethod);
                return infoMethod.hasSuper(info);
                }

                default:
                    throw new IllegalArgumentException("no such reserved name: " + sName);
                }
            }

        @Override
        public boolean isVarWritable(String sName)
            {
            Argument arg = ensureNameMap().get(sName);
            if (arg instanceof Register)
                {
                return ((Register) arg).isWritable();
                }
            else if (arg instanceof PropertyConstant)
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
        protected Argument resolveRegularName(String sName, Token name, ErrorListener errs)
            {
            checkValidating();

            // check if the name is a parameter name, or a global name that has already been looked
            // up and cached
            Map<String, Argument> mapByName = ensureNameMap();
            Argument              arg       = mapByName.get(sName);
            if (arg == null)
                {
                // resolve the name from outside of this statement
                arg = new NameResolver(StatementBlock.this, sName)
                        .forceResolve(errs == null ? ErrorListener.BLACKHOLE : errs);
                if (arg != null)
                    {
                    mapByName.put(sName, arg);
                    }
                }

            return arg;
            }

        @Override
        protected Argument getVar(String sName, Token name, ErrorListener errs)
            {
            Argument arg = super.getVar(sName, name, errs);
            return arg == null
                    ? resolveReservedName(sName, name, errs)
                    : arg;
            }

        @Override
        protected Argument resolveReservedName(String sName, Token name, ErrorListener errs)
            {
            Map<String, Argument> mapByName = ensureNameMap();
            Argument              arg       = mapByName.get(sName);
            if (arg instanceof Register && ((Register) arg).isPredefined())
                {
                return arg;
                }

            ConstantPool pool = pool();
            TypeConstant type;
            int          nReg;
            switch (sName)
                {
                case "this":
                    if (isConstructor())
                        {
                        type = pool.ensureAccessTypeConstant(getThisType(), Access.STRUCT);
                        nReg = Op.A_STRUCT;
                        break;
                        }
                    // fall through
                case "this:target":
                    type = getThisType();
                    nReg = Op.A_TARGET;
                    break;

                case "this:public":
                    type = getThisType();
                    assert type.getAccess() == Access.PUBLIC;
                    nReg = Op.A_PUBLIC;
                    break;

                case "this:protected":
                    type = pool.ensureAccessTypeConstant(getThisType(), Access.PROTECTED);
                    nReg = Op.A_PROTECTED;
                    break;

                case "this:private":
                    type = pool.ensureAccessTypeConstant(getThisType(), Access.PRIVATE);
                    nReg = Op.A_PRIVATE;
                    break;

                case "this:struct":
                    type = pool.ensureAccessTypeConstant(getThisType(), Access.STRUCT);
                    nReg = Op.A_STRUCT;
                    break;

                case "this:service":
                    type = pool.typeService();
                    nReg = Op.A_SERVICE;
                    break;

                case "super":
                {
                type = getMethod().getIdentityConstant().getSignature().asFunctionType();
                nReg = Op.A_SUPER;
                break;
                }

                case "this:module":
                    // the module can be resolved to the actual module component at compile time
                    return getModule().getIdentityConstant();

                default:
                    return null;
                }

            arg = new Register(type, nReg);
            mapByName.put(sName, arg);
            return arg;
            }

        @Override
        protected boolean hasInitialNames()
            {
            return true;
            }

        @Override
        protected void initNameMap(Map<String, Argument> mapByName)
            {
            MethodStructure         method      = m_method;
            Map<String, Assignment> mapAssigned = ensureDefiniteAssignments();
            for (int i = 0, c = method.getParamCount(); i < c; ++i)
                {
                org.xvm.asm.Parameter param = method.getParam(i);
                String    sName = param.getName();
                if (!sName.equals(Id.IGNORED.TEXT))
                    {
                    Register reg = new Register(param.getType(), i);
                    mapByName.put(sName, reg);

                    // the variable has been definitely assigned, but not multiple times (i.e. it's
                    // still effectively final)
                    mapAssigned.put(sName, Assignment.AssignedOnce);
                    }
                }
            }

        @Override
        public boolean isMethod()
            {
            return !isFunction() && !isConstructor();
            }

        @Override
        public boolean isFunction()
            {
            if (isConstructor())
                {
                return false;
                }

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

        @Override
        public boolean isConstructor()
            {
            return m_method.isConstructor();
            }

        ModuleStructure getModule()
            {
            Component parent = m_method;
            while (!(parent instanceof ModuleStructure))
                {
                parent = parent.getParent();
                }
            return (ModuleStructure) parent;
            }

        @Override
        public Context exit()
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
            Context ctx = m_ctxValidating;
            if (ctx == null)
                {
                m_ctxValidating = ctx = super.enter();
                }
            return ctx;
            }

        /**
         * @return a Context that can be used while emitting code
         */
        public Context emittingContext()
            {
            checkValidating();
            m_ctxValidating.exit();
            m_ctxValidating = null;

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
        private Context         m_ctxValidating;
        private boolean         m_fEmitting;
        }


    // ----- fields --------------------------------------------------------------------------------

    protected Source          source;
    protected List<Statement> stmts;
    protected long            lStartPos;
    protected long            lEndPos;
    protected boolean         boundary;
    protected boolean         containsEnclosed;

    protected Map<String, ImportStatement> imports;

    private transient boolean m_fSuppressScope;

    private static final Field[] CHILD_FIELDS = fieldsForNames(StatementBlock.class, "stmts");
    }
