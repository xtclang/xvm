package org.xvm.compiler.ast;


import org.xvm.compiler.Token;


/**
 * A labeled statement represents a statement that has a label.
 *
 * @author cp 2017.04.09
 */
public class LabeledStatement
        extends Statement
    {
    public LabeledStatement(Token label, Statement stmt)
        {
        this.label = label;
        this.stmt  = stmt;
        }

    @Override
    public String toString()
        {
        return label.getValue() + ": " + stmt;
        }

    public final Token label;
    public final Statement stmt;
    }
