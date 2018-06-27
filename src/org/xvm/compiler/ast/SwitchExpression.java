package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import java.util.List;

import org.xvm.compiler.Token;

import static org.xvm.util.Handy.indentLines;


/**
 * A "switch" expression.
 */
public class SwitchExpression
        extends Expression
    {
    // ----- constructors --------------------------------------------------------------------------

    public SwitchExpression(Token keyword, Statement cond, List<AstNode> contents, long lEndPos)
        {
        this.keyword  = keyword;
        this.cond     = cond;
        this.contents = contents;
        this.lEndPos  = lEndPos;
        }


    // ----- accessors -----------------------------------------------------------------------------

    @Override
    public long getStartPosition()
        {
        return keyword.getStartPosition();
        }

    @Override
    public long getEndPosition()
        {
        return lEndPos;
        }

    @Override
    protected Field[] getChildFields()
        {
        return CHILD_FIELDS;
        }


    // ----- compilation ---------------------------------------------------------------------------

    // TODO


    // ----- debugging assistance ------------------------------------------------------------------

    @Override
    public String toString()
        {
        StringBuilder sb = new StringBuilder();

        sb.append("switch (");

        if (cond != null)
            {
            sb.append(cond);
            }

        sb.append(")\n    {");
        for (AstNode node : contents)
            {
            if (node instanceof CaseStatement)
                {
                sb.append('\n')
                  .append(indentLines(node.toString(), "    "));
                }
            else
                {
                sb.append(' ')
                  .append((Expression) node)
                  .append(';');
                }
            }
        sb.append("\n    }");

        return sb.toString();
        }


    // ----- fields --------------------------------------------------------------------------------

    protected Token         keyword;
    protected Statement     cond;
    protected List<AstNode> contents;
    protected long          lEndPos;

    private static final Field[] CHILD_FIELDS = fieldsForNames(SwitchExpression.class, "cond", "contents");
    }
