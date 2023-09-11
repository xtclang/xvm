package org.xvm.asm.ast;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.util.Arrays;
import java.util.Objects;

import static org.xvm.util.Handy.readUtf8String;
import static org.xvm.util.Handy.writeUtf8String;


/**
 * Place-holder that advertises that an implementation was missing.
 * TODO delete this class when binary AST work is complete
 */
public class NotImplAST<C>
        extends ExprAST<C> {

    private String   name;
    private Object[] types;

    NotImplAST() {}

    public NotImplAST(String name) {
        this.name  = name;
        this.types = NO_CONSTS;
    }

    public NotImplAST(String name, C[] types) {
        assert name != null && types != null && Arrays.stream(types).allMatch(Objects::nonNull);
        this.name  = name;
        this.types = types;
    }

    @Override
    public NodeType nodeType() {
        return NodeType.NotImplYet;
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
    protected void readBody(DataInput in, ConstantResolver<C> res)
            throws IOException {
        name  = readUtf8String(in);
        types = readConstArray(in, res);
    }

    @Override
    public void prepareWrite(ConstantResolver<C> res) {
        prepareConstArray(types, res);
    }

    @Override
    protected void writeBody(DataOutput out, ConstantResolver<C> res)
            throws IOException {
        writeUtf8String(out, name);
        writeConstArray(types, out, res);
    }

    @Override
    public String toString() {
        return "TODO CP: Implement " + name;
    }
}