package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import java.util.List;

import org.xvm.asm.ConstantPool;

import org.xvm.asm.constants.ClassTypeConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.compiler.Compiler;
import org.xvm.compiler.Constants;
import org.xvm.compiler.ErrorListener;

import org.xvm.util.Severity;


/**
 * A nullable type expression is a type expression followed by a question mark.
 *
 * @author cp 2017.03.31
 */
public class NullableTypeExpression
        extends TypeExpression
    {
    // ----- constructors --------------------------------------------------------------------------

    public NullableTypeExpression(TypeExpression type, long lEndPos)
        {
        this.type    = type;
        this.lEndPos = lEndPos;
        }


    // ----- accessors -----------------------------------------------------------------------------

    @Override
    public ClassTypeConstant asClassTypeConstant(ErrorListener errs)
        {
        log(errs, Severity.ERROR, Compiler.NOT_CLASS_TYPE);
        return super.asClassTypeConstant(errs);
        }

    @Override
    protected boolean canResolveSimpleName()
        {
        return super.canResolveSimpleName() || type.canResolveSimpleName();
        }

    @Override
    public long getStartPosition()
        {
        return type.getStartPosition();
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


    // ----- compile phases ------------------------------------------------------------------------

    @Override
    public void resolveNames(List<AstNode> listRevisit, ErrorListener errs)
        {
        if (getStage().ordinal() < Compiler.Stage.Resolved.ordinal())
            {
            // resolve the sub-type
            type.resolveNames(listRevisit, errs);
            TypeConstant constSub = type.ensureTypeConstant();

            // obtain the Nullable type
            ConstantPool pool = getConstantPool();
            TypeConstant constNullable = pool.ensureClassTypeConstant(
                    pool.ensureEcstasyClassConstant(Constants.X_CLASS_NULLABLE),
                    Constants.Access.PUBLIC);

            // store off the Nullable form of the sub-type
            setTypeConstant(pool.ensureIntersectionTypeConstant(constNullable, constSub));

            super.resolveNames(listRevisit, errs);
            }
        }


    // ----- debugging assistance ------------------------------------------------------------------

    @Override
    public String toString()
        {
        StringBuilder sb = new StringBuilder();

        sb.append(type)
          .append("?");

        return sb.toString();
        }

    @Override
    public String getDumpDesc()
        {
        return toString();
        }


    // ----- fields --------------------------------------------------------------------------------

    protected TypeExpression type;
    protected long           lEndPos;

    private static final Field[] CHILD_FIELDS = fieldsForNames(NullableTypeExpression.class, "type");
    }
