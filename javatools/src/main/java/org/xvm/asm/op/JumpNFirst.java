package org.xvm.asm.op;


import java.io.DataInput;
import java.io.IOException;

import java.util.concurrent.atomic.AtomicBoolean;

import org.xvm.asm.Constant;
import org.xvm.asm.Op;
import org.xvm.asm.OpJump;

import org.xvm.runtime.Frame;


/**
 * JMP_NFIRST addr ; jump if this is NOT the first time the op has been executed
 */
public class JumpNFirst
        extends OpJump {
    /**
     * Construct a JMP_NFIRST op.
     *
     * @param op  the op to conditionally jump to
     */
    public JumpNFirst(Op op) {
        super(op);
    }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public JumpNFirst(DataInput in, Constant[] aconst)
            throws IOException {
        super(in, aconst);
    }

    @Override
    public int getOpCode() {
        return OP_JMP_NFIRST;
    }

    @Override
    public int process(Frame frame, int iPC) {
        if (m_fVisited.compareAndSet(false, true)) {
            return iPC + 1;
        }

        return jump(frame, iPC + m_ofJmp, m_cExits);
    }

    @Override
    public boolean advances() {
        return true;
    }

    @Override
    public boolean checkRedundant(Op[] aop) {
        if (m_ofJmp == 1) {
            markRedundant();
            return true;
        }

        return false;
    }

    /**
     * Deliberately process-wide decoded-op state for {@code assert:once}. The compiler emits this
     * opcode for assertions that should run only the first time execution reaches this instruction.
     * A container-local cache would change that behavior; a plain boolean made concurrent first
     * execution racy. Atomic publication preserves the old owner key while making one winner exact.
     */
    private final transient AtomicBoolean m_fVisited = new AtomicBoolean();
}
