package org.xvm.asm.constants;


import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;

import org.xvm.compiler.ast.Expression;


/**
 * Represent a synthetic compile-time only constant that represents an {@link Expression}.
 */
public class ExpressionConstant
        extends PseudoConstant
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Constructor.
     *
     * @param pool  the ConstantPool that will contain this Constant
     * @param expr  an {@link Expression} that this constant represents
     */
    public ExpressionConstant(ConstantPool pool, Expression expr)
        {
        super(pool);

        m_expr = expr;
        }


    // ----- type-specific functionality -----------------------------------------------------------

    /**
     * @return the underlying {@link Expression}
     */
    public Expression getExpression()
        {
        return m_expr;
        }


    // ----- Constant methods ----------------------------------------------------------------------

    @Override
    public Format getFormat()
        {
        return Format.UnresolvedName;
        }

    @Override
    public boolean containsUnresolved()
        {
        return true;
        }

    @Override
    protected int compareDetails(Constant that)
        {
        return that instanceof ExpressionConstant exprThat
            && this.m_expr.equals(exprThat.m_expr)
                ?  0
                : -1;
        }

    @Override
    public String getValueString()
        {
        return m_expr.toString();
        }


    // ----- XvmStructure methods ------------------------------------------------------------------

    @Override
    public String getDescription()
        {
        return "expression=" + m_expr;
        }


    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public int hashCode()
        {
        return m_expr.hashCode();
        }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * The underlying expression.
     */
    private final transient Expression m_expr;
    }
