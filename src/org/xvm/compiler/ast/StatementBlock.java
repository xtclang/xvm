package org.xvm.compiler.ast;


import org.xvm.compiler.Source;

import java.lang.reflect.Field;

import java.util.List;


/**
 * An block statement specifies a series of statements.
 *
 * @author cp 2017.03.28
 */
public class StatementBlock
        extends Statement
    {
    // ----- constructors --------------------------------------------------------------------------

    public StatementBlock(List<Statement> stmts)
        {
        this(stmts, null);
        }

    public StatementBlock(List<Statement> stmts, Source source)
        {
        this.stmts  = stmts;
        this.source = source;
        }


    // ----- accessors -----------------------------------------------------------------------------

    public List<Statement> getStatements()
        {
        return stmts;
        }

    public void addStatement(Statement stmt)
        {
        stmts.add(stmt);
        }

    @Override
    public Source getSource()
        {
        return source == null
                ? super.getSource()
                : source;
        }

    @Override
    protected Field[] getChildFields()
        {
        return CHILD_FIELDS;
        }


    // ----- compile phases ------------------------------------------------------------------------


    // ----- debugging assistance ------------------------------------------------------------------

    @Override
    public String toString()
        {
        if (stmts == null || stmts.isEmpty())
            {
            return "{}";
            }

        StringBuilder sb = new StringBuilder();
        sb.append('{');

        int firstNonEnum = 0;
        if (stmts.get(0) instanceof EnumDeclaration)
            {
            boolean multiline = false;
            for (int i = 0, c = stmts.size(); i < c; ++i)
                {
                Statement stmt = stmts.get(i);
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
                sb.append(stmts.get(i));
                }
            if (firstNonEnum < stmts.size())
                {
                sb.append(';');
                }
            }

        for (int i = firstNonEnum, c = stmts.size(); i < c; ++i)
            {
            sb.append('\n')
              .append(stmts.get(i));
            }
        sb.append("\n}");

        return sb.toString();
        }


    // ----- fields --------------------------------------------------------------------------------

    protected Source          source;
    protected List<Statement> stmts;

    private static final Field[] CHILD_FIELDS = fieldsForNames(StatementBlock.class, "stmts");
    }
