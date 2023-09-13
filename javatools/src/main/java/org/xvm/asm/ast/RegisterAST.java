package org.xvm.asm.ast;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.Op;
import org.xvm.asm.OpProperty;

import static org.xvm.asm.ast.BinaryAST.NodeType.RegisterExpr;

import static org.xvm.util.Handy.writePackedLong;


/**
 * An unnamed Register.
 */
public class RegisterAST<C>
        extends ExprAST<C> {

    /**
     * A value that is illegal to use as a register id.
     */
    private static final int UNASSIGNED_ID = Integer.MAX_VALUE;

    private int regId = UNASSIGNED_ID;

    transient C type;
    transient C name;     // null for unnamed registers

    RegisterAST() {}

    public RegisterAST(C type, C name) {
        assert type != null;
        this.type  = type;
        this.name  = name;
    }

    /**
     * Special constructor used to create "special" registers.
     *
     * @param regId  the register id; a "special" (internal, hard-coded) register id is allowed
     * @param type   the type of the register, or null if not applicable
     * @param name   the type of the register, or null if not applicable
     */
    public RegisterAST(int regId, C type, C name) {
        assert regId > Op.CONSTANT_OFFSET && regId < 0;
        assert regId != Op.A_STACK;

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
    public boolean isAssignable() {
        // we don't have information here that would allow us to exclude type params
        return !isRegIdSpecial() || getRegId() == Op.A_IGNORE || getRegId() == Op.A_IGNORE_ASYNC;
    }

    @Override
    public NodeType nodeType() {
        return RegisterExpr;
    }

    @Override
    protected void readBody(DataInput in, ConstantResolver<C> res)
            throws IOException {
        throw new IllegalStateException("Use 'readExpr' instead");
    }

    @Override
    public void prepareWrite(ConstantResolver<C> res) {
        // this class does not "own" the type and name, and therefore does not register them
    }

    @Override
    protected void writeBody(DataOutput out, ConstantResolver<C> res)
            throws IOException {
        throw new IllegalStateException("Use 'writeExpr' instead");
    }

    @Override
    protected void writeExpr(DataOutput out, ConstantResolver<C> res)
            throws IOException {
        assert regId != UNASSIGNED_ID;
        assert regId != OpProperty.A_STACK;

        writePackedLong(out, regId < 0 ? regId : 32 + regId);
    }

    @Override
    public String dump() {
        return (name == null ? "_" : name.toString()) +
            "(" + type + ")#" + (regId == UNASSIGNED_ID ? "???" : String.valueOf(regId));
    }

    @Override
    public String toString() {
        return (name == null ? "_" : name.toString()) +
            "#" + (regId == UNASSIGNED_ID ? "???" : String.valueOf(regId));
    }
}