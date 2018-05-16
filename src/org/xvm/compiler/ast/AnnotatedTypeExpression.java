package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import java.util.List;

import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.ErrorListener;

import org.xvm.asm.constants.TerminalTypeConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.compiler.Compiler.Stage;

import org.xvm.compiler.ast.Statement.Context;


/**
 * An annotated type expression is a type expression preceded with an annotation.
 */
public class AnnotatedTypeExpression
        extends TypeExpression
    {
    // ----- constructors --------------------------------------------------------------------------

    public AnnotatedTypeExpression(Annotation annotation, TypeExpression type)
        {
        this.annotation = annotation;
        this.type       = type;
        }


    // ----- accessors -----------------------------------------------------------------------------

    /**
     * @return the annotation
     */
    Annotation getAnnotation()
        {
        return annotation;
        }

    /**
     * @return true iff this AnnotatedTypeExpression has been instructed to disassociate its
     *         annotation from the underlying type, which will occur if the annotation needs to be
     *         associated with property, method, or variable, for example
     */
    public boolean isDisassociated()
        {
        return m_fDisassociateAnnotation;
        }

    /**
     * This method allows a VariableDeclarationStatement (for example) to steal the annotation from
     * the type expression, if the annotation applies to the variable and not the type. One example
     * would be "@Inject Int i;", in which case the "Int" is not annotated (it is the variable "i"
     * that is annotated).
     *
     * @return the annotation
     */
    public Annotation disassociateAnnotation()
        {
        m_fDisassociateAnnotation = true;
        resetTypeConstant();
        return annotation;
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
    public TypeExpression unwrapIntroductotryType()
        {
        return type;
        }


    // ----- TypeConstant methods ------------------------------------------------------------------

    @Override
    protected TypeConstant instantiateTypeConstant()
        {
        // this is a bit complicated:
        // 1) we need the class of the annotation, which is resolved during validateExpressions()
        // 2) we need a constant for each parameter, but those are only guaranteed to be correct
        //    after validateExpressions()
        // 3) a "dis-associated" annotation is one that does not apply to the underlying type, so
        //    the underlying type is unchanged by this AnnotatedTypeExpression
        ConstantPool pool           = pool();
        TypeConstant typeUnderlying = type.ensureTypeConstant();
        return isDisassociated()
                ? typeUnderlying    // our annotation is not added to the underlying type constant
                : pool.ensureAnnotatedTypeConstant(annotation.ensureAnnotation(pool), typeUnderlying);
        }


    // ----- compile phases ------------------------------------------------------------------------

    @Override
    public AstNode resolveNames(List<AstNode> listRevisit, ErrorListener errs)
        {
        if (!alreadyReached(Stage.Resolved))
            {
            setStage(Stage.Resolving);

            // resolve the annotation and sub-type
            AstNode annotationNew = annotation.resolveNames(listRevisit, errs);
            AstNode typeNew       = type.resolveNames(listRevisit, errs);

            assert annotationNew != null && typeNew != null;
            annotation = (Annotation) annotationNew;
            type       = (TypeExpression) typeNew;

            if (!annotationNew.alreadyReached(Stage.Resolved) || !typeNew.alreadyReached(Stage.Resolved))
                {
                listRevisit.add(this);
                return this;
                }

            // store off a type constant for this type expression
            // note: each of the parameters of the Annotation will be verified to be a compile-time
            //       constant, but that cannot be attempted until after the call to validate() /
            //       validateMulti(); @see Annotation#validateExpressions
            ensureTypeConstant();
            }

        return super.resolveNames(listRevisit, errs);
        }


    // ----- Expression methods --------------------------------------------------------------------

    @Override
    protected Expression validate(Context ctx, TypeConstant typeRequired, TuplePref pref, ErrorListener errs)
        {
        // we need to verify that the type specified in the annotation will "apply to" our
        // right-hand-side type
// TODO        org.xvm.asm.Annotation annoNew = annotation.validate(ctx, typeRequired, pref, errs);
//        ConstantPool pool = pool();
//        TypeConstant typeReferent  = getTypeConstant();
//        TypeConstant typeReference = pool.ensureParameterizedTypeConstant(pool.typeType(), typeReferent);
//
//        // TODO pref etc. - this kind of nonsense should not have to show up on every single qExpression implementation!
//        TypeFit fit = typeRequired == null || typeRequired.isA(typeRequired)
//                ? TypeFit.Fit
//                : TypeFit.NoFit;
        return super.validate(ctx, typeRequired, pref, errs);
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

    protected Annotation     annotation;
    protected TypeExpression type;

    private boolean m_fDisassociateAnnotation;

    private static final Field[] CHILD_FIELDS = fieldsForNames(AnnotatedTypeExpression.class,
            "annotation", "type");
    }
