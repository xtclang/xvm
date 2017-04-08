package org.xvm.compiler.ast;


/**
 * A type expression that can't figure out how to be a type exception.
 *
 * @author cp 2017.04.07
 */
public class BadTypeExpression
        extends TypeExpression
    {
    public BadTypeExpression(Expression nonType)
        {
        this.nonType = nonType;
        }

    @Override
    public String toString()
        {
        return "/* NOT A TYPE!!! */ " + nonType;
        }

    public final Expression nonType;
    }
