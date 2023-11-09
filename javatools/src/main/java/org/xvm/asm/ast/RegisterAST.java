package org.xvm.asm.ast;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.Op;
import org.xvm.asm.OpProperty;

import org.xvm.asm.constants.StringConstant;
import org.xvm.asm.constants.TypeConstant;

import static org.xvm.asm.ast.BinaryAST.NodeType.RegisterExpr;

import static org.xvm.util.Handy.writePackedLong;


/**
 * A Register (could be unnamed).
 */
public class RegisterAST
        extends ExprAST {

    /**
     * A value that is illegal to use as a register id.
     */
    private static final int UNASSIGNED_ID = Integer.MAX_VALUE;

    private int regId = UNASSIGNED_ID;

    transient TypeConstant   refType;  // the type of the reference to the register
    transient TypeConstant   type;     // the type of the register
    transient StringConstant name;     // null for unnamed registers

    public RegisterAST(TypeConstant type, StringConstant name) {
        assert type != null;
        this.type  = type;
        this.name  = name;
    }

    public RegisterAST(TypeConstant refType, TypeConstant type, StringConstant name) {
        this(type, name);
        assert refType != null;
        this.refType = refType;
    }

    /**
     * Special constructor used to create "special" registers.
     *
     * @param regId  the register id; a "special" (internal, hard-coded) register id is allowed
     * @param type   the type of the register, or null if not applicable
     * @param name   the type of the register, or null if not applicable
     */
    public RegisterAST(int regId, TypeConstant type, StringConstant name) {
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

    public boolean isAnnotated() {
        return refType != null;
    }

    public TypeConstant getRefType() {
        return refType;
    }

    public void setRegId(int regId) {
        assert regId >= 0 && this.regId >= 0;
        this.regId = regId;
    }

    public TypeConstant getType() {
        return type;
    }

    public StringConstant getNameConstant() {
        return name;
    }

    public String getName() {
        return name.getValue();
    }

    @Override
    public TypeConstant getType(int i) {
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
    protected void readBody(DataInput in, ConstantResolver res)
            throws IOException {
        throw new IllegalStateException("Use 'readExpr' instead");
    }

    @Override
    public void prepareWrite(ConstantResolver res) {
        // this class does not "own" the type and name, and therefore does not register them
    }

    @Override
    protected void writeBody(DataOutput out, ConstantResolver res)
            throws IOException {
        throw new IllegalStateException("Use 'writeExpr' instead");
    }

    @Override
    protected void writeExpr(DataOutput out, ConstantResolver res)
            throws IOException {
        assert regId != UNASSIGNED_ID;
        assert regId != OpProperty.A_STACK;

        writePackedLong(out, regId < 0 ? regId : 32 + regId);
    }

    public static  RegisterAST defaultReg(TypeConstant type) {
        return new RegisterAST(Op.A_DEFAULT, type, null);
    }

    @Override
    public String toString() {
        if (name != null) {
            return name.getValue();
        }

        return switch (regId) {
            case Op.A_THIS, Op.A_TARGET, Op.A_PUBLIC, Op.A_PROTECTED, Op.A_PRIVATE ->
                "this";
            case Op.A_STRUCT ->
                "this:struct";
            case Op.A_CLASS ->
                "this:class";
            case Op.A_SERVICE ->
                "this:service";
            case Op.A_SUPER ->
                "super";
            default -> regId == UNASSIGNED_ID
                ? "_"
                : "_#" + regId;
        };
    }
}