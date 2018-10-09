package org.xvm.compiler.ast;


import org.xvm.asm.op.Label;

import org.xvm.compiler.Token;


/**
 * A short-cirtuit statement represents "break" and "continue" statements.
 */
public abstract class ShortCircuitStatement
        extends Statement
    {
    // ----- constructors --------------------------------------------------------------------------

    public ShortCircuitStatement(Token keyword, Token name)
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
     * @return true iff the short-circuit statement specifies the name of a label
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
     * @return the label to jump to
     */
    public Label getJumpLabel()
        {
        return m_label;
        }

    /**
     * From within the validate() stage (i.e. before the emit() stage), specify the label to use.
     *
     * @param label  the "long jump" label that the statement short-circuits to
     */
    protected void setJumpLabel(Label label)
        {
        m_label = label;
        }

    /**
     * @return the statement that this short-circuiting statement refers to
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
                if (nodeParent instanceof LabeledStatement
                        && ((LabeledStatement) nodeParent).getName().equals(sLabel))
                    {
                    return (Statement) node;
                    }

                if (nodeParent.isComponentNode())
                    {
                    // cannot pass a component boundary
                    return null;
                    }

                node = nodeParent;
                }
            }

        AstNode node = getParent();
        while (true)
            {
            if (node instanceof Statement)
                {
                Statement stmt = (Statement) node;
                if (stmt.isNaturalShortCircuitStatementTarget())
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
