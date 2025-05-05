package org.xvm.asm.op;


import java.io.DataInput;
import java.io.IOException;

import org.xvm.asm.Argument;
import org.xvm.asm.Constant;
import org.xvm.asm.Op;
import org.xvm.asm.OpCondJump;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;

import org.xvm.runtime.template.xBoolean.BooleanHandle;


/**
 * JMP_TRUE rvalue, addr ; jump if value is Boolean.True
 */
public class JumpTrue
        extends OpCondJump {
    /**
     * Construct a JMP_TRUE op.
     *
     * @param arg  the argument to test
     * @param op   the op to conditionally jump to
     */
    public JumpTrue(Argument arg, Op op) {
        super(arg, op);
    }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public JumpTrue(DataInput in, Constant[] aconst)
            throws IOException {
        super(in, aconst);
    }

    @Override
    public int getOpCode() {
        return OP_JMP_TRUE;
    }

    @Override
    protected int completeUnaryOp(Frame frame, int iPC, ObjectHandle hValue) {
        return ((BooleanHandle) hValue).get() ? jump(frame, iPC + m_ofJmp, m_cExits) : iPC + 1;
    }
}
