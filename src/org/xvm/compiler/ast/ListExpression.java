package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import java.util.List;
import org.xvm.asm.Constant;
import org.xvm.asm.constants.TypeConstant;


/**
 * A list expression is an expression containing some number (0 or more) expressions of some common
 * type.
 * <p/>
 * <pre>
 * ListLiteral
 *     "{" ExpressionList-opt "}"
 *     "List:{" ExpressionList-opt "}"
 * </pre>
 */
public class ListExpression
        extends Expression
    {
    // ----- constructors --------------------------------------------------------------------------

    public ListExpression(TypeExpression type, List<Expression> exprs, long lStartPos, long lEndPos)
        {
        this.type      = type;
        this.exprs     = exprs;
        this.lStartPos = lStartPos;
        this.lEndPos   = lEndPos;
        }


    // ----- accessors -----------------------------------------------------------------------------

    public List<Expression> getExpressions()
        {
        return exprs;
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


    // ----- compilation ---------------------------------------------------------------------------


    @Override
    public TypeConstant getImplicitType()
        {
        // TODO lots of error checking required

        if (type != null)
            {
            return type.getImplicitType();
            }

        TypeConstant typeArray = pool().typeArray();

        ElementsAllSameType: if (!exprs.isEmpty())
            {
            TypeConstant typeElement = exprs.get(0).getImplicitType();
            for (Expression expr : exprs)
                {
                if (!typeElement.equals(expr.getImplicitType()))
                    {
                    break ElementsAllSameType;
                    }
                }
            typeArray = pool().ensureParameterizedTypeConstant(typeArray, typeElement);
            }

        return typeArray;
        }

    @Override
    public boolean isConstant()
        {
        for (Expression expr : exprs)
            {
            if (!expr.isConstant())
                {
                return false;
                }
            }

        return type == null || type.isConstant();
        }

    @Override
    public Constant toConstant()
        {
        int        cConsts = exprs.size();
        Constant[] aConsts = new Constant[cConsts];
        for (int i = 0; i < cConsts; ++i)
            {
            aConsts[i] = exprs.get(i).toConstant();
            }
        return pool().ensureArrayConstant(getImplicitType(), aConsts);
        }

    // ----- debugging assistance ------------------------------------------------------------------

    @Override
    public String toString()
        {
        StringBuilder sb = new StringBuilder();

        sb.append('{');

        boolean first = true;
        for (Expression expr : exprs)
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

        sb.append('}');

        return sb.toString();
        }

    @Override
    public String getDumpDesc()
        {
        return toString();
        }


    // ----- fields --------------------------------------------------------------------------------

    protected TypeExpression   type;
    protected List<Expression> exprs;
    protected long             lStartPos;
    protected long             lEndPos;

    private static final Field[] CHILD_FIELDS = fieldsForNames(ListExpression.class, "type", "exprs");
    }
