package org.xvm.asm.ast;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.util.Arrays;
import java.util.Objects;

import org.xvm.asm.ast.LanguageAST.ExprAST;

import static org.xvm.util.Handy.readUtf8String;
import static org.xvm.util.Handy.writeUtf8String;


/**
 * Place-holder that advertises that an implementation was missing.
 */
public class ExprNotImplAST<C>
        extends ExprAST<C> {

    private String   name;
    private Object[] types;

    ExprNotImplAST() {}

    public ExprNotImplAST(String name, C[] types) {
        assert name != null && types != null && Arrays.stream(types).allMatch(Objects::nonNull);
        this.name  = name;
        this.types = types;
    }

    @Override
    public NodeType nodeType() {
        return NodeType.EXPR_NOT_IMPL_YET;
    }

    @Override
    public int getCount() {
        return types.length;
    }

    @Override
    public C getType(int i) {
        return (C) types[i];
    }

    @Override
    public void read(DataInput in, ConstantResolver<C> res)
            throws IOException {
        name  = readUtf8String(in);
        types = readConstArray(in, res);
    }

    @Override
    public void prepareWrite(ConstantResolver<C> res) {
         res.registerAll(types);
    }

    @Override
    public void write(DataOutput out, ConstantResolver<C> res)
            throws IOException {
        out.writeByte(nodeType().ordinal());

        writeUtf8String(out, name);
        writeConstArray(out, res, types);
    }

    @Override
    public String toString() {
        return "TODO CP: Implement expression " + name;
    }
}