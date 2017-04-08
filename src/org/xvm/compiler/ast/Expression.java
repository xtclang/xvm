package org.xvm.compiler.ast;

/**
 * Base class for all Ecstasy expressions.
 *
 * @author cp 2017.03.28
 */
public abstract class Expression
    {
    // TODO first & last tokens?

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
