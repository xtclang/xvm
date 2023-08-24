package org.xvm.asm.ast;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.Register;
import org.xvm.asm.ast.LanguageAST.ExprAST;

import static org.xvm.asm.ast.LanguageAST.NodeType.REGISTER_EXPR;

import static org.xvm.util.Handy.readMagnitude;
import static org.xvm.util.Handy.writePackedLong;


/**
 * An unnamed Register.
 */
public class RegisterAST<C>
        extends ExprAST<C> {

    private           C        type;
    private           int      registerId;
    private transient Register register;

    RegisterAST() {}

    public RegisterAST(C type, int regId) {
        assert type != null && regId < Register.UNKNOWN;

        this.type       = type;
        this.registerId = regId;
    }

    public RegisterAST(C type, Register reg) {
        assert type != null;

        this.type     = type;
        this.register = reg;
    }

    public int getRegister() {
        return register == null ? registerId : register.getIndex();
    }

    @Override
    public NodeType nodeType() {
        return REGISTER_EXPR;
    }

    @Override
    public void read(DataInput in, ConstantResolver<C> res)
            throws IOException {
        type       = res.getConstant(readMagnitude(in));
        registerId = readMagnitude(in);
    }

    @Override
    public void prepareWrite(ConstantResolver<C> res) {
        type = res.register(type);
    }

    @Override
    public void write(DataOutput out, ConstantResolver<C> res)
            throws IOException {
        out.writeByte(nodeType().ordinal());

        writePackedLong(out, res.indexOf((C) type));
        writePackedLong(out, getRegister());
    }

    @Override
    public int getCount() {
        return 1;
    }

    @Override
    public C getType(int i) {
        assert i == 0;
        return type;
    }

    @Override
    public String toString() {
        return type + " #" + getRegister();
    }
}