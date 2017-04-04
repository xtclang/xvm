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

        int firstNonEnum = 0;
        if (statements.get(0) instanceof EnumDeclaration)
            {
            boolean multiline = false;
            for (int i = 0, c = statements.size(); i < c; ++i)
                {
                Statement stmt = statements.get(i);
                if (stmt instanceof EnumDeclaration)
                    {
                    EnumDeclaration enumStmt = (EnumDeclaration) stmt;
                    multiline |= enumStmt.doc != null || enumStmt.body != null;
                    ++firstNonEnum;
                    }
                }

            String sBetweenEnums = multiline ? ",\n" : ", ";
            for (int i = 0; i < firstNonEnum; ++i)
                {
                if (i == 0)
                    {
                    sb.append('\n');
                    }
                else
                    {
                    sb.append(sBetweenEnums);
                    }
                sb.append(statements.get(i));
                }
            if (firstNonEnum < statements.size())
                {
                sb.append(';');
                }
            }

        for (int i = firstNonEnum, c = statements.size(); i < c; ++i)
            {
            sb.append('\n')
              .append(statements.get(i));
            }
        sb.append("\n}");

        return sb.toString();
        }

    public final List<Statement> statements;
    }
