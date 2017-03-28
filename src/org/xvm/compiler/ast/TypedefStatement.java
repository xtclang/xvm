package org.xvm.compiler.ast;


import org.xvm.compiler.Token;


/**
 * A typedef statement specifies a type to alias as a simple name.
 *
 * @author cp 2017.03.28
 */
public class TypedefStatement
        extends Statement
    {
    public TypedefStatement(Token alias, TypeExpression type)
        {
        this.alias = alias;
        this.type  = type;
        }

    @Override
    public String toString()
        {
        return "typedef " + type + " " + alias;
        }

    public final Token alias;
    public final TypeExpression type;
    }
