package org.xvm.asm.ast;


import java.io.DataOutput;
import java.io.IOException;

import static org.xvm.util.Handy.writePackedLong;


/**
 * Class hierarchy root for all expressions.
 */
public abstract class ExprAST<C>
        extends BinaryAST<C> {

    /**
     * @return the number of values yielded by the expression
     */
    public int getCount() {
        // subclasses that can yield more than one value must override this
        return 1;
    }

    /**
     * @param i a value in the range {@code 0 ..< getCount()}
     *
     * @return the type constant of the i-th value yielded by the expression
     */
    public abstract C getType(int i);

    /**
     * @return true iff this expression can theoretically be used as an L-Value; this is
     *     primarily intended to be used by assertions
     */
    public boolean isAssignable() {
        return false;
    }

    /**
     * Write the "expression short form" formatted data.
     */
    protected void writeExpr(DataOutput out, ConstantResolver<C> res)
            throws IOException {
        int id = nodeType().ordinal();
        if (id < NodeType.Escape.ordinal()) {
            writePackedLong(out, id);
            writeBody(out, res);
        } else {
            writePackedLong(out, NodeType.Escape.ordinal());
            write(out, res);
        }
    }
}
