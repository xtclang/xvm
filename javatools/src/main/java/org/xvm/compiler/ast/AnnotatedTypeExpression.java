package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import java.util.ArrayList;
import java.util.List;

import org.xvm.asm.Annotation;
import org.xvm.asm.ClassStructure;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.ErrorListener;

import org.xvm.asm.constants.AnnotatedTypeConstant;
import org.xvm.asm.constants.IdentityConstant;
import org.xvm.asm.constants.TypeConstant;

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
        return m_fDisassociate;
        }

    /**
     * @return true iff this AnnotatedTypeExpression is itself disassociated or contains any
     *         disassociated AnnotatedTypeExpression type
     */
    public boolean isIntoRef()
        {
        return m_fDisassociate
            || ((type instanceof AnnotatedTypeExpression)
                && ((AnnotatedTypeExpression) type).isIntoRef());
        }

    /**
     * @return true iff this AnnotatedTypeExpression is itself refers to a Var (read/write) or
     *         contains any disassociated AnnotatedTypeExpression type that refers to a Var
     */
    public boolean isVar()
        {
        return m_fVar
            || ((type instanceof AnnotatedTypeExpression)
                && ((AnnotatedTypeExpression) type).isVar());
        }

    /**
     * @return true if this type expression represents an injected type
     */
    public boolean isInjected()
        {
        return m_fInjected
            || ((type instanceof AnnotatedTypeExpression)
                && ((AnnotatedTypeExpression) type).isInjected());
        }

    /**
     * @return true if this type expression represents a "final" type (@Injected or @Final)
     */
    public boolean isFinal()
        {
        return m_fFinal
            || ((type instanceof AnnotatedTypeExpression)
                && ((AnnotatedTypeExpression) type).isFinal());
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
        if (m_fDisassociate)
            {
            list.add(annotation);
            }

        // REVIEW in what order?
        if (type instanceof AnnotatedTypeExpression)
            {
            ((AnnotatedTypeExpression) type).collectRefAnnotations(list);
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
    protected TypeConstant instantiateTypeConstant(Context ctx)
        {
        // this is a bit complicated:
        // 1) we need the class of the annotation, which is resolved during validateContent()
        // 2) we need a constant for each parameter, but those are only guaranteed to be correct
        //    after validateContent()
        // 3) a "dis-associated" annotation is one that does not apply to the underlying type, so
        //    the underlying type is unchanged by this AnnotatedTypeExpression
        ConstantPool pool           = pool();
        TypeConstant typeUnderlying = type.ensureTypeConstant(ctx);

        Annotation anno      = annotation.ensureAnnotation(pool());
        Constant   constAnno = anno.getAnnotationClass();

        // until it's resolved let's assume it's disassociated
        boolean fIntoVar = true;
        if (!constAnno.containsUnresolved())
            {
            IdentityConstant idAnno   = (IdentityConstant) constAnno;
            TypeConstant     typeInto = ((ClassStructure) idAnno.getComponent()).getTypeInto();

            fIntoVar = !typeInto.containsUnresolved() && typeInto.isIntoVariableType();
            }

        m_fDisassociate = fIntoVar;

        return fIntoVar
                ? typeUnderlying    // our annotation is not added to the underlying type constant
                : pool.ensureAnnotatedTypeConstant(typeUnderlying, anno);
        }

    @Override
    protected void collectAnonInnerClassInfo(AnonInnerClass info)
        {
        info.addAnnotation(getAnnotation());
        type.collectAnonInnerClassInfo(info);
        }


    // ----- Expression methods --------------------------------------------------------------------

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

        TypeConstant typeReferent = ensureTypeConstant(ctx);
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
        if (m_fDisassociate)
            {
            Constant clzAnno = anno.getAnnotationClass();
            if (clzAnno.equals(pool.clzInject()))
                {
                // @Inject implies assignment & final
                m_fFinal = m_fInjected = true;
                }
            else if (clzAnno.equals(pool.clzFinal()))
                {
                m_fFinal = true;
                }

            if (exprTypeNew instanceof AnnotatedTypeExpression)
                {
                AnnotatedTypeExpression exprTypeNext = (AnnotatedTypeExpression) exprTypeNew;
                if (m_fInjected || exprTypeNext.isInjected())
                    {
                    log(errs, Severity.ERROR, Compiler.ANNOTATED_INJECTION);
                    return null;
                    }
                if (m_fFinal && exprTypeNext.isFinal())
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
            typeReq = ((AnnotatedTypeConstant) typeReferent).getAnnotationType();
            }
        else
            {
            log(errs, Severity.ERROR, Constants.VE_ANNOTATION_INCOMPATIBLE,
                    type.ensureTypeConstant(ctx).getValueString(),
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
        typeAnno = ensureTypeConstant(ctx);

        TypeConstant typeType   = typeAnno.getType();
        Constant     constValue = typeType;
        if (typeRequired != null)
            {
            if (typeRequired.isA(pool.typeClass()))
                {
                // class of type conversion
                IdentityConstant clzAnno = pool.ensureClassConstant(typeAnno);
                typeType   = clzAnno.getValueType(null);
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


    // ----- debugging assistance ------------------------------------------------------------------

    @Override
    public String toString()
        {
        StringBuilder sb = new StringBuilder();

        sb.append(annotation)
          .append(' ')
          .append(type);

        return sb.toString();
        }

    @Override
    public String getDumpDesc()
        {
        return toString();
        }


    // ----- fields --------------------------------------------------------------------------------

    protected AnnotationExpression annotation;
    protected TypeExpression       type;

    private transient boolean m_fDisassociate;
    private transient boolean m_fVar;
    private transient boolean m_fInjected;
    private transient boolean m_fFinal;

    private static final Field[] CHILD_FIELDS = fieldsForNames(AnnotatedTypeExpression.class,
            "annotation", "type");
    }
