package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.xvm.asm.Annotation;
import org.xvm.asm.Argument;
import org.xvm.asm.Assignment;
import org.xvm.asm.ClassStructure;
import org.xvm.asm.Component.Format;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Register;

import org.xvm.asm.constants.ClassConstant;
import org.xvm.asm.constants.ExpressionConstant;
import org.xvm.asm.constants.IdentityConstant;
import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.TypeConstant;
import org.xvm.asm.constants.TypeInfo;
import org.xvm.asm.constants.TypeInfo.MethodKind;
import org.xvm.asm.constants.UnresolvedNameConstant;

import org.xvm.compiler.Compiler;
import org.xvm.compiler.Compiler.Stage;
import org.xvm.compiler.Constants;
import org.xvm.compiler.Source;
import org.xvm.compiler.Token;
import org.xvm.compiler.Token.Id;

import org.xvm.util.Severity;


/**
 * A type annotation is used for type annotations with an optional argument list.
 */
public class AnnotationExpression
        extends Expression
    {
    // ----- constructors --------------------------------------------------------------------------

    public AnnotationExpression(NamedTypeExpression type, List<Expression> args, long lStartPos, long lEndPos)
        {
        this.type      = type;
        this.args      = args;
        this.lStartPos = lStartPos;
        this.lEndPos   = lEndPos;
        }

    public AnnotationExpression(Annotation anno, AstNode node)
        {
        this.m_anno    = anno;
        this.m_node    = node;
        this.lStartPos = node.getStartPosition();
        this.lEndPos   = node.getEndPosition();
        }


    // ----- accessors -----------------------------------------------------------------------------

    @Override
    public NamedTypeExpression toTypeExpression()
        {
        NamedTypeExpression exprType = type;
        if (exprType == null)
            {
            assert m_node != null && m_anno != null;
            List<Token> names = Collections.singletonList(new Token(lStartPos, lEndPos,
                    Id.IDENTIFIER, ((IdentityConstant) m_anno.getAnnotationClass()).getName()));
            exprType = new NamedTypeExpression(null, names, null, null, null, lEndPos);
            exprType.setParent(getParent());
            type = exprType;
            }
        return exprType;
        }

    @Override
    public boolean isConstant()
        {
        return m_fConst;
        }

    @Override
    public boolean isAutoNarrowingAllowed(TypeExpression type)
        {
        return false;
        }

    @Override
    protected boolean canResolveNames()
        {
        return m_anno != null || super.canResolveNames() || type.canResolveNames();
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

    /**
     * Build an XVM Annotation that corresponds to the information that this AST Annotation has
     * available.
     *
     * @param pool  the ConstantPool to use to create the XVM structures
     *
     * @return an XVM Annotation
     */
    public Annotation ensureAnnotation(ConstantPool pool)
        {
        if (m_anno != null)
            {
            return m_anno;
            }

        Constant         constClass  = toTypeExpression().getIdentityConstant();
        List<Expression> listArgs    = args;
        Constant[]       aconstArgs  = Constant.NO_CONSTS;
        int              cArgs       = listArgs == null ? 0 : listArgs.size();
        if (cArgs > 0)
            {
            aconstArgs = new Constant[cArgs];
            for (int iArg = 0; iArg < cArgs; ++iArg)
                {
                Expression exprArg = listArgs.get(iArg);
                if (exprArg.alreadyReached(Stage.Validated))
                    {
                    aconstArgs[iArg] = exprArg.isConstant()
                            ? exprArg.toConstant()
                            : new ExpressionConstant(pool, exprArg);
                    continue;
                    }

                if (exprArg instanceof LabeledExpression exprLbl)
                    {
                    exprArg = exprLbl.getUnderlyingExpression();
                    }

                if (exprArg instanceof LiteralExpression exprLit)
                    {
                    if (exprLit.getLiteral().getId() == Id.LIT_STRING)
                        {
                        // only String literals have a predictable runtime type (no @Auto conversions)
                        aconstArgs[iArg] = pool.ensureStringConstant(((LiteralExpression) exprArg)
                                .getLiteral().getValue().toString());
                        }
                    else
                        {
                        aconstArgs[iArg] = exprLit.getLiteralConstant();
                        }
                    }
                else if (exprArg instanceof NameExpression exprName)
                    {
                    aconstArgs[iArg] = new UnresolvedNameConstant(pool,
                            exprName.collectNames(1), false);
                    }
                else
                    {
                    aconstArgs[iArg] = new ExpressionConstant(pool, exprArg);
                    }
                }
            }

        return m_anno = pool.ensureAnnotation(constClass, aconstArgs);
        }


    // ----- compilation ---------------------------------------------------------------------------

    @Override
    public void validateContent(StageMgr mgr, ErrorListener errs)
        {
        Annotation anno = m_anno;
        assert anno != null;

        Constant constAnno = anno.getAnnotationClass();
        if (constAnno.containsUnresolved())
            {
            // the type name resolution failed and an error must have been reported
            return;
            }

        ClassConstant idAnno   = (ClassConstant) constAnno;
        TypeConstant  typeAnno = type == null
                ? idAnno.getFormalType()
                : type.ensureTypeConstant();

        TypeInfo infoAnno = typeAnno.ensureTypeInfo(errs);
        if (infoAnno.getFormat() != Format.MIXIN)
            {
            log(errs, Severity.ERROR, Constants.VE_ANNOTATION_NOT_MIXIN, anno.getValueString());
            return;
            }

        if (!mgr.processChildren())
            {
            mgr.requestRevisit();
            return;
            }

        if (getCodeContainer() != null)
            {
            return;
            }

        List<Expression>  listArgs = args;
        int               cArgs    = listArgs == null ? 0 : listArgs.size();
        ValidatingContext ctx      = new ValidatingContext(getComponent().getContainingClass());
        ErrorListener     errsTemp = errs.branch(this);

        // find a matching constructor on the annotation class
        MethodConstant idConstruct = findMethod(ctx, typeAnno, infoAnno,
                    "construct", listArgs, MethodKind.Constructor, true, false, TypeConstant.NO_TYPES, errsTemp);

        errsTemp.merge();
        if (idConstruct == null)
            {
            if (!errsTemp.hasSeriousErrors())
                {
                log(errs, Severity.ERROR, Compiler.MISSING_CONSTRUCTOR, idAnno.getValueString());
                }
            return;
            }

        if (cArgs > 0)
            {
            MethodStructure constructor = infoAnno.getMethodById(idConstruct).
                                            getTopmostMethodStructure(infoAnno);
            if (containsNamedArgs(listArgs))
                {
                listArgs = rearrangeNamedArgs(constructor, listArgs, errs);
                if (listArgs == null)
                    {
                    return;
                    }
                args  = listArgs;
                cArgs = listArgs.size();
                }

            // validate the argument expressions and fix up all the constants used as arguments to
            // construct the annotation
            TypeConstant[] atypeParams = idConstruct.getRawParams();
            int            cAll        = constructor.getParamCount();
            int            cDefault    = constructor.getDefaultParamCount();
            Constant[]     aconstArgs  = new Constant[cAll];
            boolean        fDefaults   = cArgs < cAll;

            assert cArgs <= cAll && cArgs >= cAll - cDefault;

            for (int iArg = 0; iArg < cArgs; ++iArg)
                {
                Expression exprOld = listArgs.get(iArg);
                int        iParam  = exprOld instanceof LabeledExpression exprLbl
                        ? constructor.getParam(exprLbl.getName()).getIndex()
                        : iArg;

                Expression exprNew = exprOld.validate(ctx, atypeParams[iParam], errs);
                if (exprNew != null)
                    {
                    if (exprNew != exprOld)
                        {
                        listArgs.set(iArg, exprNew);
                        }

                    if (exprNew.isRuntimeConstant())
                        {
                        // update the Annotation directly
                        // Note: this is quite unusual, in that normally things like an annotation are
                        //       treated as a constant once instantiated, but in this case, it was
                        //       impossible to validate the arguments of the annotation when it was
                        //       constructed, because we were too early in the compile cycle to resolve
                        //       any constant expressions that refer to anything _by name_
                        aconstArgs[iParam] = exprNew.toConstant();
                        }
                    else if (exprNew.isNonBinding())
                        {
                        fDefaults = true;
                        }
                    else
                        {
                        exprOld.log(errs, Severity.ERROR, Compiler.CONSTANT_REQUIRED);
                        }
                    }
                }

            if (fDefaults)
                {
                // fill the default values
                for (int iParam = 0; iParam < cAll; ++iParam)
                    {
                    if (aconstArgs[iParam] == null)
                        {
                        aconstArgs[iParam] = pool().valDefault();
                        }
                    }
                }

            anno.resolveParams(aconstArgs);
            }
        }

    @Override
    protected Expression validate(Context ctx, TypeConstant typeRequired, ErrorListener errs)
        {
        ConstantPool     pool     = pool();
        List<Expression> listArgs = args;
        int              cArgs    = listArgs == null ? 0 : listArgs.size();

        NamedTypeExpression exprTypeOld = toTypeExpression();
        NamedTypeExpression exprTypeNew = (NamedTypeExpression) exprTypeOld.validate(ctx, null, errs);

        if (exprTypeNew == null)
            {
            return null;
            }
        type = exprTypeNew;

        TypeConstant  typeAnno = exprTypeNew.ensureTypeConstant(ctx, errs);
        ClassConstant idAnno   = (ClassConstant) typeAnno.getDefiningConstant();

        if (typeRequired != null)
            {
            TypeConstant typeInferred = inferTypeFromRequired(typeAnno, typeRequired);
            if (typeInferred != null)
                {
                typeAnno = typeInferred;
                }
            }

        if (idAnno.equals(pool.clzInject()) && cArgs == 0)
            {
            // the "resourceName" will come from the variable/property name
            return finishValidation(ctx, typeRequired, typeAnno, TypeFit.Fit, null, errs);
            }

        if (typeRequired != null && !typeAnno.isA(typeRequired))
            {
            log(errs, Severity.ERROR, Compiler.WRONG_TYPE,
                    typeRequired.getValueString(), typeAnno.getValueString());
            return null;
            }

        TypeInfo       infoAnno    = typeAnno.ensureTypeInfo(errs);
        MethodConstant idConstruct = findMethod(ctx, typeAnno, infoAnno, "construct", listArgs,
                MethodKind.Constructor, true, false, null, errs);
        if (idConstruct == null)
            {
            log(errs, Severity.ERROR, Compiler.MISSING_CONSTRUCTOR, idAnno.getValueString());
            return null;
            }

        if (cArgs > 0 && containsNamedArgs(listArgs))
            {
            listArgs = rearrangeNamedArgs((MethodStructure) idConstruct.getComponent(), listArgs, errs);
            if (listArgs == null)
                {
                return null;
                }
            args  = listArgs;
            cArgs = listArgs.size();
            }

        // validate the arguments
        TypeConstant[] atypeParam = idConstruct.getRawParams();
        boolean        fValid     = true;
        boolean        fConst     = true;

        assert atypeParam.length >= cArgs;

        for (int iArg = 0; iArg < cArgs; ++iArg)
            {
            Expression exprArgOld = listArgs.get(iArg);
            Expression exprArgNew = exprArgOld.validate(ctx, atypeParam[iArg], errs);

            if (exprArgNew == null)
                {
                fValid = false;
                continue;
                }

            if (exprArgNew != exprArgOld)
                {
                listArgs.set(iArg, exprArgNew);
                }

            fConst &= exprArgNew.isConstant();
            }

        if (fValid)
            {
            m_fConst = fConst;
            m_anno   = null; // force a re-generation of the annotation

            Annotation   anno     = ensureAnnotation(pool);
            TypeConstant typeInto = anno.getAnnotationType().getExplicitClassInto();
            if (typeRequired != null)
                {
                typeInto = typeInto.resolveGenerics(pool, typeAnno);
                }
            typeAnno = pool.ensureAnnotatedTypeConstant(typeInto, anno);

            return finishValidation(ctx, typeRequired, typeAnno, TypeFit.Fit, null, errs);
            }

        return null;
        }


    // ----- debugging assistance ------------------------------------------------------------------

    @Override
    public String toString()
        {
        if (m_anno != null)
            {
            return m_anno.getValueString();
            }

        StringBuilder sb = new StringBuilder();

        sb.append('@')
          .append(type);

        if (args != null)
            {
            sb.append('(');

            boolean first = true;
            for (Expression expr : args)
                {
                if (first)
                    {
                    first = false;
                    }
                else
                    {
                    sb.append(", ");
                    }
                sb.append(expr);
                }

            sb.append(')');
            }

        return sb.toString();
        }

    @Override
    public String getDumpDesc()
        {
        return toString();
        }


    // ----- inner class: ValidatingContext --------------------------------------------------------

    /**
     * A context implementation that allows us to validate the constant parameters of an annotation
     * without having to be inside a StatementBlock of a method that is being compiled.
     */
    protected class ValidatingContext
            extends Context
        {
        public ValidatingContext(ClassStructure clzContainer)
            {
            super(null, false);

            f_clzContainer = clzContainer;
            }

        @Override
        public ClassStructure getThisClass()
            {
            return f_clzContainer;
            }

        @Override
        public MethodStructure getMethod()
            {
            return null;
            }

        @Override
        public Source getSource()
            {
            return AnnotationExpression.this.getSource();
            }

        @Override
        public ConstantPool pool()
            {
            return AnnotationExpression.this.pool();
            }

        @Override
        public Context enterFork(boolean fWhenTrue)
            {
            throw new IllegalStateException();
            }

        @Override
        public Context enter()
            {
            return this;
            }

        @Override
        public void registerVar(Token tokName, Register reg, ErrorListener errs)
            {
            }

        @Override
        public void unregisterVar(Token tokName)
            {
            }

        @Override
        public boolean isVarDeclaredInThisScope(String sName)
            {
            return false;
            }

        @Override
        public boolean isVarWritable(String sName)
            {
            return false;
            }

        @Override
        protected Argument resolveRegularName(Context ctxFrom, String sName, Token name, ErrorListener errs)
            {
            return new NameResolver(AnnotationExpression.this, sName).forceResolve(errs);
            }

        @Override
        protected Argument resolveReservedName(String sName, Token name, ErrorListener errs)
            {
            return sName.equals("this:module")
                    ? AnnotationExpression.this.getComponent().getIdentityConstant().getModuleConstant()
                    : null;
            }

        @Override
        public Context exit()
            {
            return this;
            }

        @Override
        public Map<String, Assignment> prepareJump(Context ctxDest)
            {
            return Collections.emptyMap();
            }

        private final ClassStructure f_clzContainer;
        }


    // ----- fields --------------------------------------------------------------------------------

    protected NamedTypeExpression type;
    protected List<Expression>    args;
    protected long                lStartPos;
    protected long                lEndPos;

    // these two fields allow us to pretend to be an Annotation by generating a type on the fly, if
    // necessary
    private transient AstNode    m_node;
    private transient Annotation m_anno;
    private transient boolean    m_fConst;

    private static final Field[] CHILD_FIELDS = fieldsForNames(AnnotationExpression.class, "type", "args");
    }
