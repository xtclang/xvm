package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import java.util.List;

import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;

import org.xvm.asm.constants.ClassConstant;

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


    // ----- compile phases ------------------------------------------------------------------------

    @Override
    public void resolveNames(List<AstNode> listRevisit, ErrorListener errs)
        {
        if (getStage().ordinal() < org.xvm.compiler.Compiler.Stage.Resolved.ordinal())
            {
            // resolve the annotation and sub-type
            annotation.resolveNames(listRevisit, errs);
            type.resolveNames(listRevisit, errs);

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
                        // TODO log error
                        throw new IllegalStateException("not a constant: " + exprArg);
                        }
                    }
                }

            // store off the annotated type
            ConstantPool pool = getConstantPool();
            ClassConstant constAnnotation = (ClassConstant) annotation.getType().ensureTypeConstant(
                    errs).getDefiningConstant();
            setTypeConstant(pool.ensureAnnotatedTypeConstant(constAnnotation, aconstParam,
                    type.ensureTypeConstant()));

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
