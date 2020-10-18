package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.xvm.asm.Argument;
import org.xvm.asm.ClassStructure;
import org.xvm.asm.Component;
import org.xvm.asm.Component.ResolutionResult;
import org.xvm.asm.Component.SimpleCollector;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.Constants.Access;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.MethodStructure.Code;
import org.xvm.asm.ModuleStructure;
import org.xvm.asm.Op;
import org.xvm.asm.Op.ConstantRegistry;
import org.xvm.asm.PropertyStructure;
import org.xvm.asm.Register;
import org.xvm.asm.Assignment;

import org.xvm.asm.constants.ClassConstant;
import org.xvm.asm.constants.IdentityConstant;
import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.MethodInfo;
import org.xvm.asm.constants.MultiMethodConstant;
import org.xvm.asm.constants.PropertyConstant;
import org.xvm.asm.constants.PropertyInfo;
import org.xvm.asm.constants.TypeConstant;
import org.xvm.asm.constants.TypeInfo;

import org.xvm.asm.op.Enter;
import org.xvm.asm.op.Exit;
import org.xvm.asm.op.Nop;
import org.xvm.asm.op.Return_0;
import org.xvm.asm.op.Var_C;
import org.xvm.asm.op.Var_CN;
import org.xvm.asm.op.Var_IN;

import org.xvm.compiler.Compiler;
import org.xvm.compiler.Source;
import org.xvm.compiler.Token;
import org.xvm.compiler.Token.Id;

import org.xvm.compiler.ast.NameResolver.Result;
import org.xvm.compiler.ast.NewExpression.AnonInnerClassContext;

