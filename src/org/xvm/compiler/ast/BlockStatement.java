package org.xvm.compiler.ast;


import java.util.List;


/**
 * An block statement specifies a series of statements.
 *
 * @author cp 2017.03.28
 */
public class BlockStatement
        extends Statement
    {
    public BlockStatement(List<Statement> statements)
        {
        this.statements = statements;
        }

    @Override
    public String toString()
        {
        if (statements == null || statements.isEmpty())
            {
            return "{}";
            }

        StringBuilder sb = new StringBuilder();
        sb.append('{');
        for (Statement statement : statements)
            {
            sb.append('\n')
              .append(statement);
            }
        sb.append("\n}");

        return sb.toString();
        }

    public final List<Statement> statements;
    }
