package org.xvm.compiler.ast;


import java.util.Collections;
import java.util.List;

import org.xvm.compiler.Token;


/**
 * A conditional statement, including "if", "while", etc.
 */
public abstract class ConditionalStatement
        extends Statement
    {
    // ----- constructors --------------------------------------------------------------------------

    public ConditionalStatement(Token keyword, List<AstNode> conds)
        {
        this.keyword = keyword;
        this.conds   = conds  == null ? Collections.emptyList() : conds;
        }


    // ----- accessors -----------------------------------------------------------------------------

    @Override
    public long getStartPosition()
        {
        return keyword.getStartPosition();
        }

    /**
     * @return the number of conditions
     */
    public int getConditionCount()
        {
        return conds.size();
        }

    /**
     * @param i  a value between 0 and {@link #getConditionCount()}-1
     *
     * @return the condition, which is either an Expression or an AssignmentStatement
     */
    public AstNode getCondition(int i)
        {
        return conds.get(i);
        }

    /**
     * @param nodeChild  an expression (or AssignmentStatement) that is a child of this statement
     *
     * @return the index of the expression in the list of conditions within this statement, or -1
     */
    public int findCondition(AstNode nodeChild)
        {
        for (int i = 0, c = getConditionCount(); i < c; ++i)
            {
            if (conds.get(i) == nodeChild)
                {
                return i;
                }
            }
        return -1;
        }

    @Override
    protected boolean allowsShortCircuit(AstNode nodeChild)
        {
        // conditions are allowed to short-circuit
        return findCondition(nodeChild) >= 0;
        }

    protected int getLabelId()
        {
        int n = m_nLabel;
        if (n == 0)
            {
            m_nLabel = n = ++s_nLabelCounter;
            }
        return n;
        }


    // ----- fields --------------------------------------------------------------------------------

    protected Token         keyword;
    protected List<AstNode> conds;

    private static    int   s_nLabelCounter;
    private transient int   m_nLabel;
    }
