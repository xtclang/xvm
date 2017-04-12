package org.xvm.compiler.ast;

/**
 * Base class for all Ecstasy expressions.
 *
 * @author cp 2017.03.28
 */
public abstract class Expression
        extends AstNode
    {
    /**
     * @return this expression, converted to a type expression
     */
    public TypeExpression toTypeExpression()
        {
        return new BadTypeExpression(this);
        }

    /**
     * @return true iff the expression is capable of completing normally
     */
    public boolean canComplete()
        {
        return true;
        }
    }
