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
        if (this instanceof TypeExpression)
            {
            return (TypeExpression) this;
            }
        else
            {
            return new BadTypeExpression(this);
            }
        }

    @Override
    public abstract String toString();
    }
