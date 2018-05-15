package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import java.util.List;

import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.ErrorListener;

import org.xvm.asm.constants.TerminalTypeConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.compiler.Compiler;
import org.xvm.compiler.Compiler.Stage;

import org.xvm.compiler.Constants;
import org.xvm.compiler.ast.Statement.Context;
import org.xvm.util.Severity;


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
     * @return true iff this AnnotatedTypeExpression has been instructed to disassociate its
     *         annotation from the underlying type, which will occur if the annotation needs to be
     *         associated with property, method, or variable, for example
     */
    public boolean isAnnotationDisassociated()
        {
        return m_fDisassociateAnnotation;
        }

    public void setAnnotationDisassociated(boolean fDisassociate)
        {
        m_fDisassociateAnnotation = fDisassociate;

        // reset the type constant if one was already created
        if (getTypeConstant() != null)
            {
            setTypeConstant(instantiateTypeConstant());
            AstNode parent = getParent();
            while (parent instanceof TypeExpression)
                {
                TypeExpression exprParent = (TypeExpression) parent;
                if (exprParent.getTypeConstant() != null)
                    {
                    exprParent.setTypeConstant(exprParent.instantiateTypeConstant());
                    }

                parent = parent.getParent();
                }
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


    // ----- TypeConstant methods ------------------------------------------------------------------

    @Override
    protected TypeConstant instantiateTypeConstant()
        {
        TypeConstant typeUnderlying = type.ensureTypeConstant();
        if (isAnnotationDisassociated())
            {
            // our annotation is not added to the underlying type constant
            return typeUnderlying;
            }

        // TODO TODO TODO

        // this is a bit complicated:
        // 1) we need the class of the annotation
        // 2) we need a constant for each parameter (how do we know we're ready to ask for those at
        //    this point? what do we do if the parameters are NOT constant? how do we log an error?)
        // 3) we need the underlying type -- but this is easy: type.ensureTypeConstant()
        //
        // the question is, how do we put the resulting TypeConstant together in a way that if it's
        // not all resolved at this point, that it will get resolved during the resolveNames pass?

        TypeConstant constAnnotationType = annotation.getType().ensureTypeConstant();
        if (!(constAnnotationType instanceof TerminalTypeConstant))
            {
            // TODO should this be a throw? or an error logged? or a temporary place-holder constant
            // returned just to avoid blowing up?
            throw new IllegalStateException("illegal annotation class: " + constAnnotationType);
            }

        Constant[]       aconstParam = null;
        List<Expression> args = annotation.getArguments();
        if (args != null)
            {
            int cArgs = args.size();
            aconstParam = new Constant[cArgs];
            for (int i = 0; i < cArgs; ++i)
                {
                Expression exprArg = args.get(i);
                if (exprArg.isConstant())
                    {
                    aconstParam[i] = exprArg.toConstant();
                    }
                else
                    {
                    // TODO should this be a throw? or an error logged?
                    throw new IllegalStateException("annotation param not constant: " + exprArg);
                    }
                }
            }

        return pool().ensureAnnotatedTypeConstant(
                constAnnotationType.getDefiningConstant(), aconstParam, typeUnderlying);
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

            // note: each of the parameters of the Annotation will be verified to be a compile-
            // time constant, but that cannot be attempted until after the call to validate() /
            // validateMulti(); @see Annotation#validateExpressions

            // store off a type constant for this type expression
            // TODO setTC(initTC)
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
