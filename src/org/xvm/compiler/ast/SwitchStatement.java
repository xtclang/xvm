package org.xvm.compiler.ast;


import org.xvm.compiler.Token;

import static org.xvm.util.Handy.indentLines;


/**
 * A "switch" statement.
 *
 * @author cp 2017.04.10
 */
public class SwitchStatement
        extends Statement
    {
    public SwitchStatement(Token keyword, Statement cond, StatementBlock block)
        {
        this.keyword = keyword;
        this.cond    = cond;
        this.block   = block;
        }

    @Override
    public String toString()
        {
        return "switch (" + cond + ")\n" + indentLines(block.toString(), "    ");
        }

    public final Token          keyword;
    public final Statement      cond;
    public final StatementBlock block;
    }
