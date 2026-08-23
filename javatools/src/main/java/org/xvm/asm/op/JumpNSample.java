package org.xvm.asm.op;


import java.io.DataInput;
import java.io.IOException;

import java.util.concurrent.ThreadLocalRandom;

import org.xvm.asm.Argument;
import org.xvm.asm.Constant;
import org.xvm.asm.Op;
import org.xvm.asm.OpCondJump;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.JavaLong;


/**
 * OP_JMP_NSAMPLE inverse-sample-rate, addr ; jump if this is NOT a selected sample based on the
 *                                          ; rvalue sample rate (a compile-time or run-time
 *                                          ; constant)
 *
 * <p/>TODO verify that inverse-sample-rate is a constant or a runtime constant
 */
public class JumpNSample
        extends OpCondJump {
    /**
     * Construct a OP_JMP_NSAMPLE op.
     *
     * @param arg  the sample rate (must be a compile-time or run-time constant)
     * @param op   the op to conditionally jump to
     */
    public JumpNSample(Argument arg, Op op) {
        super(arg, op);
    }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public JumpNSample(DataInput in, Constant[] aconst)
            throws IOException {
        super(in, aconst);
    }

    @Override
    public int getOpCode() {
        return OP_JMP_NSAMPLE;
    }

    @Override
    protected int completeUnaryOp(Frame frame, int iPC, ObjectHandle hValue) {
        long lEvery = ((JavaLong) hValue).getValue();

        // The operand arrives as a runtime handle, even though AssertStatement validates that the
        // source interval is a runtime constant. Do not cache it on this decoded Op: the same Op
        // object can be shared by multiple invocations/owners, and the first runtime value would
        // then silently control all later samples. Keep the historical clamping behavior and let the
        // verifier/compile-time validation reject illegal values.
        int nEvery = Math.max(1, Math.min(Integer.MAX_VALUE, (int) lEvery));
        return f_rnd.nextInt(nEvery) == 0 ? iPC + 1 : jump(frame, iPC + m_ofJmp, m_cExits);
    }

    private static final ThreadLocalRandom f_rnd = ThreadLocalRandom.current();
}
