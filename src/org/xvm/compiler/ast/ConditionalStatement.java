package org.xvm.compiler.ast;


import org.xvm.asm.op.Label;
import org.xvm.compiler.ast.Expression.Assignable;


/**
 * A ConditionalStatement represents any statement that can appear as any combination of a variable
 * declaration and/or variable assignment in the parenthesized conditional expression location of an
 * "if", "for", "while", "do-while", or "switch" statement.
 * <p/>
 * If the expression condition in the ConditionalStatement short-ciruits, the result must be
 * identical to the expression resulting in the value of {@code Boolean.False}.
 */
public abstract class ConditionalStatement
        extends Statement
    {
    /**
     * This method is used to indicate to the statement that it is being used by an "if" statement
     * as the condition. This method must be invoked before the statement is validated.
     * This method is used to indicate to the statement that it is being used by a "while" statement
     * as the condition. This method must be invoked before the statement is validated.
     * This method is used to indicate to the statement that it is being used by a "for"
     * statement as the condition. This method must be invoked before the statement is validated.
     */
    public void markConditional(Usage usage, Label label)
        {
        assert m_usage == Usage.Standalone && m_label == null;
        assert usage != null && usage != Usage.Standalone && (label != null || usage == Usage.Switch);

        m_usage = usage;
        m_label = label;
        }

    /**
     * @return the conditional usage of this statement, or {@link Usage#Standalone} if the usage is
     *         not conditional
     */
    public Usage getUsage()
        {
        return m_usage;
        }

    /**
     * The label is used differently, based on the {@link #getUsage()} value:
     * <p/>
     * <ul>
     * <li>{@link Usage#Standalone Standalone} - not applicable.</li>
     * <li>{@link Usage#If If} - the label is the destination for the condition being false.</li>
     * <li>{@link Usage#While While} - the label is the destination for the condition being true.</li>
     * <li>{@link Usage#For For} - TODO.</li>
     * <li>{@link Usage#Switch Switch} - no label is used.</li>
     * </ul>
     *
     * @return the label that this statement conditionally jumps to based on its Usage
     */
    public Label getLabel()
        {
        return m_label;
        }

    /**
     * @return true iff the conditional statement is being used as a condition that always results
     *         in the value false
     */
    public boolean isAlwaysFalse()
        {
        return false;
        }

    /**
     * @return true iff the conditional statement is being used as a condition that always results
     *         in the value true
     */
    public boolean isAlwaysTrue()
        {
        return false;
        }

    /**
     * @return true iff the conditional statement is being used as a condition that declares
     *         variables that should be managed in a nested scope
     */
    public boolean isScopeRequired()
        {
        return true;
        }

    /**
     * @return the declaration portion of the statement
     */
    public Statement onlyDeclarations()
        {
        if (m_stmtDeclOnly == null)
            {
            split();
            assert m_stmtDeclOnly != null;
            }
        return m_stmtDeclOnly;
        }

    /**
     * @return everything but the declaration portion of the statement, which includes any
     *         assignment and the condition itself
     */
    public Statement nonDeclarations()
        {
        if (m_stmtNonDecl == null)
            {
            split();
            assert m_stmtNonDecl != null;
            }
        return m_stmtNonDecl;
        }

    /**
     * @return true iff this form of ConditionalStatement has a conditional expression
     */
    public boolean hasExpression()
        {
        return false;
        }

    /**
     * @return the conditional expression, if applicable
     */
    public Expression getExpression()
        {
        throw new IllegalStateException(this.getClass().getName());
        }

    /**
     * Sub-classes implement this method and configure the declarations-only and non-declarations
     * statements.
     */
    protected abstract void split();

    /**
     * Called by the {@link #split()} method to store the result of the split.
     *
     * @param stmtDeclOnly  the "declaration only" statement
     * @param stmtNonDecl   the "everything but the declaration" statement
     */
    protected void configureSplit(Statement stmtDeclOnly, Statement stmtNonDecl)
        {
        assert stmtDeclOnly != null && stmtNonDecl != null;
        assert m_stmtDeclOnly == null && m_stmtNonDecl == null;

        Statement stmtParent = (Statement) getParent();
        stmtParent.adopt(stmtDeclOnly);
        stmtParent.adopt(stmtNonDecl);

        m_stmtDeclOnly = stmtDeclOnly;
        m_stmtNonDecl  = stmtNonDecl;
        }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * The manner in which the ConditionalStatement is used. When it is not being used as a
     * conditional, the usage is Standalone.
     */
    public static enum Usage {Standalone, If, While, For, Switch}

    /**
     * Specifies the usage of this statement.
     */
    private Usage m_usage = Usage.Standalone;

    /**
     * Specifies the label that this statement conditionally jumps to based on its Usage.
     */
    private Label m_label;

    /**
     * If the statement has been split, then this is the "declaration only" portion.
     */
    private Statement m_stmtDeclOnly;

    /**
     * If the statement has been split, then this is the "everything that is not the declaration"
     * portion.
     */
    private Statement m_stmtNonDecl;
    }
