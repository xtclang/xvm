package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import java.util.List;
import org.xvm.asm.Constant;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;
import org.xvm.asm.constants.TypeConstant;
import org.xvm.compiler.ErrorListener;


/**
 * A tuple expression is an expression containing some number (0 or more) expressions.
 *
 * @author cp 2017.04.07
 */
public class TupleExpression
        extends Expression
    {
    // ----- constructors --------------------------------------------------------------------------

    public TupleExpression(TypeExpression type, List<Expression> exprs, long lStartPos, long lEndPos)
        {
        this.type      = type;
        this.exprs     = exprs;
        this.lStartPos = lStartPos;
        this.lEndPos   = lEndPos;
        }


    // ----- accessors -----------------------------------------------------------------------------

    /**
     * @return get the TypeExpression for the tuple, if any; otherwise return null
     */
    TypeExpression getTypeExpression()
        {
        return type;
        }

    /**
     * @return the expressions making up the tuple value
     */
    public List<Expression> getExpressions()
        {
        return exprs;
        }

    @Override
    public boolean isConstant()
        {
        List<Expression> exprs = getExpressions();
        if (exprs != null)
            {
            // the tuple is constant if its members are constants
            for (Expression expr : getExpressions())
                {
                if (!expr.isConstant())
                    {
                    return false;
                    }
                }
            }
        return true;
        }

    @Override
    public Constant toConstant()
        {
        assert isConstant();
        // TODO how to factor in type? (which may be null)

        List<Expression> exprs = getExpressions();
        if (exprs == null)
            {
            // TODO empty tuple?
            }
        else
            {
            TypeExpression
            for (Expression expr : exprs)
                {
                // TODO
                }
            }

        // TODO temporary
        return super.toConstant();
        }

    @Override
    public Op.Argument generateArgument(MethodStructure.Code code, TypeConstant constType,
            boolean fTupleOk, ErrorListener errs)
        {
        // TODO
        return super.generateArgument(code, constType, fTupleOk, errs);
        }

    @Override
    public List<Op.Argument> generateArguments(MethodStructure.Code code,
            List<TypeConstant> listTypes,
            boolean fTupleOk, ErrorListener errs)
        {
        // TODO
        return super.generateArguments(code, listTypes, fTupleOk, errs);
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


    // ----- debugging assistance ------------------------------------------------------------------

    @Override
    public String toString()
        {
        StringBuilder sb = new StringBuilder();

        sb.append('(');

        if (exprs != null)
            {
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
            }

        sb.append(')');

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

    private static final Field[] CHILD_FIELDS = fieldsForNames(TupleExpression.class, "type", "exprs");
    }
