package org.xvm.asm.op;

import java.lang.classfile.CodeBuilder;

import org.xvm.javajit.BuildContext;

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

    // ----- JIT support ---------------------------------------------------------------------------

    @Override
    public void build(BuildContext bctx, CodeBuilder code) {
        code.labelBinding(bctx.ensureLabel(code, getAddress()));
    }
}