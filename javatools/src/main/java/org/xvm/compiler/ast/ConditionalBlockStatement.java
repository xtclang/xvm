package org.xvm.compiler.ast;


import java.util.List;
import java.util.stream.Stream;

import org.xvm.compiler.Token;


/**
 * Abstract base class for conditional statements that have a block (while, for-each, switch).
 */
public abstract class ConditionalBlockStatement
        extends ConditionalStatement {
    // ----- constructors --------------------------------------------------------------------------

    protected ConditionalBlockStatement(Token keyword, List<AstNode> conds, StatementBlock block) {
        super(keyword, conds);

        this.block = block;
    }


    // ----- accessors -----------------------------------------------------------------------------

    /**
     * @return the statement block
     */
    public StatementBlock getBlock() {
        return block;
    }

    @Override
    public List<AstNode> children() {
        return Stream.concat(super.children().stream(), Stream.of(block)).toList();
    }

    @Override
    protected void replaceChild(AstNode oldChild, AstNode newChild) {
        if (tryReplace(oldChild, newChild, block, n -> block = n)) {
            return;
        }
        super.replaceChild(oldChild, newChild);
    }


    // ----- fields --------------------------------------------------------------------------------

    protected StatementBlock block;
}