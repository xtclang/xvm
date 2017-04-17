package org.xvm.compiler.ast;


import org.xvm.asm.ErrorList;
import org.xvm.asm.StructureContainer;
import org.xvm.util.ListMap;

import java.util.List;
import java.util.Map;


/**
 * An block statement specifies a series of statements.
 *
 * @author cp 2017.03.28
 */
public class StatementBlock
        extends Statement
    {
    // ----- constructors --------------------------------------------------------------------------

    public StatementBlock(List<Statement> statements)
        {
        this.statements = statements;
        }


    // ----- accessors -----------------------------------------------------------------------------

    public List<Statement> getStatements()
        {
        return statements;
        }

    public void addStatement(Statement stmt)
        {
        statements.add(stmt);
        }


    // ----- compile phases ------------------------------------------------------------------------

    @Override
    protected AstNode registerNames(AstNode parent, ErrorList errs)
        {
        AstNode newNode = super.registerNames(parent, errs);
        assert newNode == this;

        // recurse to children
        // TODO what if one of them changes?
        if (statements != null)
            {
            for (Statement stmt : statements)
                {
                stmt.registerNames(this, errs);
                }
            }

        return this;
        }


    // ----- debugging assistance ------------------------------------------------------------------

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

    @Override
    public Map<String, Object> getDumpChildren()
        {
        ListMap<String, Object> map = new ListMap();
        map.put("statements", statements);
        return map;
        }


    // ----- fields --------------------------------------------------------------------------------

    protected List<Statement> statements;
    }
