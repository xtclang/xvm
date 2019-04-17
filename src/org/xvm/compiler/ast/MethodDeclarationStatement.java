package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Component;
import org.xvm.asm.Constant;
import org.xvm.asm.Constant.Format;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.Constants.Access;
import org.xvm.asm.ErrorList;
import org.xvm.asm.ErrorList.ErrorInfo;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.MethodStructure.Code;
import org.xvm.asm.ModuleStructure;
import org.xvm.asm.MultiMethodStructure;
import org.xvm.asm.PropertyStructure;

import org.xvm.asm.constants.ClassConstant;
import org.xvm.asm.constants.IdentityConstant;
import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.PropertyConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.compiler.Compiler;
import org.xvm.compiler.Token;
import org.xvm.compiler.Token.Id;

import org.xvm.util.Severity;

import static org.xvm.util.Handy.appendString;
import static org.xvm.util.Handy.indentLines;


/**
 * A method declaration.
 */
public class MethodDeclarationStatement
        extends ComponentStatement
    {
    // ----- constructors --------------------------------------------------------------------------

    public MethodDeclarationStatement(long                 lStartPos,
                                      long                 lEndPos,
                                      Expression           condition,
                                      List<Token>          modifiers,
                                      List<Annotation>     annotations,
                                      List<Parameter>      typeParams,
                                      Token                conditional,
                                      List<Parameter>      returns,
                                      Token                name,
                                      List<TypeExpression> redundant,
                                      List<Parameter>      params,
                                      StatementBlock       body,
                                      Token                tokFinally,
                                      StatementBlock       bodyFinally,
                                      Token                doc)
        {
        super(lStartPos, lEndPos);

        assert name != null;

        this.condition     = condition;
        this.modifiers     = modifiers;
        this.annotations   = annotations;
        this.conditional   = conditional;
        this.typeParams    = typeParams;
        this.returns       = returns;
        this.name          = name;
        this.redundant     = redundant;
        this.params        = params;
        this.body          = body;
        this.m_tokFinally  = tokFinally;
        this.m_bodyFinally = bodyFinally;
        this.doc           = doc;
        }

    /**
     * Create a MethodDeclarationStatement that turns an expression into a MethodStructure. This is
     * used, for example, by initializers.
     * <p/>
     * Note: the underlying expression can be retrieved using the {@link #getInitializerExpression()}
     * method.
     *
     * @param struct  the MethodStructure that this MethodDeclarationStatement is intended to
     *                compile into
     * @param expr    the Expression that the resulting method must evaluate as its one return value
     */
    public MethodDeclarationStatement(MethodStructure struct, Expression expr)
        {
        super(expr.getStartPosition(), expr.getEndPosition());

        // store off the method structure that we will generate code into
        setComponent(struct);

        // grab a body from the expression, if it has one, otherwise make one
        if (expr instanceof LambdaExpression && ((LambdaExpression) expr).params.isEmpty())
            {
            body = ((LambdaExpression) expr).body;
            }
        else
            {
            // turn "<expr>" into the statement block "{ return <expr>; }"
            Token fakeReturn = new Token(expr.getStartPosition(), expr.getStartPosition(), Id.RETURN);
            ReturnStatement stmt = new ReturnStatement(fakeReturn, expr);
            stmt.adopt(expr);
            body = new StatementBlock(Collections.singletonList(stmt), expr.getStartPosition(), expr.getEndPosition());
            body.adopt(stmt);
            }

        adopt(body);
        }


    // ----- accessors -----------------------------------------------------------------------------

    /**
     * @return true iff this statement represents a constructor
     */
    public boolean isConstructor()
        {
        return name != null && name.getId() == Id.CONSTRUCT;
        }

    /**
     * @return true iff this statement represents a constructor's finally block
     */
    public boolean isConstructorFinally()
        {
        return name != null && name.getId() == Id.FINALLY;
        }

    /**
     * If this statement represents a constructor, return a complementary statement
     * representing the "finally" block if one exists.
     *
     * @return the statement representing the "finally" block or null
     */
    public MethodDeclarationStatement getConstructorFinally()
        {
        if (!isConstructor())
            {
            return null;
            }

        MethodDeclarationStatement stmtFinally = m_stmtComplement;
        if (stmtFinally != null)
            {
            return stmtFinally;
            }

        StatementBlock bodyFinally = m_bodyFinally;
        if (bodyFinally == null)
            {
            return null;
            }

        stmtFinally = new MethodDeclarationStatement(
                bodyFinally.getStartPosition(),
                bodyFinally.getEndPosition(),
                condition,
                modifiers,
                annotations,
                typeParams,
                conditional,
                returns,
                m_tokFinally,
                redundant,
                params,
                bodyFinally,
                null,
                null,
                doc);

        stmtFinally.bindConstructor(this);
        return m_stmtComplement = stmtFinally;
        }

    /**
     * When this statement represents a "finally" block, bind it to the corresponding
     * constructor.
     *
     * @param stmtConstructor  the "construct" statement to bind this "finally" to
     */
    private void bindConstructor(MethodDeclarationStatement stmtConstructor)
        {
        assert isConstructorFinally();
        m_stmtComplement = stmtConstructor;
        }

    /**
     * @return the simple name for this statement
     */
    private String getName()
        {
        if (name != null)
            {
            return name.getValueText();
            }

        MethodStructure struct = (MethodStructure) getComponent();
        return struct == null
                ? "???"
                : struct.getName();
        }

    @Override
    public Access getDefaultAccess()
        {
        // methods are *not* taking the parent's access by default
        Access  access = getAccess(modifiers);
        return access == null ? Access.PUBLIC : access;
        }

    @Override
    public boolean isAutoNarrowingAllowed(TypeExpression type)
        {
        return getComponent().isAutoNarrowingAllowed();
        }

    @Override
    protected Field[] getChildFields()
        {
        return CHILD_FIELDS;
        }

    @Override
    protected boolean usesSuper()
        {
        return body != null && body.usesSuper();
        }

    /**
     * This helper method is only used for MethodDeclarationStatements that are created with the
     * {@link #MethodDeclarationStatement(MethodStructure, Expression) method-based constructor}
     *
     * @return the one expression that defines the return value of this statement, if such a thing
     *         is even possible; otherwise null
     */
    public Expression getInitializerExpression()
        {
        List<Statement> list = body.stmts;
        if (list.isEmpty())
            {
            return null;
            }

        Statement stmtFirst = list.get(0);
        if (stmtFirst instanceof ReturnStatement && ((ReturnStatement) stmtFirst).exprs.size() == 1)
            {
            return ((ReturnStatement) stmtFirst).exprs.get(0);
            }

        return null;
        }

    // ----- code container methods ----------------------------------------------------------------

    @Override
    public TypeConstant[] getReturnTypes()
        {
        return ((MethodStructure) getComponent()).getReturnTypes();
        }

    @Override
    public boolean isReturnConditional()
        {
        return conditional != null;
        }

    @Override
    public void collectReturnTypes(TypeConstant[] atypeRet)
        {
        // it's a no-op for a method declaration statement
        }

    // ----- compile phases ------------------------------------------------------------------------

    @Override
    protected void registerStructures(StageMgr mgr, ErrorListener errs)
        {
        // methods are opaque, so everything inside can be deferred until we get to the
        // validateContent() stage
        mgr.deferChildren();

        // create the structure for this method
        CreateStructure: if (getComponent() == null)
            {
            // TODO validate that the "redundant" types match the return types
            // TODO validate that the names (params, type params) are unique

            // create a structure for this type
            Component container = getParent().getComponent();
            String    sName     = getName();
            if (container.isMethodContainer())
                {
                boolean      fConstructor = isConstructor();
                boolean      fFinally     = isConstructorFinally();
                boolean      fFunction    = isStatic(modifiers) || fConstructor;
                Access       access       = getDefaultAccess();
                ConstantPool pool         = container.getConstantPool();

                // build array of annotations
                org.xvm.asm.Annotation[] aAnnotations = buildAnnotations(pool);

                // build array of return types
                org.xvm.asm.Parameter[] aReturns;
                if (returns == null)
                    {
                    if (container instanceof PropertyStructure)
                        {
                        if (fFunction)
                            {
                            // TODO: error - function is not allowed
                            throw new UnsupportedOperationException("function is not allowed");
                            }
                        // it's a "short hand" property method; stop right here
                        // will continue resolution in resolveNames() below
                        break CreateStructure;
                        }

                    if (fConstructor || fFinally)
                        {
                        aReturns = org.xvm.asm.Parameter.NO_PARAMS;
                        }
                    else
                        {
                        // parser should have caught this
                        throw new IllegalStateException("missing returns");
                        }
                    }
                else
                    {
                    int ofReturn = 0;
                    int cReturns = returns.size();
                    if (conditional != null)
                        {
                        ++ofReturn;
                        ++cReturns;
                        }
                    aReturns = new org.xvm.asm.Parameter[cReturns];
                    if (conditional != null)
                        {
                        aReturns[0] = new org.xvm.asm.Parameter(pool, pool.typeBoolean(), null, null, true, 0, true);
                        }
                    for (int i = ofReturn; i < cReturns; ++i)
                        {
                        Parameter param = returns.get(i-ofReturn);
                        aReturns[i] = new org.xvm.asm.Parameter(pool,
                                param.getType().ensureTypeConstant(), param.getName(), null, true, i, false);
                        }
                    }

                org.xvm.asm.Parameter[] aParams = buildParameters(pool);

                boolean fUsesSuper = !fFunction && !fFinally && access != Access.PRIVATE && usesSuper();
                MethodStructure method;
                if (fFinally)
                    {
                    method = container.createMethod(false, Access.PRIVATE, null,
                            aReturns, "finally", aParams, true, false);

                    MethodStructure methodConstruct = (MethodStructure) m_stmtComplement.getComponent();
                    if (methodConstruct != null)
                        {
                        methodConstruct.setConstructFinally(method);
                        }
                    }
                else
                    {
                    method = container.createMethod(fFunction, access, aAnnotations,
                            aReturns, sName, aParams, body != null, fUsesSuper);

                    if (fConstructor)
                        {
                        MethodDeclarationStatement stmtFinally = m_stmtComplement;
                        if (stmtFinally != null)
                            {
                            MethodStructure methodFinally = (MethodStructure) stmtFinally.getComponent();
                            if (methodFinally != null)
                                {
                                method.setConstructFinally(methodFinally);
                                }
                            }
                        }
                    }
                setComponent(method);
                }
            else
                {
                log(errs, Severity.ERROR, Compiler.METHOD_UNEXPECTED, sName, container);
                throw new UnsupportedOperationException("not a method container: " + container);
                }
            }

        // methods are opaque, so everything inside can be deferred until we get to the
        // validateContent() stage
        mgr.processChildrenExcept((child) -> child == body);
        }

    @Override
    public void resolveNames(StageMgr mgr, ErrorListener errs)
        {
        // methods are opaque, so everything inside can be deferred until we get to the
        // validateContent() stage
        mgr.deferChildren();

        if (getComponent() == null)
            {
            Component container = getParent().getComponent();
            String    sName     = getName();
            if (container.isMethodContainer())
                {
                if (returns == null && container instanceof PropertyStructure)
                    {
                    // this is a short-hand property method
                    PropertyStructure property = (PropertyStructure) container;
                    List<Annotation> annotations =
                        ((PropertyDeclarationStatement) getParent().getParent()).annotations; // TODO: replace

                    MethodStructure methodSuper = findRefMethod(property, annotations, sName, params, errs);
                    if (methodSuper == null)
                        {
                        if (annotations != null)
                            {
                            for (Annotation anno : annotations)
                                {
                                TypeConstant type = anno.getType().getTypeConstant();
                                if (type != null && type.containsUnresolved())
                                    {
                                    mgr.requestRevisit();
                                    return;
                                    }
                                }
                            }
                        
                        for (Parameter param : params)
                            {
                            TypeConstant type = param.getType().getTypeConstant();
                            if (type != null && type.containsUnresolved())
                                {
                                mgr.requestRevisit();
                                return;
                                }
                            }

                        // all annotations and parameters  are resolved, report an error
                        log(errs, Severity.ERROR, Compiler.NAME_UNRESOLVABLE, sName);
                        return;
                        }

                    ConstantPool            pool     = container.getConstantPool();
                    int                     cReturns = methodSuper.getReturnCount();
                    org.xvm.asm.Parameter[] aReturns = new org.xvm.asm.Parameter[cReturns];
                    for (int i = 0; i < cReturns; i++)
                        {
                        org.xvm.asm.Parameter param = methodSuper.getReturn(i);
                        TypeConstant type = param.getType();

                        if (type.containsUnresolved())
                            {
                            // not yet resolved; come back later
                            mgr.requestRevisit();
                            return;
                            }

                        if (type.getFormat() == Format.TerminalType)
                            {
                            Constant constReturn = type.getDefiningConstant();
                            if (constReturn.getFormat() == Format.Property
                                    && ((PropertyConstant) constReturn).getName().equals("RefType"))
                                {
                                // replace the RefType with the actual property type
                                param = new org.xvm.asm.Parameter(pool,
                                        property.getType(), param.getName(), null, true, i, false);
                                }
                            }
                        aReturns[i] = param;
                        }

                    org.xvm.asm.Parameter[] aParams = buildParameters(pool);

                    // the parameters were already matched; no need to re-check
                    org.xvm.asm.Annotation[] annos = new org.xvm.asm.Annotation[]
                            {new org.xvm.asm.Annotation(pool, pool.clzOverride(), Constant.NO_CONSTS)};
                    MethodStructure method = container.createMethod(
                            false, methodSuper.getAccess(), annos, aReturns, sName, aParams,
                            body != null, usesSuper());
                    setComponent(method);
                    }
                }
            }

        // methods are opaque, so everything inside the curlies can be deferred until we get to the
        // validateContent() stage
        mgr.processChildrenExcept((child) -> child == body);

        MethodStructure method = (MethodStructure) getComponent();
        if (method != null)
            {
            // sort out which annotations go on the method, and which belong to the return type
            if (!method.resolveAnnotations() || !method.resolveTypedefs())
                {
                mgr.requestRevisit();
                }
            }
        }

    @Override
    public void validateContent(StageMgr mgr, ErrorListener errs)
        {
        // method children are all deferred up until this stage, so we have to "catch them up" at
        // this point, recreating the various compiler stages here
        MethodStructure method = (MethodStructure) getComponent();
        if (method == null || !catchUpChildren(errs))
            {
            // we are in an error state; we choose not to proceed with compilation
            mgr.deferChildren();
            }
        else if (method.getChildrenCount() > 0)
            {
            // the discovery of new structures means that any TypeInfo that was already created will
            // be wrong
            IdentityConstant idClz    = method.getContainingClass().getIdentityConstant();
            ConstantPool     poolStmt = pool();
            ConstantPool     poolId   = idClz.getConstantPool();
            poolStmt.invalidateTypeInfos(idClz);
            if (poolId != poolStmt)
                {
                poolId.invalidateTypeInfos(idClz);
                }
            }
        }

    @Override
    public void generateCode(StageMgr mgr, ErrorListener errs)
        {
        MethodStructure method = (MethodStructure) getComponent();
        if (method == null)
            {
            // we are in an error state; we choose not to proceed with compilation
            mgr.deferChildren();
            return;
            }

        int cDefaults = method.getDefaultParamCount();
        if (cDefaults > 0)
            {
            StatementBlock block = adopt(new StatementBlock(Collections.EMPTY_LIST));

            StatementBlock.RootContext ctxMethod = block.new RootContext(method);
            Context                    ctx       = ctxMethod.validatingContext();

            int cParamExprs = params.size();
            int cTypeParams = method.getTypeParamCount();

            assert cParamExprs == method.getParamCount() - cTypeParams;

            for (int i = cParamExprs - cDefaults; i < cParamExprs; ++i)
                {
                org.xvm.asm.Parameter parameter = method.getParam(cTypeParams + i);
                assert parameter.hasDefaultValue();

                TypeConstant typeParam = parameter.getType();
                Parameter    param     = params.get(i);
                Expression   value     = param.value;

                Expression   valueNew;
                ctx      = ctx.enterInferring(typeParam);
                valueNew = value.validate(ctx, typeParam, errs);
                ctx      = ctx.exit();

                if (valueNew != null)
                    {
                    if (valueNew.isConstant())
                        {
                        parameter.setDefaultValue(value.toConstant());
                        }
                    else
                        {
                        value.log(errs, Severity.ERROR, Compiler.CONSTANT_REQUIRED);
                        }
                    }
                }
            }

        if (body != null && !body.compileMethod(method.createCode(), errs))
            {
            // the compilation has failed; no further progress is possible
            mgr.deferChildren();
            }
        }

    @Override
    protected Statement validateImpl(Context ctx, ErrorListener errs)
        {
        return this;
        }

    @Override
    protected boolean emit(Context ctx, boolean fReachable, Code code, ErrorListener errs)
        {
        return true;
        }

    // ----- internal ------------------------------------------------------------------------------

    protected org.xvm.asm.Annotation[] buildAnnotations(ConstantPool pool)
        {
        org.xvm.asm.Annotation[] aAnnotations = org.xvm.asm.Annotation.NO_ANNOTATIONS;
        if (annotations != null)
            {
            int cAnnotations = annotations.size();
            aAnnotations = new org.xvm.asm.Annotation[cAnnotations];
            for (int i = 0; i < cAnnotations; ++i)
                {
                aAnnotations[i] = annotations.get(i).ensureAnnotation(pool);
                }
            }

        return aAnnotations;
        }

    protected org.xvm.asm.Parameter[] buildParameters(ConstantPool pool)
        {
        // build array of parameters
        int cTypes  = typeParams == null ? 0 : typeParams.size();
        int cParams = cTypes + params.size();
        org.xvm.asm.Parameter[] aParams = new org.xvm.asm.Parameter[cParams];
        for (int i = 0; i < cTypes; ++i)
            {
            Parameter param = typeParams.get(i);
            TypeExpression exprType  = param.getType();
            TypeConstant constType = pool.ensureClassTypeConstant(pool.clzType(), null,
                    exprType == null
                            ? pool.typeObject()
                            : exprType.ensureTypeConstant());
            aParams[i] = new org.xvm.asm.Parameter(pool, constType, param.getName(), null, false, i, true);
            }
        for (int i = cTypes; i < cParams; ++i)
            {
            Parameter param = params.get(i-cTypes);
            aParams[i] = new org.xvm.asm.Parameter(pool, param.getType().ensureTypeConstant(),
                    param.getName(), null, false, i, false);
            if (param.value != null)
                {
                // TODO: ensure that all default values are grouped at the end
                aParams[i].markDefaultValue();
                }
            }
        return aParams;
        }

    /**
     * Find a method on the Ref class or any of the annotations that matches the specified
     * name and parameters of a "short-hand" property method declaration.
     *
     * @param property     the property structure
     * @param annotations  the annotations on the property
     * @param sMethName    the method name
     * @param params       the parameters
     * @param errs         the error listener
     *
     * @return the matching methods structure of null if none is found
     */
    protected MethodStructure findRefMethod(PropertyStructure property, List<Annotation> annotations,
            String sMethName, List<Parameter> params, ErrorListener errs)
        {
        ConstantPool pool = property.getConstantPool();

        ClassStructure clzRef = (ClassStructure) pool.clzRef().getComponent();
        if (clzRef == null)
            {
            // no class for "Ref" yet; come back later
            return null;
            }

        MethodStructure method = findMethod(pool, clzRef, sMethName, params);
        if (method == null)
            {
            if (annotations != null)
                {
                for (Iterator<Annotation> iter = annotations.iterator(); iter.hasNext();)
                    {
                    Annotation annotation = iter.next();

                    String        sAnnotation = annotation.getType().getName();
                    ClassConstant constClass  = (ClassConstant) pool.getImplicitlyImportedIdentity(sAnnotation);
                    if (constClass == null)
                        {
                        log(errs, Severity.ERROR, Compiler.NAME_UNRESOLVABLE, '@' + sAnnotation);
                        iter.remove();
                        continue;
                        }

                    ClassStructure clzMixin = (ClassStructure) constClass.getComponent();
                    if (clzMixin == null)
                        {
                        // no class for the annotation yet; come back later
                        continue;
                        }

                    method = findMethod(pool, clzMixin, sMethName, params);
                    if (method != null)
                        {
                        break;
                        }
                    }
                }
            }

        return method;
        }

    /**
     * Find a method on the specified ClassStructure that matches the specified name and parameters.
     *
     * @param pool        the constant pool
     * @param clz         the class structure
     * @param sMethName   the method name
     * @param parameters  the parameters
     *
     * @return the matching method structure
     */
    protected MethodStructure findMethod(ConstantPool pool, ClassStructure clz,
            String sMethName, List<Parameter> parameters)
        {
        MultiMethodStructure mms = (MultiMethodStructure) clz.getChild(sMethName);
        if (mms != null)
            {
            for (Component c : mms.children())
                {
                MethodStructure method = (MethodStructure) c;

                if (parameters.size() != method.getParamCount())
                    {
                    continue;
                    }

                // TODO: compare the ast.Parameters (parameters) with asm.Parameters (method)
                return method;
                }
            }
        // TODO: check the contributions (super, mixin, etc.)
        return null;
        }


    // ----- debugging assistance ------------------------------------------------------------------

    public String toSignatureString()
        {
        if (name == null)
            {
            MethodStructure struct = (MethodStructure) getComponent();
            return struct == null
                    ? "?()"
                    : struct.getIdentityConstant().getValueString();
            }

        StringBuilder sb = new StringBuilder();

        if (modifiers != null)
            {
            for (Token token : modifiers)
                {
                sb.append(token.getId().TEXT)
                        .append(' ');
                }
            }

        if (annotations != null)
            {
            for (Annotation annotation : annotations)
                {
                sb.append(annotation)
                        .append(' ');
                }
            }

        if (typeParams != null)
            {
            sb.append('<');
            boolean first = true;
            for (Parameter param : typeParams)
                {
                if (first)
                    {
                    first = false;
                    }
                else
                    {
                    sb.append(", ");
                    }
                sb.append(param.toTypeParamString());
                }
            sb.append("> ");
            }

        if (returns == null)
            {
            sb.append("<Unknown> ");
            }
        else if (returns.isEmpty())
            {
            sb.append("void ");
            }
        else if (returns.size() == 1)
            {
            sb.append(returns.get(0))
                    .append(' ');
            }
        else
            {
            sb.append(" (");
            boolean first = true;
            for (Parameter param : returns)
                {
                if (first)
                    {
                    first = false;
                    }
                else
                    {
                    sb.append(", ");
                    }
                sb.append(param);
                }
            sb.append(") ");
            }

        sb.append(getName());

        if (redundant != null)
            {
            sb.append('<');
            boolean first = true;
            for (TypeExpression type : redundant)
                {
                if (first)
                    {
                    first = false;
                    }
                else
                    {
                    sb.append(", ");
                    }
                sb.append(type);
                }
            sb.append('>');
            }

        if (params != null)
            {
            sb.append('(');
            boolean first = true;
            for (Parameter param : params)
                {
                if (first)
                    {
                    first = false;
                    }
                else
                    {
                    sb.append(", ");
                    }
                sb.append(param);
                }
            sb.append(')');
            }

        if (m_bodyFinally != null)
            {
            sb.append(" {..} finally {..}");
            }

        return sb.toString();
        }

    @Override
    public String toString()
        {
        StringBuilder sb = new StringBuilder();

        if (doc != null)
            {
            String sDoc = String.valueOf(doc.getValue());
            if (sDoc.length() > 100)
                {
                sDoc = sDoc.substring(0, 97) + "...";
                }
            appendString(sb.append("/*"), sDoc).append("*/\n");
            }

        sb.append(toSignatureString());

        if (body == null)
            {
            sb.append(';');
            }
        else
            {
            try
                {
                String sBody = body.toString();
                if (sBody.indexOf('\n') >= 0)
                    {
                    sb.append('\n')
                      .append(indentLines(sBody, "    "));
                    }
                else
                    {
                    sb.append(' ')
                      .append(sBody);
                    }
                }
            catch (RuntimeException e)
                {
                sb.append("[body]");
                }

            if (m_bodyFinally != null)
                {
                try
                    {
                    String sFinally = m_bodyFinally.toString();
                    sb.append("\nfinally");
                    if (sFinally.indexOf('\n') >= 0)
                        {
                        sb.append('\n')
                          .append(indentLines(sFinally, "    "));
                        }
                    else
                        {
                        sb.append(' ')
                          .append(sFinally);
                        }
                    }
                catch (RuntimeException e)
                    {
                    sb.append("[finally]");
                    }
                }
            }

        return sb.toString();
        }

    @Override
    public String getDumpDesc()
        {
        return toSignatureString();
        }


    // ----- fields --------------------------------------------------------------------------------

    protected Expression           condition;
    protected List<Token>          modifiers;
    protected List<Annotation>     annotations;
    protected List<Parameter>      typeParams;
    protected Token                conditional;
    protected List<Parameter>      returns;
    protected Token                name;
    protected List<TypeExpression> redundant;
    protected List<Parameter>      params;
    protected StatementBlock       body;
    protected Token                doc;

    private transient Token          m_tokFinally;
    private transient StatementBlock m_bodyFinally;

    // complementary statement for the constructor points to the finalizer and vice versa
    private transient MethodDeclarationStatement m_stmtComplement;

    private static final Field[] CHILD_FIELDS = fieldsForNames(MethodDeclarationStatement.class,
            "condition", "annotations", "typeParams", "returns", "redundant", "params", "body");
    }
