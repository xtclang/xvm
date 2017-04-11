package org.xvm.compiler.ast;


import java.util.List;


/**
 * This is a complicated form of the variable declaration statement that allows for multiple
 * L-values to be assigned, of which any number can be a new variable declaration.
 *
 * @author cp 2017.04.10
 */
public class MultipleDeclarationStatement
        extends Statement
    {
    public MultipleDeclarationStatement(List<Statement> lvalues, Expression rvalue)
        {
        this.lvalues = lvalues;
        this.rvalue  = rvalue;
        }

    @Override
    public String toString()
        {
        StringBuilder sb = new StringBuilder();

        sb.append('(');

        boolean first = true;
        for (Statement stmt : lvalues)
            {
            if (first)
                {
                first = false;
                }
            else
                {
                sb.append(", ");
                }

            sb.append(stmt);
            }

        sb.append(" = ")
          .append(rvalue)
          .append(';');

        return sb.toString();
        }

    public final List<Statement> lvalues;
    public final Expression      rvalue;
    }
