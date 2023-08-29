package org.xvm.asm.ast;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.Op;

import org.xvm.asm.ast.LanguageAST.ExprAST;

import static org.xvm.asm.ast.LanguageAST.NodeType.RegisterExpr;

import static org.xvm.util.Handy.readPackedInt;
import static org.xvm.util.Handy.writePackedLong;


/**
 * An unnamed Register.
 */
public class RegisterAST<C>
        extends ExprAST<C> {

    /**
     * A value that we will is illegal to use as a register id.
     */
    private static final int UNASSIGNED_ID = Integer.MAX_VALUE;

    private int regId = UNASSIGNED_ID;

    transient C type;
    transient C name;     // null for unnamed registers

    RegisterAST() {}

    public RegisterAST(C type) {
        this(type, null);
    }

    public RegisterAST(C type, C name) {
        assert type != null;
        this.type  = type;
        this.name  = name;
    }

    /**
     * @param regId  the register id; a "special" (internal, hard-coded) register id is allowed
     * @param type   the type of the register, or null if not applicable
     * @param name   the type of the register, or null if not applicable
     */
    public RegisterAST(int regId, C type, C name) {
        assert regId > Op.CONSTANT_OFFSET;
        this.regId = regId;
        this.type  = type;
        this.name  = name;
    }

    public int getRegId() {
        return regId;
    }

    public boolean isRegIdSpecial() {
        return regId < 0;
    }

    public boolean isRegIdAssigned() {
        return regId != UNASSIGNED_ID;
    }

    public void setRegId(int regId) {
        assert regId >= 0 && this.regId >= 0;
        this.regId = regId;
    }

    public C getType() {
        return type;
    }

    public C getName() {
        return name;
    }

    @Override
    public C getType(int i) {
        assert i == 0;
        return type;
    }

    @Override
    public NodeType nodeType() {
        return RegisterExpr;
    }

    @Override
    public void read(DataInput in, ConstantResolver<C> res)
            throws IOException {
        regId = readPackedInt(in);
    }

    @Override
    public void prepareWrite(ConstantResolver<C> res) {
        // this class does not "own" the type and name, and therefore does not register them
    }

    @Override
    public void write(DataOutput out, ConstantResolver<C> res)
            throws IOException {
        // REVIEW at some point, this method should not be being called at all, i.e. this should assert
        out.writeByte(nodeType().ordinal());
        writeExpr(out, res);
    }

    @Override
    public void writeExpr(DataOutput out, ConstantResolver<C> res)
            throws IOException {
        writePackedLong(out, regId < 0 ? regId : 32 + regId);
    }

    // TODO dump

    @Override
    public String toString() {
        return "#" + regId;
    }
}