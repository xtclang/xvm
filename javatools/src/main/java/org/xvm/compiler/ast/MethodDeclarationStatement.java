package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.xvm.asm.Annotation;
import org.xvm.asm.ClassStructure;
import org.xvm.asm.Component;
import org.xvm.asm.Constant;
import org.xvm.asm.Constant.Format;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.Constants.Access;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.MethodStructure.Code;
import org.xvm.asm.ModuleStructure;
import org.xvm.asm.MultiMethodStructure;
import org.xvm.asm.PropertyStructure;

import org.xvm.asm.constants.AnnotatedTypeConstant;
import org.xvm.asm.constants.ClassConstant;
import org.xvm.asm.constants.IdentityConstant;
import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.MethodInfo;
import org.xvm.asm.constants.PropertyConstant;
import org.xvm.asm.constants.PropertyInfo;
import org.xvm.asm.constants.TypeConstant;
import org.xvm.asm.constants.TypeInfo;

import org.xvm.compiler.Compiler;
import org.xvm.compiler.Constants;
import org.xvm.compiler.Token;
import org.xvm.compiler.Token.Id;

import org.xvm.compiler.ast.StatementBlock.RootContext;

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

    public MethodDeclarationStatement(long                       lStartPos,
                                      long                       lEndPos,
                                      Expression                 condition,
                                      List<Token>                modifiers,
                                      List<AnnotationExpression> annotations,
                                      List<Parameter>            typeParams,
                                      Token                      conditional,
                                      List<Parameter>            returns,
                                      Token                      name,
                                      List<TypeExpression>       redundant,
                                      List<Parameter>            params,
                                      StatementBlock             body,
                                      Token                      tokFinally,
                                      StatementBlock             bodyFinally,
                                      Token                      doc)
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

        // turn "<expr>" into the statement block "{ return <expr>; }"
        Token fakeReturn = new Token(expr.getStartPosition(), expr.getStartPosition(), Id.RETURN);
        ReturnStatement stmt = new ReturnStatement(fakeReturn, expr);
        stmt.adopt(expr);
        body = new StatementBlock(Collections.singletonList(stmt), expr.getStartPosition(), expr.getEndPosition());
        body.adopt(stmt);

        adopt(body);
        }

    /**
     * Create a MethodDeclarationStatement that turns a statement block into a MethodStructure.
     * This is used by the "eval" compiler.
     *
     * @param struct  the MethodStructure that this MethodDeclarationStatement is intended to
     *                compile into
     * @param body    the StatementBlock that the resulting method must use
     */
    public MethodDeclarationStatement(MethodStructure struct, StatementBlock body)
        {
        super(body.getStartPosition(), body.getEndPosition());

        this.body = body;

        setComponent(struct);
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
     * @return true iff this statement represents a validator
     */
    public boolean isValidator()
        {
        return name != null && name.getId() == Id.ASSERT;
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
        if (stmtFirst instanceof ReturnStatement stmtRet && stmtRet.exprs.size() == 1)
            {
            return stmtRet.exprs.get(0);
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

    @Override
    protected AstNode getCodeContainer()
        {
        return null;
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
            if (container == null)
                {
                // something went wrong; there must have already been an error
                return;
                }

            String sName = getName();
            if (container.isMethodContainer())
                {
                boolean fConstructor = isConstructor();
                boolean fFinally     = isConstructorFinally();
                boolean fValidator   = isValidator();
                boolean fFunction    = isStatic(modifiers) || fConstructor ||
                        container.isStatic() && !(container instanceof ClassStructure);

                // build array of annotations
                ConstantPool pool         = container.getConstantPool();
                Annotation[] aAnnotations = buildAnnotations(pool);

                // build array of return types
                org.xvm.asm.Parameter[] aReturns;
                if (returns == null)
                    {
                    if (container instanceof PropertyStructure)
                        {
                        if (fFunction)
                            {
                            log(errs, Severity.ERROR, Compiler.FUNCTION_NOT_ALLOWED, sName);
                            return;
                            }
                        // it's a "shorthand" property method; stop right here
                        // will continue resolution in resolveNames() below
                        break CreateStructure;
                        }

                    if (fConstructor || fFinally || fValidator)
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

                if (fValidator)
                    {
                    if (modifiers != null && !modifiers.isEmpty())
                        {
                        Token tok = modifiers.get(0);
                        errs.log(Severity.ERROR, Compiler.ILLEGAL_MODIFIER, null,
                            getSource(), tok.getStartPosition(), tok.getEndPosition());
                        return;
                        }
                    if (params != null && !params.isEmpty())
                        {
                        params.get(0).log(errs, Severity.ERROR, Compiler.VALIDATOR_PARAMS_UNEXPECTED);
                        return;
                        }

                    if (body == null)
                        {
                        log(errs, Severity.ERROR, Compiler.VALIDATOR_BODY_MISSING);
                        return;
                        }
                    }

                org.xvm.asm.Parameter[] aParams = buildParameters(pool, errs);
                if (aParams == null)
                    {
                    return;
                    }

                Access  access = container instanceof MethodStructure
                                        ? Access.PRIVATE
                                        : getDefaultAccess();
                boolean fUsesSuper = !fFunction && !fFinally && !fValidator
                                        && access != Access.PRIVATE && usesSuper();
                MethodStructure method;
                if (fFinally)
                    {
                    method = container.createMethod(false, Access.PRIVATE, null,
                            aReturns, "finally", aParams, true, false);

                    if (body != null)
                        {
                        body.donateSource(method);
                        }

                    MethodStructure methodConstruct = (MethodStructure) m_stmtComplement.getComponent();
                    if (methodConstruct != null && method != null)
                        {
                        methodConstruct.setConstructFinally(method);
                        }
                    }
                else
                    {
                    if (body == null)
                        {
                        if (fFunction && container.getFormat() != Component.Format.INTERFACE)
                            {
                            log(errs, Severity.ERROR, Compiler.FUNCTION_BODY_MISSING, sName);
                            return;
                            }
                        if (!fFunction && container instanceof ClassStructure clz && clz.isSingleton())
                            {
                            log(errs, Severity.ERROR, Compiler.METHOD_BODY_MISSING, sName);
                            return;
                            }
                        }

                    if (fConstructor && container.getFormat() == Component.Format.INTERFACE &&
                            (body != null || access != Access.PUBLIC))
                        {
                        log(errs, Severity.ERROR, Compiler.ILLEGAL_VIRTUAL_CONSTRUCTOR);
                        return;
                        }

                    method = container.createMethod(fFunction, access, aAnnotations,
                            aReturns, sName, aParams, body != null, fUsesSuper);

                    if (method != null && fConstructor)
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

                    if (body != null)
                        {
                        body.donateSource(method);
                        }
                    }

                if (method == null)
                    {
                    log(errs, Severity.ERROR, Compiler.DUPLICATE_METHOD, sName, container);
                    return;
                    }

                setComponent(method);
                }
            else
                {
                log(errs, Severity.ERROR, Compiler.METHOD_UNEXPECTED, sName, container);
                return;
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
                if (returns == null && container instanceof PropertyStructure property)
                    {
                    // this is a short-hand property method
                    List<AnnotationExpression> annotations =
                        ((PropertyDeclarationStatement) getParent().getParent()).annotations;

                    MethodStructure methodSuper = findRefMethod(property, annotations, sName, params);
                    if (methodSuper == null)
                        {
                        if (annotations != null)
                            {
                            for (AnnotationExpression anno : annotations)
                                {
                                TypeConstant type = anno.toTypeExpression().getTypeConstant();
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
                                    && "Referent".equals(((PropertyConstant) constReturn).getName()))
                                {
                                // replace the Referent with the actual property type
                                param = new org.xvm.asm.Parameter(pool,
                                        property.getType(), param.getName(), null, true, i, false);
                                }
                            }
                        aReturns[i] = param;
                        }

                    org.xvm.asm.Parameter[] aParams = buildParameters(pool, errs);
                    if (aParams == null)
                        {
                        return;
                        }

                    // the parameters were already matched; no need to re-check
                    Annotation[] annos = new Annotation[]
                            {pool.ensureAnnotation(pool.clzOverride())};
                    MethodStructure method = container.createMethod(
                            false, methodSuper.getAccess(), annos, aReturns, sName, aParams,
                            body != null, usesSuper());
                    if (body != null)
                        {
                        body.donateSource(method);
                        }
                    setComponent(method);
                    }
                }
            }

        MethodStructure method = (MethodStructure) getComponent();
        if (method != null)
            {
            // methods are opaque, so everything inside the curlies can be deferred until we get to the
            // validateContent() stage
            mgr.processChildrenExcept((child) -> child == body);

            List<Parameter> listParams = params;
            if (listParams != null)
                {
                for (int i = 0, c = listParams.size(); i < c; i++)
                    {
                    Parameter param = listParams.get(i);

                    TypeExpression exprType = param.getType();
                    while (exprType instanceof AnnotatedTypeExpression exprAnno)
                        {
                        if (exprAnno.isDisassociated())
                            {
                            method.getParam(i).addAnnotation(
                                exprAnno.getAnnotation().ensureAnnotation(pool()));
                            }
                        exprType = exprAnno.type;
                        }
                    }
                }

            // sort out which annotations go on the method or its parameters, and which belong to
            // the return type
            if (!method.resolveAnnotations() || !method.resolveTypedefs())
                {
                mgr.requestRevisit();
                }
            else if (method.isFunction())
                {
                // make sure functions don't use any generic types
                for (TypeConstant type : method.getIdentityConstant().getRawParams())
                    {
                    if (type.containsGenericType(true))
                        {
                        log(errs, Severity.ERROR, Compiler.GENERIC_FUNCTION_TYPE_NOT_ALLOWED,
                                method.getName(), type.getValueString());
                        }
                    }
                for (TypeConstant type : method.getIdentityConstant().getRawReturns())
                    {
                    if (type.containsGenericType(true))
                        {
                        log(errs, Severity.ERROR, Compiler.GENERIC_FUNCTION_TYPE_NOT_ALLOWED,
                                method.getName(), type.getValueString());
                        }
                    }
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
            return;
            }

        ConstantPool   pool      = pool();
        ClassStructure clzParent = method.getContainingClass();
        if (method.getChildrenCount() > 0)
            {
            // the discovery of new structures means that any TypeInfo that was already created will
            // be wrong
            IdentityConstant idClz  = clzParent.getIdentityConstant();
            ConstantPool     poolId = idClz.getConstantPool();
            pool.invalidateTypeInfos(idClz);
            if (pool != poolId)
                {
                poolId.invalidateTypeInfos(idClz);
                }
            }

        // check for invalid method annotations
        Annotation[] aAnno  = method.getAnnotations();
        int          cAnnos = aAnno.length;
        if (cAnnos > 0)
            {
            boolean      fReordered = false;
            TypeConstant typeBase   = method.getIdentityConstant().getValueType(pool, null);

            Validate:
            if (cAnnos == 1)
                {
                // optimize the most common scenario
                Annotation anno = aAnno[0];
                if (anno.getAnnotationClass().equals(pool.clzOverride()))
                    {
                    if (!validateOverride(clzParent, method, errs))
                        {
                        return; // an error must've been reported
                        }
                    break Validate;
                    }

                TypeConstant typeMixin = anno.getFormalType();
                if (typeMixin.getExplicitClassFormat() != Component.Format.MIXIN)
                    {
                    findAnnotationExpression(anno, annotations).
                        log(errs, Severity.ERROR, Constants.VE_ANNOTATION_NOT_MIXIN,
                            anno.getValueString());
                    return;
                    }

                TypeConstant typeInto    = typeMixin.getExplicitClassInto();
                boolean      fApplicable = typeInto.isIntoMetaData(typeBase, true)
                    && typeBase.isA(typeInto.resolveGenerics(pool, typeBase));

                if (!fApplicable)
                    {
                    findAnnotationExpression(anno, annotations).
                        log(errs, Severity.ERROR, Compiler.ANNOTATION_NOT_APPLICABLE,
                            anno.getValueString(), typeBase);
                    return;
                    }
                }
            else
                {
                // collect the mixin types and check for duplicates
                TypeConstant[]    atypeMixin = new TypeConstant[cAnnos];
                TypeConstant[]    atypeInto  = new TypeConstant[cAnnos];
                Set<TypeConstant> setTypes   = new HashSet<>();
                for (int i = 0; i < cAnnos; i++)
                    {
                    Annotation anno = aAnno[i];
                    if (anno.getAnnotationClass().equals(pool.clzOverride()))
                        {
                        if (!validateOverride(clzParent, method, errs))
                            {
                            return; // an error must've been reported
                            }
                        }
                    TypeConstant typeMixin = anno.getFormalType();
                    if (typeMixin.getExplicitClassFormat() != Component.Format.MIXIN)
                        {
                        findAnnotationExpression(anno, annotations).
                            log(errs, Severity.ERROR, Constants.VE_ANNOTATION_NOT_MIXIN,
                                anno.getValueString());
                        return;
                        }
                    if (!setTypes.add(typeMixin))
                        {
                        findAnnotationExpression(anno, annotations).
                            log(errs, Severity.ERROR, Constants.VE_ANNOTATION_REDUNDANT,
                                anno.getValueString());
                        }
                    atypeMixin[i] = typeMixin;
                    atypeInto[i]  = typeMixin.getExplicitClassInto();
                    }

                // While validating applicability we allow the method annotations to be
                // arranged in the most "readable" fashion, but not strictly "correct" order.
                // For example, consider Get and Produces annotations from web.xtclang.org.
                // "Get" extends "Endpoint" mixin, which is "into Method", while "Produces" is into
                // "Endpoint". The technically correct annotation ordering therefore should be:
                //      @Produces @Get void f() {...}
                // but it doesn't read as well as much more natural sequence:
                //      @Get @Produces void f() {...}
                // What we are trying to do here is to automatically reorder an "illegal" sequence
                // into a legal one.
                ErrorListener errsTemp = errs.branch(this);
                if (validateAnnotations(typeBase, aAnno, atypeMixin, atypeInto, errsTemp))
                    {
                    // the original order is good; nothing to do
                    break Validate;
                    }

                TypeConstant typeNext = null;
                int          iNext    = atypeInto.length-1;
                while (true)
                    {
                    int iFound = -1;
                    for (int i = iNext; i >= 0; i--)
                        {
                        if (isApplicable(atypeInto[i], typeBase, typeNext))
                            {
                            iFound = i;
                            break;
                            }
                        }
                    if (iFound == -1)
                        {
                        // not found; report the error
                        break;
                        }

                    // move the applicable annotation to the "next" position
                    shuffle(aAnno,      iFound, iNext);
                    shuffle(atypeInto,  iFound, iNext);
                    shuffle(atypeMixin, iFound, iNext);

                    if (validateAnnotations(typeBase, aAnno, atypeMixin, atypeInto, ErrorListener.BLACKHOLE))
                        {
                        fReordered = true;
                        break Validate;
                        }

                    typeNext = typeNext == null
                            ? atypeMixin[iNext]
                            : new AnnotatedTypeConstant(pool, aAnno[iNext], typeNext);
                    iNext--;
                    }

                // nothing worked; report the error that was detected when using the original order
                errsTemp.merge();
                return;
                }

            if (fReordered)
                {
                method.reorderAnnotations(aAnno);
                }
            }

        // validate accessibility of all generic types
        // Note: most commonly, the top level (not anonymous) class knows what generic types are
        //       visible; in some scenarios, however, the anonymous parent class knows better
        ClassStructure clzTop = method.getContainingClass(false);

        for (org.xvm.asm.Parameter param : method.getParamArray())
            {
            String sInvalid = clzTop.checkGenericTypeVisibility(param.getType());
            if (sInvalid != null)
                {
                if (clzParent == clzTop ||
                        clzParent.checkGenericTypeVisibility(param.getType()) != null)
                    {
                    log(errs, Severity.ERROR, Compiler.TYPE_PARAMS_UNRESOLVABLE, sInvalid);
                    return;
                    }
                }
            }

        org.xvm.asm.Parameter[] aReturns = method.getReturnArray();
        if (aReturns.length > 0)
            {
            for (org.xvm.asm.Parameter param : aReturns)
                {
                String sInvalid = clzTop.checkGenericTypeVisibility(param.getType());
                if (sInvalid != null)
                    {
                    if (clzParent == clzTop ||
                            clzParent.checkGenericTypeVisibility(param.getType()) != null)
                        {
                        log(errs, Severity.ERROR, Compiler.TYPE_PARAMS_UNRESOLVABLE, sInvalid);
                        return;
                        }
                    }
                }

            // validate annotations added to the return type by MethodStructure.resolveAnnotations()
            TypeConstant typeRet = (aReturns[0].isConditionalReturn()
                    ? aReturns[1]
                    : aReturns[0]).getType();
            typeRet.validate(errs);
            }
        }

    /**
     * Validate the @Override applicability. Note, that strictly speaking this check is not
     * necessary, since a corresponding verification would be reported by the TypeInfo computation
     * logic anyway, but it allows to provide a better compiler error message.
     */
    private boolean validateOverride(ClassStructure clzParent, MethodStructure method, ErrorListener errs)
        {
        if (method.isConstructor())
            {
            // we don't collect the constructor chains in the TypeInfo, so the check below wouldn't
            // work; we may need to improve this and use logic similar to the special "super"
            // processing in InvocationExpression.resolveName()
            return true;
            }

        ErrorListener errsTemp   = errs.branch(this);
        MethodInfo    infoMethod = clzParent.getFormalType().ensureAccess(Access.PRIVATE).
                                    ensureTypeInfo(errsTemp).
                                    getMethodById(method.getIdentityConstant());
        if (infoMethod == null || errsTemp.hasSeriousErrors())
            {
            // something is seriously wrong with this method; the "@Override" is the least of the
            // issues; ignore it and the problem will be reported in due time
            errsTemp.merge();
            return true;
            }

        if (infoMethod.getChain().length < 2)
            {
            log(errs, Severity.ERROR, Compiler.SUPER_MISSING);
            return false;
            }
        return true;
        }

    /**
     * Validate the applicability of the mixin annotations to the specified base type.
     */
    private boolean validateAnnotations(TypeConstant typeBase, Annotation[] aAnno,
                                        TypeConstant[] atypeMixin, TypeConstant[] atypeInto,
                                        ErrorListener errs)
        {
        ConstantPool pool     = pool();
        TypeConstant typeNext = null;

        for (int iNext = aAnno.length-1; true; iNext--)
            {
            TypeConstant typeInto = atypeInto[iNext];

            if (!isApplicable(typeInto, typeBase, typeNext))
                {
                Annotation anno = aAnno[iNext];
                findAnnotationExpression(anno, annotations).
                    log(errs, Severity.ERROR, Compiler.ANNOTATION_NOT_APPLICABLE,
                        anno.getValueString(), typeBase.getValueString());
                return false;
                }

            if (iNext == 0)
                {
                return true;
                }

            // IMPORTANT: the annotation hasn't been validated yet; don't register the annotated
            //            type with the constant pool, since it will prematurely register the
            //            annotation before its parameters are fully resolved
            typeNext = typeNext == null
                    ? atypeMixin[iNext]
                    : new AnnotatedTypeConstant(pool, aAnno[iNext], typeNext);
            }
        }

    /**
     * Check if the specified "into" type applies to the specified "base" type and potentially
     * to the "next" annotation type.
     *
     * Note, that the "into" for the first mixin *must* be strictly into the base type (which is
     * either Method or Function), but the following could also apply to the previous ones.
     */
    private boolean isApplicable(TypeConstant typeInto, TypeConstant typeBase, TypeConstant typeNext)
        {
        if (typeInto.isIntoMetaData(typeBase, true))
            {
            return typeBase.isA(typeInto.resolveGenerics(pool(), typeBase));
            }

        return typeNext != null && typeNext.isA(typeInto);
        }

    /**
     * Move the i-th element of the array to the j-th position.
     */
    private static void shuffle(Object[] ao, int i, int j)
        {
        assert i < j;

        Object t = ao[i];
        System.arraycopy(ao, i+1, ao, i, j-i);
        ao[j] = t;
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

        if (!errs.isSilent() && !method.isConstructor())
            {
            ClassStructure clz  = method.getContainingClass();
            TypeConstant   type = pool().ensureAccessTypeConstant(clz.getFormalType(), Access.PRIVATE);
            TypeInfo       info = type.ensureTypeInfo(errs);
            PropertyInfo   prop = info.findProperty(method.getName());
            if (prop != null &&
                prop.getIdentity().getNestedDepth() == method.getIdentityConstant().getNestedDepth() - 1)
                {
                log(errs, Severity.ERROR, Compiler.METHOD_NAME_COLLISION,
                        method.getName(), prop.getIdentity().getNamespace().getPathString());
                }
            }

        int cDefaults = method.getDefaultParamCount();
        if (cDefaults > 0)
            {
            StatementBlock block = adopt(new StatementBlock(Collections.emptyList()));

            RootContext ctxMethod = new RootContext(block, method);
            Context     ctx       = ctxMethod.validatingContext();

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

                assert value != null;
                Expression valueNew;
                ctx      = ctx.enterInferring(typeParam);
                valueNew = value.validate(ctx, typeParam, errs);
                ctx      = ctx.exit();

                if (valueNew != null)
                    {
                    value = valueNew;
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

        compileBody(mgr, method, errs);
        }

    protected void compileBody(StageMgr mgr, MethodStructure method, ErrorListener errs)
        {
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

    protected Annotation[] buildAnnotations(ConstantPool pool)
        {
        Annotation[] aAnnotations = Annotation.NO_ANNOTATIONS;
        if (annotations != null)
            {
            int cAnnotations = annotations.size();
            aAnnotations = new Annotation[cAnnotations];
            for (int i = 0; i < cAnnotations; ++i)
                {
                aAnnotations[i] = annotations.get(i).ensureAnnotation(pool);
                }
            }

        return aAnnotations;
        }

    /**
     * @return an array of parameters or null if an error has been reported
     */
    protected org.xvm.asm.Parameter[] buildParameters(ConstantPool pool, ErrorListener errs)
        {
        // build array of parameters
        int cTypes  = typeParams == null ? 0 : typeParams.size();
        int cParams = cTypes + params.size();

        Set<String>             setNames = cParams < 2 ? null : new HashSet<>();
        org.xvm.asm.Parameter[] aParams  = new org.xvm.asm.Parameter[cParams];
        for (int i = 0; i < cTypes; ++i)
            {
            Parameter      param     = typeParams.get(i);
            String         sName     = param.getName();
            TypeExpression exprType  = param.getType();
            TypeConstant   typeParam = pool.ensureClassTypeConstant(pool.clzType(), null,
                    exprType == null
                            ? pool.typeObject()
                            : exprType.ensureTypeConstant());
            if (setNames != null && !setNames.add(sName))
                {
                log(errs, Severity.ERROR, Compiler.VAR_DEFINED, sName);
                return null;
                }
            aParams[i] = new org.xvm.asm.Parameter(pool, typeParam, sName, null, false, i, true);
            }

        boolean fDefaultRequired = false;
        for (int i = cTypes; i < cParams; ++i)
            {
            Parameter      param    = params.get(i - cTypes);
            String         sName    = param.getName();
            TypeExpression exprType = param.getType();
            TypeConstant   typeArg  = exprType.ensureTypeConstant();

            if (setNames != null && !setNames.add(sName))
                {
                log(errs, Severity.ERROR, Compiler.VAR_DEFINED, sName);
                return null;
                }
            aParams[i] = new org.xvm.asm.Parameter(pool, typeArg, sName, null, false, i, false);

            if (param.value == null)
                {
                if (fDefaultRequired)
                    {
                    param.log(errs, Severity.ERROR, Compiler.DEFAULT_VALUE_REQUIRED, sName);
                    }
                }
            else
                {
                aParams[i].markDefaultValue();
                fDefaultRequired = true;
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
     *
     * @return the matching methods structure of null if none is found
     */
    protected MethodStructure findRefMethod(PropertyStructure property,
                                            List<AnnotationExpression> annotations,
                                            String sMethName, List<Parameter> params)
        {
        ConstantPool   pool   = property.getConstantPool();
        ClassStructure clzRef = (ClassStructure) pool.clzRef().getComponent();
        if (clzRef == null)
            {
            // no class for "Ref" yet; come back later
            return null;
            }
        MethodStructure method = findMethod(pool, clzRef, sMethName, params);
        if (method != null)
            {
            return method;
            }

        ClassStructure clzVar = (ClassStructure) pool.clzVar().getComponent();
        if (clzVar == null)
            {
            // no class for "Var" yet; come back later
            return null;
            }
        method = findMethod(pool, clzVar, sMethName, params);
        if (method != null)
            {
            return method;
            }

        if (annotations != null)
            {
            for (Iterator<AnnotationExpression> iter = annotations.iterator(); iter.hasNext();)
                {
                AnnotationExpression annotation = iter.next();

                String        sAnnotation = annotation.toTypeExpression().getName();
                ClassConstant constClass  = (ClassConstant) pool.getImplicitlyImportedIdentity(sAnnotation);
                if (constClass == null)
                    {
                    // not well-known annotation; continue with others
                    continue;
                    }

                ClassStructure clzMixin = (ClassStructure) constClass.getComponent();
                if (clzMixin == null)
                    {
                    // no class for the annotation yet; try others or come back later
                    continue;
                    }

                method = findMethod(pool, clzMixin, sMethName, params);
                if (method != null)
                    {
                    break;
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
        Component child = clz.getChild(sMethName);
        if (child instanceof MultiMethodStructure mms)
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

    /**
     * Used only for setting up conditional break points.
     */
    private String path()
        {
        MethodConstant  idMethod  = ((MethodStructure) getComponent()).getIdentityConstant();
        ModuleStructure module    = (ModuleStructure) idMethod.getModuleConstant().getComponent();
        return module.getName() + "/" + idMethod.getPathString();
        }

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
            for (AnnotationExpression annotation : annotations)
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

    protected Expression                 condition;
    protected List<Token>                modifiers;
    protected List<AnnotationExpression> annotations;
    protected List<Parameter>            typeParams;
    protected Token                      conditional;
    protected List<Parameter>            returns;
    protected Token                      name;
    protected List<TypeExpression>       redundant;
    protected List<Parameter>            params;
    protected StatementBlock             body;
    protected Token                      doc;

    private transient Token          m_tokFinally;
    private transient StatementBlock m_bodyFinally;

    // complementary statement for the constructor points to the finalizer and vice versa
    private transient MethodDeclarationStatement m_stmtComplement;

    private static final Field[] CHILD_FIELDS = fieldsForNames(MethodDeclarationStatement.class,
            "condition", "annotations", "typeParams", "returns", "redundant", "params", "body");
    }