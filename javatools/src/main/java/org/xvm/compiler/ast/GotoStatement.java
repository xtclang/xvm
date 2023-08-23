package org.xvm.compiler.ast;


import org.xvm.asm.op.Label;

import org.xvm.compiler.Token;


/**
 * A "goto" statement is any statement that impacts the control flow of the program by jumping
 * from the point of the "goto" statement. Thanks to Dijkstra, the term "goto" is now verboten,
 * and its use is considered to be harmful; as a result, languages must use terms like "break"
 * and "continue", and allow the use of such terms in a far more limited and predictable manner.
 */
public abstract class GotoStatement
        extends Statement
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a GotoStatement.
     *
     * @param keyword  the keyword (either "break" or "continue")
     * @param name     the name specified, or null
     */
    public GotoStatement(Token keyword, Token name)
        {
        this.keyword = keyword;
        this.name    = name;
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
        return name == null ? keyword.getEndPosition() : name.getEndPosition();
        }

    /**
     * @return true iff the "goto" statement specifies the name of a label
     */
    public boolean isLabeled()
        {
        return name != null;
        }

    /**
     * @return the name of the label on the labeled statement, or null
     */
    public String getLabeledName()
        {
        return isLabeled()
                ? name.getValueText()
                : null;
        }

    /**
     * @return the label that this statement will jump to
     */
    public Label getJumpLabel()
        {
        return m_label;
        }

    /**
     * Specify the label to use. This must occur from within the validate() stage, i.e. before the
     * emit() stage.
     *
     * @param label  the label that the statement jumps to
     */
    protected void setJumpLabel(Label label)
        {
        assert m_label == null;
        m_label = label;
        }

    /**
     * @return the statement that this "goto" statement refers to
     */
    protected Statement getTargetStatement()
        {
        if (isLabeled())
            {
            String  sLabel = getLabeledName();
            AstNode node = getParent();
            while (true)
                {
                AstNode nodeParent = node.getParent();
                if (nodeParent instanceof LabeledStatement stmtLabeled
                        && stmtLabeled.getName().equals(sLabel))
                    {
                    return (Statement) node;
                    }

                if (nodeParent.isComponentNode() || nodeParent instanceof StatementExpression)
                    {
                    // cannot pass a component boundary (and StatementExpression is a "pretend"
                    // component boundary, because it's supposed to act like a lambda)
                    return null;
                    }

                node = nodeParent;
                }
            }

        AstNode node = getParent();
        while (true)
            {
            if (node instanceof Statement stmt)
                {
                if (stmt.isNaturalGotoStatementTarget())
                    {
                    return stmt;
                    }

                if (stmt.isComponentNode())
                    {
                    // cannot pass a component boundary
                    return null;
                    }
                }

            node = node.getParent();
            }
        }


    // ----- debugging assistance ------------------------------------------------------------------

    @Override
    public String toString()
        {
        StringBuilder sb = new StringBuilder();

        sb.append(keyword.getId().TEXT);

        if (name != null)
            {
            sb.append(' ')
              .append(name.getValue());
            }

        sb.append(';');
        return sb.toString();
        }

    @Override
    public String getDumpDesc()
        {
        return toString();
        }


    // ----- fields --------------------------------------------------------------------------------

    protected Token keyword;
    protected Token name;

    protected transient Label m_label;
    }