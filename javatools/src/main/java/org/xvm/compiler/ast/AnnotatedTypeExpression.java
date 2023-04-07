package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import java.util.ArrayList;
import java.util.List;

import org.xvm.asm.Annotation;
import org.xvm.asm.ClassStructure;
import org.xvm.asm.Component;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.ErrorListener;

import org.xvm.asm.constants.AnnotatedTypeConstant;
import org.xvm.asm.constants.IdentityConstant;
import org.xvm.asm.constants.TypeConstant;
import org.xvm.asm.constants.UnresolvedNameConstant;
import org.xvm.asm.constants.UnresolvedTypeConstant;

import org.xvm.compiler.Compiler;
import org.xvm.compiler.Constants;

import org.xvm.util.Severity;


/**
 * An annotated type expression is a type expression preceded with an annotation.
 */
public class AnnotatedTypeExpression
        extends TypeExpression
    {
    // ----- constructors --------------------------------------------------------------------------

    public AnnotatedTypeExpression(AnnotationExpression annotation, TypeExpression type)
        {
        this.annotation = annotation;
        this.type       = type;
        }


    // ----- accessors -----------------------------------------------------------------------------

    /**
     * @return the annotation
     */
    AnnotationExpression getAnnotation()
        {
        return annotation;
        }

    /**
     * @return true iff this AnnotatedTypeExpression has been instructed to disassociate its
     *         annotation from the underlying type, which will occur if the annotation needs to be
     *         associated with property or variable, for example
     */
    public boolean isDisassociated()
        {
        return m_fDisassociateRef;
        }

    /**
     * @return true iff this AnnotatedTypeExpression is itself disassociated or contains any
     *         disassociated AnnotatedTypeExpression type
     */
    public boolean isIntoRef()
        {
        return m_fDisassociateRef
            || type instanceof AnnotatedTypeExpression exprType && exprType.isIntoRef();
        }

    /**
     * @return true iff this AnnotatedTypeExpression is itself refers to a Var (read/write) or
     *         contains any disassociated AnnotatedTypeExpression type that refers to a Var
     */
    public boolean isVar()
        {
        return m_fVar
            || type instanceof AnnotatedTypeExpression exprType && exprType.isVar();
        }

    /**
     * @return true if this annotated type expression represents an injected var
     */
    public boolean isInjected()
        {
        return m_fInjected
            || type instanceof AnnotatedTypeExpression exprType && exprType.isInjected();
        }

    /**
     * @return true if this annotated type expression contains the specified annotation
     */
    public boolean contains(Constant clzAnno)
        {
        Annotation anno = annotation.ensureAnnotation(pool());
        return anno.getAnnotationClass().equals(clzAnno)
            || type instanceof AnnotatedTypeExpression exprType && exprType.contains(clzAnno);
        }

    /**
     * @return a list of ref annotations (disassociated)
     */
    public List<AnnotationExpression> getRefAnnotations()
        {
        List<AnnotationExpression> list = new ArrayList<>();
        collectRefAnnotations(list);
        return list;
        }

    protected void collectRefAnnotations(List<AnnotationExpression> list)
        {
        if (m_fDisassociateRef)
            {
            list.add(annotation);
            }

        // REVIEW in what order?
        if (type instanceof AnnotatedTypeExpression exprType)
            {
            exprType.collectRefAnnotations(list);
            }
        }

    @Override
    protected boolean canResolveNames()
        {
        return super.canResolveNames() || type.canResolveNames();
        }

    @Override
    public long getStartPosition()
        {
        return annotation.getStartPosition();
        }

    @Override
    public long getEndPosition()
        {
        return type.getEndPosition();
        }

    @Override
    protected Field[] getChildFields()
        {
        return CHILD_FIELDS;
        }

    @Override
    public boolean isIntroductoryType()
        {
        return true;
        }

    @Override
    public TypeExpression unwrapIntroductoryType()
        {
        return type;
        }

    @Override
    public void replaceIntroducedType(TypeExpression type)
        {
        this.type = type;
        type.setParent(this);
        }


    // ----- TypeExpression methods ----------------------------------------------------------------

    @Override
    protected TypeConstant instantiateTypeConstant(Context ctx, ErrorListener errs)
        {
        return calculateType(ctx, errs);
        }

    @Override
    protected void collectAnonInnerClassInfo(AnonInnerClass info)
        {
        m_fAnonInner = true;
        info.addAnnotation(getAnnotation());
        type.collectAnonInnerClassInfo(info);
        }

    @Override
    protected void setTypeConstant(TypeConstant constType)
        {
        TypeConstant constBase = constType;

        if (!m_fDisassociateClass && !m_fDisassociateRef)
            {
            constBase = constType.getUnderlyingType();
            }
        type.setTypeConstant(constBase);

        super.setTypeConstant(constType);
        }


    // ----- Expression methods --------------------------------------------------------------------

    @Override
    public void resolveNames(StageMgr mgr, ErrorListener errs)
        {
        if (!mgr.processChildren())
            {
            mgr.requestRevisit();
            return;
            }

        calculateType(null, errs);

        if (m_typeUnresolved == null)
            {
            resetTypeConstant();
            }
        else
            {
            mgr.requestRevisit();
            }
        }

    @Override
    protected Expression validate(Context ctx, TypeConstant typeRequired, ErrorListener errs)
        {
        ConstantPool   pool        = pool();
        TypeExpression exprTypeNew = (TypeExpression) type.validate(ctx, pool.typeType(), errs);

        if (exprTypeNew == null)
            {
            return null;
            }

        type = exprTypeNew;

        TypeConstant typeReferent = ensureTypeConstant(ctx, errs);
        Annotation   anno         = annotation.ensureAnnotation(pool);
        TypeConstant typeAnno     = anno.getAnnotationType();
        TypeConstant typeReq;

        if (typeAnno.containsUnresolved())
            {
            annotation.log(errs, Severity.ERROR, Compiler.NAME_UNRESOLVABLE,
                    typeAnno.getValueString());
            return null;
            }

        // the annotation must mix in to the Var (if it's disassociated), or into the underlying
        // type otherwise
        if (m_fDisassociateRef)
            {
            Constant clzAnno = anno.getAnnotationClass();
            if (clzAnno.equals(pool.clzInject()))
                {
                m_fInjected = true;
                }

            if (exprTypeNew instanceof AnnotatedTypeExpression exprTypeNext)
                {
                if (m_fInjected || exprTypeNext.isInjected())
                    {
                    log(errs, Severity.ERROR, Compiler.ANNOTATED_INJECTION);
                    return null;
                    }
                if (exprTypeNext.contains(clzAnno))
                    {
                    log(errs, Severity.ERROR, Constants.VE_ANNOTATION_REDUNDANT,
                        anno.getAnnotationType().getValueString());
                    return null;
                    }
                }

            m_fVar  = typeAnno.getIntoVariableType().isA(pool.typeVar());
            typeReq = pool.ensureParameterizedTypeConstant(
                m_fVar ? pool.typeVar() : pool.typeRef(), typeReferent);
            }
        else if (typeReferent.isA(typeAnno.ensureTypeInfo(errs).getInto()))
            {
            typeReq = typeReferent instanceof AnnotatedTypeConstant exprAnno
                    ? exprAnno.getAnnotationType()
                    : typeAnno;
            }
        else if (m_fAnonInner && m_fDisassociateClass)
            {
            // a class annotation into an anonymous class (e.g. "new @Concurrent Object() {}")
            typeReq = null;
            }
        else
            {
            log(errs, Severity.ERROR, Constants.VE_ANNOTATION_INCOMPATIBLE,
                    type.ensureTypeConstant(ctx, errs).getValueString(),
                    anno.getAnnotationClass().getValueString(),
                    typeAnno.ensureTypeInfo(errs).getInto().getValueString());
            return null;
            }

        AnnotationExpression exprOld = annotation;
        AnnotationExpression exprNew = (AnnotationExpression) exprOld.validate(ctx, typeReq, errs);
        if (exprNew == null)
            {
            return null;
            }

        annotation = exprNew;

        resetTypeConstant();
        typeAnno = ensureTypeConstant(ctx, errs);

        TypeConstant typeType   = typeAnno.getType();
        Constant     constValue = typeType;
        if (typeRequired != null)
            {
            if (typeRequired.isA(pool.typeClass()))
                {
                // class of type conversion
                IdentityConstant clzAnno = pool.ensureClassConstant(typeAnno);
                typeType   = clzAnno.getValueType(pool, null);
                constValue = clzAnno;
                }
            else
                {
                TypeConstant typeInferred = inferTypeFromRequired(typeType, typeRequired);
                if (typeInferred != null)
                    {
                    constValue = typeType = typeInferred;
                    }
                }
            }

        return finishValidation(ctx, typeRequired, typeType, TypeFit.Fit, constValue, errs);
        }

    /**
     * Calculate the "pre-validation" annotated type.
     */
    protected TypeConstant calculateType(Context ctx, ErrorListener errs)
        {
        // this is a bit complicated:
        // 1) we need the class of the annotation, which is resolved during validateContent()
        // 2) we need a constant for each parameter, but those are only guaranteed to be correct
        //    after validateContent()
        // 3) a "dis-associated" annotation is one that does not apply to the underlying type, so
        //    the underlying type is unchanged by this AnnotatedTypeExpression

        ConstantPool pool           = pool();
        TypeConstant typeUnderlying = type.ensureTypeConstant(ctx, errs);
        Annotation   anno           = annotation.ensureAnnotation(pool());
        Constant     constAnno      = anno.getAnnotationClass();
        boolean      fResolved      = !constAnno.containsUnresolved();

        if (fResolved)
            {
            IdentityConstant idAnno   = (IdentityConstant) constAnno;
            ClassStructure   clzAnno  = (ClassStructure) idAnno.getComponent();
            if (clzAnno.getFormat() != Component.Format.MIXIN)
                {
                log(errs, Severity.ERROR, Constants.VE_ANNOTATION_NOT_MIXIN, clzAnno.getName());
                return idAnno.getType();
                }

            TypeConstant typeInto = clzAnno.getTypeInto();
            if (typeInto.containsUnresolved())
                {
                fResolved = false;
                }
            else
                {
                m_fDisassociateClass = typeInto.isIntoClassType();
                m_fDisassociateRef   = typeInto.isIntoVariableType()
                        || isMethodParameter() && typeInto.isIntoMethodParameterType();
                }
            }

        if (!fResolved)
            {
            return m_typeUnresolved == null
                    ? m_typeUnresolved = new UnresolvedTypeConstant(pool,
                        new UnresolvedNameConstant(pool, constAnno.getValueString()))
                    : m_typeUnresolved;
            }

        TypeConstant type;
        if (m_fDisassociateClass || m_fDisassociateRef)
            {
            // our annotation is not added to the underlying type constant
            type = typeUnderlying;
            }
        else
            {
            if (typeUnderlying.isA(anno.getAnnotationType()))
                {
                type = typeUnderlying;
                log(errs, Severity.ERROR, Constants.VE_ANNOTATION_REDUNDANT,
                        anno.getAnnotationClass().getValueString());
                }
            else
                {
                type = pool.ensureAnnotatedTypeConstant(typeUnderlying, anno);
                }
            }

        if (m_typeUnresolved != null)
            {
            m_typeUnresolved.resolve(type);
            m_typeUnresolved = null;
            }

        return type;
        }

    /**
     * @return true iff this expression is a child of {@link Parameter} node.
     */
    private boolean isMethodParameter()
        {
        AstNode parent = getParent();
        while (parent instanceof AnnotatedTypeExpression)
            {
            parent = parent.getParent();
            }
        return parent instanceof Parameter;
        }


    // ----- debugging assistance ------------------------------------------------------------------

    @Override
    public String toString()
        {
        return String.valueOf(annotation) + ' ' + type;
        }

    @Override
    public String getDumpDesc()
        {
        return toString();
        }


    // ----- fields --------------------------------------------------------------------------------

    protected AnnotationExpression annotation;
    protected TypeExpression       type;

    private transient boolean m_fDisassociateRef;
    private transient boolean m_fDisassociateClass;
    private transient boolean m_fAnonInner;
    private transient boolean m_fVar;
    private transient boolean m_fInjected;

    // unresolved constant that may have been created by this expression
    private transient UnresolvedTypeConstant m_typeUnresolved;

    private static final Field[] CHILD_FIELDS = fieldsForNames(AnnotatedTypeExpression.class,
            "annotation", "type");
    }