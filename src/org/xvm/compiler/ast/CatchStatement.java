package org.xvm.compiler.ast;


import static org.xvm.util.Handy.indentLines;


/**
 * A "catch" statement. (Not actually a statement.)
 *
 * @author cp 2017.04.10
 */
public class CatchStatement
        extends Statement
    {
    public CatchStatement(VariableDeclarationStatement target, StatementBlock block)
        {
        this.target = target;
        this.block  = block;
        }

    @Override
    public String toString()
        {
        return "catch (" + target + ")\n" + indentLines(block.toString(), "    ");
        }

    public final VariableDeclarationStatement target;
    public final StatementBlock               block;
    }
