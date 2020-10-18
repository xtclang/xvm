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
import org.xvm.asm.PropertyStructure;
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

    public List<Expression> getArguments()
        {
        return args;
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
        List<Expression> args        = getArguments();
        Constant[]       aconstArgs  = Constant.NO_CONSTS;
        int              cArgs       = args == null ? 0 : args.size();
        if (cArgs > 0)
            {
            aconstArgs = new Constant[cArgs];
            for (int iArg = 0; iArg < cArgs; ++iArg)
                {
                Expression exprArg = args.get(iArg);
                if (exprArg.alreadyReached(Stage.Validated))
                    {
                    aconstArgs[iArg] = exprArg.isConstant()
                            ? exprArg.toConstant()
                            : new ExpressionConstant(pool, exprArg);
                    }
                else if (exprArg instanceof LiteralExpression)
                    {
                    LiteralExpression exprLit = (LiteralExpression) exprArg;

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
                else if (exprArg instanceof NameExpression)
                    {
                    aconstArgs[iArg] = new UnresolvedNameConstant(pool,
                            ((NameExpression) exprArg).collectNames(1), false);
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
            log(errs, Severity.ERROR, Constants.VE_ANNOTATION_NOT_MIXIN, idAnno.getName());
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

        if (typeAnno.isIntoVariableType())
            {
            AstNode parent = this;
            do
                {
                parent = parent.getParent();
                }
            while (parent != null && !parent.isComponentNode());

            if (parent instanceof PropertyDeclarationStatement)
                {
                // for Property annotation, calculate the real annotation type based on the property
                // type; note that this logic doesn't calculate the actual annotated property ref
                // type, which could have multiple annotations that we are disregarding here
                PropertyDeclarationStatement stmtProp = (PropertyDeclarationStatement) parent;
                PropertyStructure            prop     = (PropertyStructure) stmtProp.getComponent();
                TypeConstant                 typeRef  = prop.getIdentityConstant().getRefType(null);

                typeAnno = pool().ensureAnnotatedTypeConstant(typeRef, anno).getAnnotationType();
                infoAnno = typeAnno.ensureTypeInfo(errs);
                }
            }

        List<Expression>  args  = getArguments();
        int               cArgs = args == null ? 0 : args.size();
        ValidatingContext ctx   = new ValidatingContext(getComponent().getContainingClass());

        // find a matching constructor on the annotation class
        MethodConstant idConstruct = findMethod(ctx, typeAnno, infoAnno,
                    "construct", args, MethodKind.Constructor, true, false, TypeConstant.NO_TYPES, errs);

        if (idConstruct == null)
            {
            log(errs, Severity.ERROR, Compiler.MISSING_CONSTRUCTOR, idAnno.getName());
            return;
            }

        if (cArgs > 0)
            {
            MethodStructure method = infoAnno.getMethodById(idConstruct).
                                        getTopmostMethodStructure(infoAnno);

            // validate the argument expressions and fix up all of the constants used as
            // arguments to construct the annotation
            TypeConstant[] atypeParams = idConstruct.getRawParams();
            int            cAll        = method.getParamCount();
            int            cDefault    = method.getDefaultParamCount();
            Constant[]     aconstArgs  = new Constant[cAll];

            assert cArgs <= cAll && cArgs >= cAll - cDefault;

            for (int iArg = 0; iArg < cArgs; ++iArg)
                {
                Expression exprOld = args.get(iArg);
                int        iParam  = exprOld instanceof LabeledExpression
                        ? method.getParam(((LabeledExpression) exprOld).getName()).getIndex()
                        : iArg;

                Expression exprNew = exprOld.validate(ctx, atypeParams[iParam], errs);

                if (exprNew == null || !exprNew.isRuntimeConstant())
                    {
                    exprOld.log(errs, Severity.ERROR, Compiler.CONSTANT_REQUIRED);
                    }
                else
                    {
                    if (exprNew != exprOld)
                        {
                        args.set(iArg, exprNew);
                        }

                    // update the Annotation directly
                    // Note: this is quite unusual, in that normally things like an annotation are
                    //       treated as a constant once instantiated, but in this case, it was
                    //       impossible to validate the arguments of the annotation when it was
                    //       constructed, because we were too early in the compile cycle to resolve
                    //       any constant expressions that refer to anything _by name_
                    aconstArgs[iParam] = exprNew.toConstant();
                    }
                }

            if (cArgs < cAll)
                {
                // fill the default values
                for (int iParam = 0; iParam < cAll; ++iParam)
                    {
                    if (aconstArgs[iParam] == null)
                        {
                        Constant constDefault = method.getParam(iParam).getDefaultValue();
                        assert constDefault != null;
                        aconstArgs[iParam] = constDefault;
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
        List<Expression> listArgs = getArguments();
        int              cArgs    = listArgs == null ? 0 : listArgs.size();

        NamedTypeExpression exprTypeOld = toTypeExpression();
        NamedTypeExpression exprTypeNew = (NamedTypeExpression) exprTypeOld.validate(ctx, null, errs);

        if (exprTypeNew == null)
            {
            return null;
            }
        type = exprTypeNew;

        TypeConstant  typeAnno = exprTypeNew.ensureTypeConstant(ctx);
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

        if (!typeAnno.isA(typeRequired))
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
            log(errs, Severity.ERROR, Compiler.MISSING_CONSTRUCTOR, idAnno.getName());
            return null;
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
                typeInto = typeInto.resolveGenerics(pool, typeRequired);
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
            throw new IllegalStateException();
            }

        @Override
        public void registerVar(Token tokName, Register reg, ErrorListener errs)
            {
            throw new IllegalStateException();
            }

        @Override
        public void unregisterVar(Token tokName)
            {
            throw new IllegalStateException();
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
            throw new IllegalStateException();
            }

        @Override
        public Map<String, Assignment> prepareJump(Context ctxDest)
            {
            throw new IllegalStateException();
            }

        @Override
        protected Assignment promote(String sName, Assignment asnInner, Assignment asnOuter)
            {
            throw new IllegalStateException();
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
