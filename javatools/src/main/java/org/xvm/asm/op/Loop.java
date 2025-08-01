package org.xvm.asm.op;

/**
 * LOOP ; (loop begin, variable scope begin)
 */
public class Loop
        extends Enter {
    /**
     * Construct an LOOP op.
     */
    public Loop() {
    }

    @Override
    public int getOpCode() {
        return OP_LOOP;
    }
}