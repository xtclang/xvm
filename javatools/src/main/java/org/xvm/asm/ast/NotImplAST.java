package org.xvm.asm.ast;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.util.Arrays;
import java.util.Objects;

import org.xvm.asm.constants.TypeConstant;

import static org.xvm.util.Handy.readUtf8String;
import static org.xvm.util.Handy.writeUtf8String;


/**
 * Place-holder that advertises that an implementation was missing.
 * TODO delete this class when binary AST work is complete
 */
public class NotImplAST
        extends ExprAST {

    private String         name;
    private TypeConstant[] types;

    NotImplAST() {}

    public NotImplAST(String name) {
        this.name  = name;
        this.types = NO_TYPES;
    }

    public NotImplAST(String name, TypeConstant[] types) {
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
    public TypeConstant getType(int i) {
        return types[i];
    }

    @Override
    protected void readBody(DataInput in, ConstantResolver res)
            throws IOException {
        name  = readUtf8String(in);
        types = readTypeArray(in, res);
    }

    @Override
    public void prepareWrite(ConstantResolver res) {
        prepareConstArray(types, res);
    }

    @Override
    protected void writeBody(DataOutput out, ConstantResolver res)
            throws IOException {
        writeUtf8String(out, name);
        writeConstArray(types, out, res);
    }

    @Override
    public String toString() {
        return "TODO CP: Implement " + name;
    }
}