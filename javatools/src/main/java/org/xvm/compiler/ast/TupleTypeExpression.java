package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import java.util.List;

import org.xvm.asm.ErrorListener;
import org.xvm.asm.constants.TypeConstant;


/**
 * A type expression TODO
 */
public class TupleTypeExpression
        extends TypeExpression
    {
    // ----- constructors --------------------------------------------------------------------------

    public TupleTypeExpression(List<TypeExpression> params, long lStartPos, long lEndPos)
        {
        this.paramTypes   = params;
        this.lStartPos    = lStartPos;
        this.lEndPos      = lEndPos;
        }


    // ----- accessors -----------------------------------------------------------------------------

    public List<TypeExpression> getParamTypes()
        {
        return paramTypes;
        }

    @Override
    public long getStartPosition()
        {
        return lStartPos;
        }

    @Override
    public long getEndPosition()
        {
        return  lEndPos;
        }

    @Override
    protected Field[] getChildFields()
        {
        return CHILD_FIELDS;
        }


    // ----- TypeConstant methods ------------------------------------------------------------------

    @Override
    protected TypeConstant instantiateTypeConstant(Context ctx)
        {
        return pool().ensureTupleType(FunctionTypeExpression.toTypeConstantArray(paramTypes));
        }

    @Override
    protected Expression validate(Context ctx, TypeConstant typeRequired, ErrorListener errs)
        {
        List<TypeExpression> listTypes = paramTypes;
        boolean              fValid    = true;
        for (int i = 0, c = listTypes.size(); i < c; i++)
            {
            TypeExpression exprOld = listTypes.get(i);
            TypeExpression exprNew = (TypeExpression) exprOld.validate(ctx, null, errs);
            if (exprNew == null)
                {
                fValid = false;
                }
            else if (exprNew != exprOld)
                {
                listTypes.set(i, exprNew);
                }
            }

        return fValid
                ? super.validate(ctx, typeRequired, errs)
                : null;
        }


    // ----- debugging assistance ------------------------------------------------------------------

    @Override
    public String toString()
        {
        StringBuilder sb = new StringBuilder();

        sb.append('<');

        boolean first = true;
        for (TypeExpression type : paramTypes)
            {
            if (first)
                {
                first = false;
                }
            else
                {
                sb.append(", ");
                }
            sb.append(type);
            }

        sb.append('>');

        return sb.toString();
        }

    @Override
    public String getDumpDesc()
        {
        return toString();
        }


    // ----- fields --------------------------------------------------------------------------------

    protected List<TypeExpression> paramTypes;
    protected long                 lStartPos;
    protected long                 lEndPos;

    private static final Field[] CHILD_FIELDS = fieldsForNames(TupleTypeExpression.class, "paramTypes");
    }
