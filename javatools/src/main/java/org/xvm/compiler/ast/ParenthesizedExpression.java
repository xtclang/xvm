package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import org.xvm.asm.Constant;

import org.xvm.asm.ast.LanguageAST.ExprAST;


/**
 * Used for parenthesized expressions.
 */
public class ParenthesizedExpression
        extends DelegatingExpression
    {
    // ----- constructors --------------------------------------------------------------------------

    public ParenthesizedExpression(Expression expr, long lStartPos, long lEndPos)
        {
        super(expr);

        m_lStartPos = lStartPos;
        m_lEndPos   = lEndPos;
        }


    // ----- accessors -----------------------------------------------------------------------------

    @Override
    public long getStartPosition()
        {
        return m_lStartPos;
        }

    @Override
    public long getEndPosition()
        {
        return m_lEndPos;
        }

    @Override
    protected Field[] getChildFields()
        {
        return CHILD_FIELDS;
        }

    @Override
    public TypeExpression toTypeExpression()
        {
        return expr.toTypeExpression();
        }

    @Override
    public boolean isStandalone()
        {
        return expr.isStandalone();
        }

    @Override
    public ExprAST<Constant> getExprAST()
        {
        return expr.getExprAST();
        }


    // ----- debugging assistance ------------------------------------------------------------------

    @Override
    public String toString()
        {
        return "(" + expr + ")";
        }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * The start and end positions.
     */
    private final long m_lStartPos;
    private final long m_lEndPos;

    private static final Field[] CHILD_FIELDS = fieldsForNames(ParenthesizedExpression.class, "expr");
    }