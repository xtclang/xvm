package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import java.util.List;

import org.xvm.asm.ConstantPool;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.compiler.Constants;
import org.xvm.compiler.ErrorListener;


/**
 * An array type expression is a type expression followed by an array indicator. Because an array
 * type can be used to (e.g.) "new" an array, it also has to support actual index extents, in
 * addition to just supporting the number of dimensions.
 *
 * @author cp 2017.03.31
 */
public class ArrayTypeExpression
        extends TypeExpression
    {
    // ----- constructors --------------------------------------------------------------------------

    public ArrayTypeExpression(TypeExpression type, int dims, long lEndPos)
        {
        this.type    = type;
        this.dims    = dims;
        this.indexes = null;
        this.lEndPos = lEndPos;
        }

    public ArrayTypeExpression(TypeExpression type, List<Expression> indexes, long lEndPos)
        {
        this.type    = type;
        this.dims    = indexes.size();
        this.indexes = null;
        this.lEndPos = lEndPos;
        }


    // ----- accessors -----------------------------------------------------------------------------

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
        if (getStage().ordinal() < org.xvm.compiler.Compiler.Stage.Resolved.ordinal())
            {
            // resolve the sub-type
            type.resolveNames(listRevisit, errs);
            TypeConstant constSub = type.ensureTypeConstant();

            // obtain the Array type
            ConstantPool pool       = getConstantPool();
            TypeConstant constArray = pool.ensureClassTypeConstant(
                    pool.ensureEcstasyClassConstant(Constants.X_CLASS_ARRAY),
                    Constants.Access.PUBLIC, constSub);

            // store off the type that is an array of the sub-type
            setTypeConstant(constArray);

            super.resolveNames(listRevisit, errs);
            }
        }


    // ----- debugging assistance ------------------------------------------------------------------

    @Override
    public String toString()
        {
        StringBuilder sb = new StringBuilder();

        sb.append(type)
          .append('[');

        for (int i = 0; i < dims; ++i)
            {
            if (i > 0)
                {
                sb.append(',');
                }

            if (indexes == null)
                {
                sb.append('?');
                }
            else
                {
                sb.append(indexes.get(i));
                }
            }

          sb.append(']');

        return sb.toString();
        }

    @Override
    public String getDumpDesc()
        {
        return toString();
        }


    // ----- fields --------------------------------------------------------------------------------

    protected TypeExpression   type;
    protected int              dims;
    protected List<Expression> indexes;
    protected long             lEndPos;

    private static final Field[] CHILD_FIELDS = fieldsForNames(ArrayTypeExpression.class, "type", "indexes");
    }
