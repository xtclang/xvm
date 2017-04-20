package org.xvm.compiler.ast;


import org.xvm.compiler.Token;

import java.lang.reflect.Field;

import static org.xvm.util.Handy.indentLines;


/**
 * An "if" statement.
 *
 * @author cp 2017.04.10
 */
public class IfStatement
        extends Statement
    {
    // ----- constructors --------------------------------------------------------------------------

    public IfStatement(Token keyword, Statement cond, StatementBlock block)
        {
        this(keyword, cond, block, null);
        }

    public IfStatement(Token keyword, Statement cond, StatementBlock stmtThen, Statement stmtElse)
        {
        this.keyword   = keyword;
        this.cond      = cond;
        this.stmtThen = stmtThen;
        this.stmtElse = stmtElse;
        }


    // ----- accessors -----------------------------------------------------------------------------

    @Override
    protected Field[] getChildFields()
        {
        return CHILD_FIELDS;
        }


    // ----- debugging assistance ------------------------------------------------------------------

    @Override
    public String toString()
        {
        StringBuilder sb = new StringBuilder();

        sb.append("if (")
          .append(cond)
          .append(")\n")
          .append(indentLines(stmtThen.toString(), "    "));

        if (stmtElse != null)
            {
            if (stmtElse instanceof IfStatement)
                {
                sb.append("\nelse ")
                  .append(stmtElse);
                }
            else
                {
                sb.append("\nelse\n")
                  .append(indentLines(stmtElse.toString(), "    "));
                }
            }

        return sb.toString();
        }


    // ----- fields --------------------------------------------------------------------------------

    protected Token     keyword;
    protected Statement cond;
    protected Statement stmtThen;
    protected Statement stmtElse;

    private static final Field[] CHILD_FIELDS = fieldsForNames(IfStatement.class, "cond", "stmtThen", "stmtElse");
    }
