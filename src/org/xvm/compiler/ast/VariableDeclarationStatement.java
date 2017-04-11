package org.xvm.compiler.ast;


import org.xvm.compiler.Token;


/**
 * A variable declaration statement specifies a type and a simply name for a variable, with an
 * optional initial value.
 *
 * Additionally, this can represent the combination of a variable "conditional declaration".
 *
 * @author cp 2017.04.04
 */
public class VariableDeclarationStatement
        extends Statement
    {
    public VariableDeclarationStatement(TypeExpression type, Token name, Expression value)
        {
        this(type, name, null, value, true);
        }

    public VariableDeclarationStatement(TypeExpression type, Token name, Token op, Expression value, Boolean standalone)
        {
        this.name  = name;
        this.type  = type;
        this.value = value;
        this.cond  = op != null && op.getId() == Token.Id.COLON;
        this.term  = standalone;
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
            sb.append(' ')
            .append(cond ? ':' : '=')
            .append(' ')
            .append(value);
            }

        if (term)
            {
            sb.append(';');
            }

        return sb.toString();
        }

    public final TypeExpression type;
    public final Token name;
    public final Expression value;
    public final boolean cond;
    public final boolean term;
    }
