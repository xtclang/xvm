package org.xvm.asm.ast;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.ast.LanguageAST.ExprAST;

import static org.xvm.asm.ast.LanguageAST.NodeType.LIT_EXPR;

import static org.xvm.util.Handy.readMagnitude;
import static org.xvm.util.Handy.writePackedLong;


/**
 * An expression whose return values are ignored, and which is treated as a statement.
 */
public class LitExprAST<C>
    extends ExprAST<C> {

    LitExprAST() {}

    public LitExprAST(C type, C literal) {
        assert type != null && literal != null;
        this.type    = type;
        this.literal = literal;
    }

    C type;
    C literal;

    public C getType() {
        return type;
    }

    public C getLiteral() {
        return literal;
    }

    @Override
    C getType(int i) {
        assert i == 0;
        return type;
    }

    @Override
    public NodeType nodeType() {
        return LIT_EXPR;
    }

    @Override
    public void read(DataInput in, ConstantResolver<C> res)
            throws IOException {
        type    = res.getConstant(readMagnitude(in));
        literal = res.getConstant(readMagnitude(in));
    }

    @Override
    public void prepareWrite(ConstantResolver<C> res) {
        type    = res.register(type);
        literal = res.register(literal);
    }

    @Override
    public void write(DataOutput out, ConstantResolver<C> res)
            throws IOException {
        out.writeByte(nodeType().ordinal());
        writePackedLong(out, res.indexOf(type));
        writePackedLong(out, res.indexOf(literal));
    }

    @Override
    public String toString() {
        return type + " literal: " + literal;
    }
}
