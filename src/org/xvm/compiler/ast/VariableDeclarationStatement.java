package org.xvm.compiler.ast;


import org.xvm.compiler.Token;


/**
 * A variable declaration statement specifies a type and a simply name for a variable, with an
 * optional initial value.
 *
 * @author cp 2017.04.04
 */
public class VariableDeclarationStatement
        extends Statement
    {
    public VariableDeclarationStatement(TypeExpression type, Token name, Expression value)
        {
        this.name  = name;
        this.type  = type;
        this.value = value;
        }

    @Override
    public String toString()
        {
        StringBuilder sb = new StringBuilder();
        sb.append(type)
          .append(' ')
          .append(name);
        if (value != null)
            {
            sb.append(" = ")
              .append(value);
            }
        sb.append(';');
        return sb.toString();
        }

    public final TypeExpression type;
    public final Token name;
    public final Expression value;
    }