import org.xvm.util.ListMap;
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
        if (stmt.isWildcard())
            {
            if (importsWild == null)
                {
                importsWild = new ArrayList<>();
                }

            // make sure that no existing import uses the same alias
            String sName = stmt.getQualifiedNameString();
            if (importsWild.contains(sName))
                {
                log(errs, Severity.WARNING, Compiler.DUPLICATE_IMPORT, sName);
                }

            importsWild.add(stmt);
            }
        else
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
        if (imports != null)
            {
            ImportStatement stmt = imports.get(sName);
            if (stmt != null)
                {
                return stmt;
                }
            }

        if (importsWild != null)
            {
            for (int i = importsWild.size()-1; i >= 0; --i)
                {
                ImportStatement stmt     = importsWild.get(i);
                Constant        constant = stmt.getNameResolver().getConstant();
                if (constant instanceof IdentityConstant && constant.isClass())
                    {
                    ClassStructure clz = (ClassStructure) ((IdentityConstant) constant).getComponent();
                    if (clz != null)
                        {
                        if (clz.getChild(sName) instanceof ClassStructure)
                            {
                            return stmt;
                            }
                        }
                    }
                }
            }

        return null;
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
     *
     * @return true if nothing occurred during the compilation that should stop further progress
     */
    public boolean compileMethod(Code code, ErrorListener errs)
        {
        RootContext ctx = new RootContext(code.getMethodStructure());

        ErrorListener errsValidation = errs.branch();

        Statement that = this.validate(ctx.validatingContext(), errsValidation);

        errsValidation.merge();

        if (that == null || errsValidation.hasSeriousErrors() || errsValidation.isAbortDesired())
            {
            return false;
            }

        boolean fCompletes = that.completes(ctx.emittingContext(code), true, code, errs);

        if (fCompletes)
            {
            if (code.getMethodStructure().getReturns().isEmpty())
                {
                // a void method has an implicit "return;" at the end of it
                code.add(new Return_0());
                }
            else
                {
                errs.log(Severity.ERROR, Compiler.RETURN_REQUIRED, null, getSource(),
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

        return true;
        }

    @Override
    protected Statement validateImpl(Context ctx, ErrorListener errs)
        {
        List<Statement> stmts  = this.stmts;
        boolean         fValid = true;
        if (stmts != null && !stmts.isEmpty())
            {
            if (hasScope())
                {
                ctx = ctx.enter();
                }

            AstNode parent = getParent();
            if (parent instanceof LambdaExpression)
                {
                LambdaExpression          exprLambda  = (LambdaExpression) parent;
                Map<String, TypeConstant> mapNarrowed = exprLambda.isValidated()
                    ? exprLambda.getValidatedContext().getNarrowedParameters()
                    : Collections.EMPTY_MAP;

                // go through all of the parameters looking for any implicit de-reference params
                // (a new local variable will be created for each, effectively hiding the original
                // parameter)
                MethodStructure method = ctx.getMethod();
                if (method != null)
                    {
                    for (org.xvm.asm.Parameter param : method.getParamArray())
                        {
                        String   sName  = param.getName();
                        Register regVar = (Register) ctx.getVar(sName);

                        TypeConstant typeNarrowed = mapNarrowed.get(sName);
                        if (typeNarrowed != null)
                            {
                            regVar.specifyActualType(typeNarrowed);
                            }

                        if (param.isImplicitDeref())
                            {
                            Assignment asnVar = ctx.getVarAssignment(sName);
                            Register   regVal = param.deref(regVar);
                            ctx.ensureNameMap().put(sName, regVal); // shadow using the capture
                            ctx.setVarAssignment(sName, asnVar);    // ... and copy its assignment                    }
                            }
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
                    // this statement is the first statement that cannot be reached;
                    // the only thing that is allowed is an inner class definition
                    fReachable = false;

                    if (!(stmt instanceof TypeCompositionStatement ||
                          stmt instanceof MethodDeclarationStatement))
                        {
                        stmt.log(errs, Severity.ERROR, Compiler.NOT_REACHABLE);
                        break;
                        }
                    }

                fCompletable &= stmt.completes(ctx, fReachable, code, errs);

                if (fReachable && !fCompletable
                        && stmt instanceof ExpressionStatement
                        && ((ExpressionStatement) stmt).expr instanceof TodoExpression)
                    {
                    // T0D0 expression is allowed to have stuff that follows it that is unreachable
                    break;
                    }
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
            super(null, false);
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

        /**
         * @return true iff the code that is being compiled belongs to a class that is an anonymous
         *         inner class
         */
        public boolean isAnonInnerClass()
            {
            AstNode parent = getStatementBlock();
            while (!(parent instanceof TypeCompositionStatement))
                {
                parent = parent.getParent();
                }

            return parent.getParent() instanceof NewExpression;
            }

        public NewExpression getAnonymousInnerClassExpression()
            {
            AstNode parent = getStatementBlock();
            while (!(parent instanceof TypeCompositionStatement))
                {
                parent = parent.getParent();
                }

            return parent.getParent() instanceof NewExpression
                    ? (NewExpression) parent.getParent()
                    : null;
            }

        /**
         * @return the ClassStructure that contains the code being compiled
         */
        public ClassStructure getEnclosingClass()
            {
            AstNode parent = getStatementBlock();
            while (!(parent instanceof TypeCompositionStatement))
                {
                parent = parent.getParent();
                }

            return (ClassStructure) parent.getComponent();
            }

        @Override
        public Source getSource()
            {
            return getStatementBlock().getSource();
            }


        @Override
        public TypeConstant getThisType()
            {
            if (isAnonInnerClass())
                {
                NewExpression exprNew = getAnonymousInnerClassExpression();
                if (exprNew.isValidated())
                    {
                    return exprNew.getType();
                    }
                else
                    {
                    Context ctxC = exprNew.getCaptureContext();
                    if (ctxC != null)
                        {
                        return ctxC.getThisType();
                        }
                    }
                }
            return super.getThisType();
            }

        @Override
        public ClassStructure getThisClass()
            {
            if (isAnonInnerClass())
                {
                NewExpression exprNew = getAnonymousInnerClassExpression();
                return (ClassStructure) exprNew.anon.getComponent();
                }
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
        public void unregisterVar(Token tokName)
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
                return reg.isUnknown() || reg.getIndex() >= 0;
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
                case "this:class":
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
                    return infoMethod != null && infoMethod.hasSuper(info);
                    }

                default:
                    throw new IllegalArgumentException("no such reserved name: " + sName);
                }
            }

        @Override
        public void requireThis(long lPos, ErrorListener errs)
            {
            AstNode parent   = getParent();
            boolean fHasThis = parent instanceof LambdaExpression
                ? ((LambdaExpression) parent).isRequiredThis()
                : !isFunction();

            if (!fHasThis)
                {
                errs.log(Severity.ERROR, Compiler.NO_THIS, null, getSource(), lPos, lPos);
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
        public boolean isVarHideable(String sName)
            {
            // if the var name is not available, then there is already a variable of that same name
            // registered in this context, which means that it is either an explicit parameter
            // (unhideable) or a capture variable (hideable)
            return super.isVarHideable(sName)
                    || isAnonInnerClass() && getMethod().getParam(sName) == null;
            }

        @Override
        protected Argument resolveRegularName(Context ctxFrom, String sName, Token name, ErrorListener errs)
            {
            checkValidating();

            // check if the name is a parameter name, or a global name that has already been looked
            // up and cached
            Map<String, Argument> mapByName = ensureNameMap();
            Argument              arg       = mapByName.get(sName);
            if (arg != null)
                {
                return arg;
                }

            // check if the name specifies a capture variable from an anonymous inner class to its
            // enclosing method (the method containing the "new" expression that defines the
            // anonymous inner class); this needs to be checked up front, because otherwise we will
            // find the synthetic property of the same name as we walk up the component tree
            if (isAnonInnerClass())
                {
                NewExpression exprNew = getAnonymousInnerClassExpression();
                if (exprNew.isCapture(sName))
                    {
                    // the name refers to a capture variable, which was provided to the
                    // anonymous inner class via a synthetic property
                    PropertyStructure prop = (PropertyStructure) getEnclosingClass().getChild(sName);
                    assert prop.isSynthetic();

                    TypeConstant type = exprNew.getCaptureType(sName);
                    Register     reg  = new Register(type);
                    mapByName.put(sName, reg);
                    ensureCaptureVars().put(sName, reg);

                    // TODO we need to know the definite assignment of the variable at the point
                    //      that it was captured, which is either what it was at that point (if it
                    //      was effectively final) or the implied result of what it was plus the
                    //      impact of NOT being effectively final
                    ensureDefiniteAssignments().put(sName, Assignment.Assigned); // TODO wrong!!!

                    return reg;
                    }
                }

            // start with the current node, and one by one walk up to the root, asking at
            // each level for the node to resolve the name
            ConstantPool     pool      = pool();
            AstNode          node      = StatementBlock.this;
            boolean          fSameFile = true;
            boolean          fHasThis  = isMethod() || isConstructor();
            TypeConstant     typeThis  = fHasThis ? ctxFrom.getVar("this").getType() : null;
            int              cSteps    = 0;
            Access           access    = Access.PRIVATE;
            IdentityConstant idPrev    = null;
            TypeInfo         infoPrev  = null;
            ErrorListener    errsTemp  = errs.branch();
            IdentityConstant idOuter   = getEnclosingClass().getIdentityConstant();
            if (idOuter instanceof ClassConstant)
                {
                idOuter = ((ClassConstant) idOuter).getOutermost();
                }

            WalkUpToTheRoot: while (node != null)
                {
                // otherwise, if the node has a component associated with it that is
                // prepared to resolve names, then ask it to resolve the name
                if (node.isComponentNode())
                    {
                    assert node.canResolveNames();

                    Component component = node.getComponent();
                    assert component != null;

                    // the identity of the component corresponding to the current node as
                    // we "WalkUpToTheRoot"
                    IdentityConstant id    = component.getIdentityConstant();
                    IdentityConstant idClz = id.getClassIdentity();

                    // first attempt: ask the component to resolve the name
                    // REVIEW - shouldn't all of this resolution info be present on the TypeInfo? i.e. shouldn't we rely on the TypeInfo instead of the Component?
                    SimpleCollector collector = new SimpleCollector(errs);
                    if (component.resolveName(sName, access, collector) == ResolutionResult.RESOLVED)
                        {
                        Constant constant = collector.getResolvedConstant();
                        switch (constant.getFormat())
                            {
                            // properties and methods will use the TypeInfo for resolution
                            case Property:
                            case Method:
                            case MultiMethod:
                                break;

                            default:
                                return constant;
                            }
                        }

                    // second attempt: ask the TypeInfo if it knows what the name refers to
                    // load the TypeInfo for the class that we are looking for names in
                    TypeInfo info;
                    if (idPrev != null && idClz == idPrev)
                        {
                        info = infoPrev;
                        }
                    else
                        {
                        TypeConstant typeClz;
                        if (typeThis == null)
                            {
                            typeClz = ((ClassStructure) idClz.getComponent()).getFormalType();
                            typeClz = pool.ensureAccessTypeConstant(typeClz, access);
                            }
                        else if (typeThis.isRelationalType())
                            {
                            typeClz = typeThis;
                            }
                        else
                            {
                            typeClz = pool.ensureAccessTypeConstant(typeThis,
                                isConstructor() ? Access.STRUCT : Access.PRIVATE);
                            }

                        infoPrev = info = typeClz.ensureTypeInfo(errsTemp);
                        idPrev   = idClz;
                        }

                    if (id == idClz)
                        {
                        // we're at a class level
                        IdentityConstant idResult = null;
                        PropertyInfo     prop     = info.findProperty(sName);
                        if (prop == null)
                            {
                            if (info.containsMultiMethod(sName))
                                {
                                // we need to find a real multimethod structure now
                                Set<MethodConstant> setMethods = info.findMethods(sName, -1, TypeInfo.MethodKind.Any);
                                assert !setMethods.isEmpty();

                                MethodConstant idMethod   = setMethods.iterator().next();
                                MethodInfo     infoMethod = info.getMethodById(idMethod);
                                idResult = infoMethod.getIdentity().getParentConstant();
                                }
                            }
                        else
                            {
                            idResult = prop.getIdentity();
                            }

                        if (idResult != null)
                            {
                            return new TargetInfo(sName, idResult, fHasThis, info.getType(), cSteps);
                            }

                        fHasThis &= !info.isStatic();
                        typeThis  = null;
                        ++cSteps;
                        }
                    else if (id instanceof PropertyConstant)
                        {
                        // first, look for a property of the given name inside the current
                        // property
                        IdentityConstant idResult = null;
                        PropertyConstant idProp   = (PropertyConstant) id;
                        PropertyInfo     prop     = info.ensureNestedPropertiesByName(idProp).get(sName);
                        if (prop == null)
                            {
                            // second, look for any methods of the given name inside the
                            // current property
                            if (info.containsNestedMultiMethod(idProp, sName))
                                {
                                // the multi-method structure does not actually exist on the
                                // class, but its methods exist in the TypeInfo
                                idResult = pool.ensureMultiMethodConstant(id, sName);
                                }
                            }
                        else
                            {
                            idResult = prop.getIdentity().ensureNestedIdentity(pool, idProp);
                            }

                        if (idResult == null)
                            {
                            if (((PropertyStructure) idProp.getComponent()).isRefAnnotated())
                                {
                                ++cSteps;
                                }
                            }
                        else
                            {
                            return new TargetInfo(sName, idResult, fHasThis, info.getType(), cSteps);
                            }
                        }
                    else if (id instanceof MethodConstant)
                        {
                        // first, look for a property of the given name inside this method
                        IdentityConstant idResult = null;
                        MethodConstant   idMethod = (MethodConstant) id;
                        PropertyInfo     prop     = info.ensureNestedPropertiesByName(idMethod).get(sName);
                        if (prop == null)
                            {
                            // second, look for any methods of the given name inside this method
                            if (info.containsNestedMultiMethod(idMethod, sName))
                                {
                                // the multi-method structure does not actually exist on the
                                // class, but its methods exist in the TypeInfo
                                idResult = pool.ensureMultiMethodConstant(id, sName);
                                }
                            }
                        else
                            {
                            idResult = prop.getIdentity();
                            }

                        if (idResult != null)
                            {
                            return new TargetInfo(sName, idResult, fHasThis, info.getType(), cSteps);
                            }
                        }
                    else
                        {
                        assert id instanceof MultiMethodConstant;
                        }

                    // check if we are nested inside an anonymous inner class and attempting to
                    // capture a variable from the context of the NewExpression
                    if (node instanceof NewExpression)
                        {
                        AnonInnerClassContext ctx = ((NewExpression) node).getCaptureContext();
                        if (ctx != null)
                            {
                            Argument argCapture = ctx.getVar(sName);
                            if (argCapture != null)
                                {
                                // we are responsible for capturing a variable for code inside of an
                                // anonymous inner class
                                Register reg = new Register(argCapture.getType());
                                super.registerVar(name, reg, errs);
                                ensureDefiniteAssignments().put(sName, ctx.getVarAssignment(sName));
                                ensureCaptureContexts().put(sName, ctx);
                                return reg;
                                }
                            }
                        }

                    // see if this was the last step on the "WalkUpToTheRoot" that had
                    // private access to all members
                    if (id == idOuter)
                        {
                        // in the top-most-class down, there is private access
                        // above the top-most-class, there is public access
                        access = Access.PUBLIC;
                        fHasThis = false;
                        }
                    else
                        {
                        switch (component.getFormat())
                            {
                            case ENUM:
                            case ENUMVALUE:
                            case PACKAGE:
                            case MODULE:
                            case TYPEDEF:
                                fHasThis = false;
                                break;

                            case INTERFACE:
                            case CLASS:
                            case CONST:
                            case MIXIN:
                            case SERVICE:
                            case PROPERTY:
                                fHasThis &= !component.isStatic();
                                break;

                            case METHOD:
                                MethodStructure method = (MethodStructure) component;
                                fHasThis &= !method.isFunction();
                                break;
                            }
                        }
                    }

                if (fSameFile && node instanceof StatementBlock)
                    {
                    // the name may specify an import
                    StatementBlock  block      = (StatementBlock) node;
                    ImportStatement stmtImport = block.getImport(sName);
                    if (stmtImport != null)
                        {
                        NameResolver resolver = stmtImport.getNameResolver();
                        if (resolver.getResult() != Result.RESOLVED)
                            {
                            // report an unresolvable import name below
                            break;
                            }
                        Constant constant = resolver.getConstant();
                        return stmtImport.isWildcard()
                                ? ((IdentityConstant) constant).getComponent().
                                        getChild(sName).getIdentityConstant()
                                : constant;
                        }

                    // see if we're crossing a source file boundary (because imports are only used
                    // when they are local to the file in which they occur)
                    if (block.isFileBoundary())
                        {
                        fSameFile = false;
                        }
                    }

                // walk up towards the root
                node = node.getParent();
                }

            // last chance: check the implicitly imported names
            Component component = pool.getImplicitlyImportedComponent(sName);

            if (component != null)
                {
                return component.getIdentityConstant();
                }

            errsTemp.merge(); // report any TypeInfo related errors we might have collected
            if (name == null)
                {
                log(errs, Severity.ERROR, Compiler.NAME_UNRESOLVABLE, sName);
                }
            else
                {
                name.log(errs, getSource(), Severity.ERROR, Compiler.NAME_UNRESOLVABLE, sName);
                }
            return null;
            }

        @Override
        protected Argument getVar(String sName, Token name, Branch branch, ErrorListener errs)
            {
            Argument arg = getLocalVar(sName, branch);
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
            int          cSteps = 0;
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
                    type   = getThisType();
                    nReg   = Op.A_TARGET;
                    cSteps = getMethod().getThisSteps();
                    break;

                case "this:public":
                    type   = pool.ensureAccessTypeConstant(getThisType(), Access.PUBLIC);
                    nReg   = Op.A_PUBLIC;
                    cSteps = getMethod().getThisSteps();
                    break;

                case "this:protected":
                    type   = pool.ensureAccessTypeConstant(getThisType(), Access.PROTECTED);
                    nReg   = Op.A_PROTECTED;
                    cSteps = getMethod().getThisSteps();
                    break;

                case "this:private":
                    type   = pool.ensureAccessTypeConstant(getThisType(), Access.PRIVATE);
                    nReg   = Op.A_PRIVATE;
                    cSteps = getMethod().getThisSteps();
                    break;

                case "this:struct":
                    type   = pool.ensureUnionTypeConstant(pool.typeStruct(),
                            pool.ensureAccessTypeConstant(getThisType(), Access.STRUCT));
                    nReg   = Op.A_STRUCT;
                    cSteps = getMethod().getThisSteps();
                    break;

                case "this:class":
                    type   = pool.ensureParameterizedTypeConstant(pool.typeClass(),
                            pool.ensureAccessTypeConstant(getThisType(), Access.PUBLIC),
                            pool.ensureAccessTypeConstant(getThisType(), Access.PROTECTED),
                            pool.ensureAccessTypeConstant(getThisType(), Access.PRIVATE),
                            pool.ensureUnionTypeConstant(pool.typeStruct(),
                                pool.ensureAccessTypeConstant(getThisType(), Access.STRUCT))); // TODO helpers for these all or getThisType passing access
                    nReg   = Op.A_CLASS;
                    cSteps = getMethod().getThisSteps();
                    break;

                case "this:service":
                    type = pool.typeService();
                    nReg = Op.A_SERVICE;
                    break;

                case "super":
                    type = getMethod().getIdentityConstant().getSignature().asFunctionType();
                    nReg = Op.A_SUPER;
                    break;

                case "this:module":
                    // the module can be resolved to the actual module component at compile time
                    return getModule().getIdentityConstant();

                default:
                    return null;
                }

            if (cSteps == 0)
                {
                arg = new Register(type, nReg);
                }
            else
                {
                arg = new TargetInfo(sName, type, cSteps);
                }
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
            MethodConstant          idMethod    = method.getIdentityConstant();
            Map<String, Assignment> mapAssigned = ensureDefiniteAssignments();
            for (int i = 0, c = method.getParamCount(); i < c; ++i)
                {
                org.xvm.asm.Parameter param = method.getParam(i);
                String    sName = param.getName();
                if (!sName.equals(Id.ANY.TEXT))
                    {
                    Register reg;
                    if (param.isTypeParameter())
                        {
                        reg = new Register(param.asTypeParameterType(idMethod).getType(), i);
                        reg.markEffectivelyFinal();
                        }
                    else
                        {
                        reg = new Register(param.getType(), i);
                        }
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
            MethodStructure method = m_method;
            if (method.isValidator() ||
                    method.isConstructor() && !method.isPropertyInitializer())
                {
                return false;
                }

            Component parent = method;
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
            return m_method.isConstructor() || m_method.isValidator();
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

        @Override
        public Map<String, Assignment> prepareJump(Context ctxDest)
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
         * Generate an emitting context, and emit the preamble, if any.
         *
         * @param code  the code that is being emitted to
         *
         * @return a Context that can be used while emitting code
         */
        public Context emittingContext(Code code)
            {
            if (m_fEmitting)
                {
                return this;
                }

            checkValidating();
            Context ctx = m_ctxValidating;
            if (ctx != null)
                {
                ctx.exit();
                m_ctxValidating = null;

                // check any variables whose scope is in this root context for "effectively final"
                for (Map.Entry<String, Assignment> entry : getDefiniteAssignments().entrySet())
                    {
                    if (entry.getValue().isEffectivelyFinal())
                        {
                        Argument arg = ensureNameMap().get(entry.getKey());
                        if (arg instanceof Register)
                            {
                            ((Register) arg).markEffectivelyFinal();
                            }
                        }
                    }

                if (m_mapCaptureContexts != null)
                    {
                    // this is something like "exit()" processing, except that it's when the root
                    // context switches from validating (which may have captured some variables into
                    // the anonymous inner class) which now need to be reported out to the capture
                    // context for the anonymous inner class, so that it knows what has been read
                    // (assume everything captured is read) and written (assume anything whose
                    // assignment changed was written)
                    for (Map.Entry<String, AnonInnerClassContext> entry : m_mapCaptureContexts.entrySet())
                        {
                        String                sName      = entry.getKey();
                        AnonInnerClassContext ctxCapture = entry.getValue();
                        Assignment            asnOrig    = ctxCapture.getVarAssignment(sName);
                        boolean               fModified  = getVarAssignment(sName) != asnOrig;
                        ctxCapture.markVarRead(true, sName, null, null);
                        if (fModified)
                            {
                            ctxCapture.setVarAssignment(sName, asnOrig.applyAssignmentFromCapture());
                            }
                        }
                    m_mapCaptureContexts = null;
                    }

                if (m_mapCaptureVars != null)
                    {
                    // REVIEW arguably, it would have been cleaner to put this with the code in StatementBlock.emit()
                    // emit the pre-amble that provides captured variables as local variables
                    assert isAnonInnerClass();
                    NewExpression  exprNew = getAnonymousInnerClassExpression();
                    ClassStructure clzAnon = getEnclosingClass();
                    for (Map.Entry<String, Register> entry : m_mapCaptureVars.entrySet())
                        {
                        String            sName = entry.getKey();
                        Register          reg   = entry.getValue();
                        PropertyStructure prop  = (PropertyStructure) clzAnon.getChild(sName);
                        PropertyConstant  id    = prop.getIdentityConstant();
                        code.add(exprNew.isImplicitDeref(sName)
                                ? new Var_CN(reg, id.getNameConstant(), id)
                                : new Var_IN(reg, id.getNameConstant(), id));
                        }
                    }
                }

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

        /**
         * @return a map of capture contexts being collected during validation that need to be
         *         reported out to the enclosing NewExpression
         */
        private Map<String, AnonInnerClassContext> ensureCaptureContexts()
            {
            Map<String, AnonInnerClassContext> map = m_mapCaptureContexts;
            if (map == null)
                {
                m_mapCaptureContexts = map = new ListMap<>();
                }

            return map;
            }

        /**
         * @return a map of variables identified during validation that need to be created in the
         *         preamble in order to provide local variables for captured variables
         */
        private Map<String, Register> ensureCaptureVars()
            {
            Map<String, Register> map = m_mapCaptureVars;
            if (map == null)
                {
                m_mapCaptureVars = map = new ListMap<>();
                }

            return map;
            }

        private MethodStructure m_method;
        private Context         m_ctxValidating;
        private boolean         m_fEmitting;

        /**
         * A lazily created mapping of captured variables that is collected during the validation
         * on a temporary (throw-away) copy of an inner class.
         */
        private Map<String, AnonInnerClassContext> m_mapCaptureContexts;

        /**
         * A lazily created mapping of variables that need to be created to implicitly dereference
         * capture-properties in an anonymous inner class.
         */
        private Map<String, Register> m_mapCaptureVars;
        }


    // ----- inner class: TargetInfo ---------------------------------------------------------------

    /**
     * Represents the information learned when resolving a name to a multi-method, property or
     * outer "this".
     */
    public static class TargetInfo
            implements Argument
        {
        /**
         * Create a new TargetInfo for a multi-method or property.
         *
         * @param name        the name being resolved
         * @param id          the id of multi-method or property
         * @param hasThis     if true, the target is instance specific; otherwise static
         * @param typeTarget  the target container type
         * @param stepsOut    the number of "outer" steps needed to get from the current context
         *                    to the target container
         */
        public TargetInfo(
                String           name,
                IdentityConstant id,
                boolean          hasThis,
                TypeConstant     typeTarget,
                int              stepsOut)
            {
            assert id instanceof PropertyConstant || id instanceof MultiMethodConstant;

            this.name       = name;
            this.id         = id;
            this.hasThis    = hasThis;
            this.typeTarget = typeTarget;
            this.stepsOut   = stepsOut;

            if (id instanceof PropertyConstant)
                {
                PropertyConstant idProp   = (PropertyConstant) id;
                PropertyInfo     infoProp = typeTarget.ensureTypeInfo().findProperty(idProp);

                this.type = infoProp == null
                        ? idProp.isFormalType()
                                ? idProp.getFormalType()
                                : idProp.getType()
                        : infoProp.getType();
                }
            else
                {
                this.type = null;
                }
            }

        /**
         * Create a new TargetInfo for a method.
         *
         * @param name        the name being resolved
         * @param method      the method
         * @param typeTarget  the target container type
         * @param stepsOut    the number of "outer" steps needed to get from the current context
         *                    to the target container
         */
        public TargetInfo(
                String           name,
                MethodStructure  method,
                TypeConstant     typeTarget,
                int              stepsOut)
            {
            this.name       = name;
            this.id         = method.getIdentityConstant();
            this.hasThis    = !method.isFunction();
            this.typeTarget = typeTarget;
            this.stepsOut   = stepsOut;
            this.type       = null;
            }

        /**
         * Create a new TargetInfo for an "outer this".
         *
         * @param name        the name being resolved ("this" or "this:[access-qualifier]")
         * @param typeTarget  the outer this type
         * @param stepsOut    the number of "outer" steps needed to get from the current context
         *                    to the target container
         */
        public TargetInfo(
                String        name,
                TypeConstant  typeTarget,
                int           stepsOut)
            {
            assert typeTarget.isExplicitClassIdentity(true);

            this.name       = name;
            this.id         = typeTarget.getSingleUnderlyingClass(true);
            this.hasThis    = true;
            this.typeTarget = typeTarget;
            this.stepsOut   = stepsOut;
            this.type       = typeTarget;
            }

        /**
         * Create a new TargetInfo by narrowing the type of the specified TargetInfo.
         *
         * @param that        the original info
         * @param typeNarrow  the narrowing type
         */
        public TargetInfo(TargetInfo that, TypeConstant typeNarrow)
            {
            this.name       = that.name;
            this.id         = that.id;
            this.hasThis    = that.hasThis;
            this.typeTarget = that.typeTarget;
            this.stepsOut   = that.stepsOut;
            this.type       = typeNarrow;
            }

        public IdentityConstant getId()
            {
            return id;
            }

        public TypeConstant getTargetType()
            {
            return typeTarget;
            }

        public boolean hasThis()
            {
            return hasThis;
            }

        public int getStepsOut()
            {
            return stepsOut;
            }

        @Override
        public TypeConstant getType()
            {
            return type;
            }

        @Override
        public boolean isStack()
            {
            return false;
            }

        @Override
        public Argument registerConstants(ConstantRegistry registry)
            {
            throw new IllegalStateException();
            }

        @Override
        public String toString()
            {
            return name + "->" + id.getValueString();
            }

        private final String           name;
        private final IdentityConstant id;
        private final boolean          hasThis;
        private final TypeConstant     typeTarget;
        private final int              stepsOut;
        private final TypeConstant     type;
        }


    // ----- fields --------------------------------------------------------------------------------

    protected Source          source;
    protected List<Statement> stmts;
    protected long            lStartPos;
    protected long            lEndPos;
    protected boolean         boundary;
    protected boolean         containsEnclosed;

    protected Map<String, ImportStatement> imports;
    protected List<ImportStatement>        importsWild;

    private transient boolean m_fSuppressScope;

    private static final Field[] CHILD_FIELDS = fieldsForNames(StatementBlock.class, "stmts");
    }
