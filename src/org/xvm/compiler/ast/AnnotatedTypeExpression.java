package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import java.util.List;

import org.xvm.asm.Constant;

import org.xvm.asm.constants.TerminalTypeConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.compiler.ErrorListener;


/**
 * An annotated type expression is a type expression preceded with an annotation.
 *
 * @author cp 2017.03.31
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

        return getConstantPool().ensureAnnotatedTypeConstant(
                constAnnotationType.getDefiningConstant(), aconstParam, type.ensureTypeConstant());
        }


    // ----- compile phases ------------------------------------------------------------------------

    @Override
    public void resolveNames(List<AstNode> listRevisit, ErrorListener errs)
        {
        if (getStage().ordinal() < org.xvm.compiler.Compiler.Stage.Resolved.ordinal())
            {
            // resolve the annotation and sub-type
            annotation.resolveNames(listRevisit, errs);
            type.resolveNames(listRevisit, errs);

            // TODO verify that the annotation type is a class type
            // TODO verify that each of the params is a constant

            // store off a type constant for this type expression
            ensureTypeConstant();

            super.resolveNames(listRevisit, errs);
            }
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

    private static final Field[] CHILD_FIELDS = fieldsForNames(AnnotatedTypeExpression.class,
            "annotation", "type");
    }
