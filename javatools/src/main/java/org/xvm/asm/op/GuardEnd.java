package org.xvm.asm.op;


import java.io.DataInput;
import java.io.IOException;

import java.lang.classfile.CodeBuilder;

import org.xvm.asm.Constant;
import org.xvm.asm.Op;
import org.xvm.asm.OpJump;
import org.xvm.asm.Scope;

import org.xvm.javajit.BuildContext;

import org.xvm.runtime.Frame;


/**
 * GUARD_END rel_addr
 * <p/>
 * Each GUARD_END op must match up with a previous GUARD op.
 * <p/>
 * The GUARD_END op exits the scope and proceeds to the instruction at the location specified by
 * "rel_addr".
 */
public class GuardEnd
        extends OpJump {
    /**
     * Construct a GUARD_E op based on the destination Op.
     *
     * @param op  the Op to jump to when the guarded section completes
     */
    public GuardEnd(Op op) {
        super(op);
    }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public GuardEnd(DataInput in, Constant[] aconst)
            throws IOException {
        super(in, aconst);
    }

    @Override
    public int getOpCode() {
        return OP_GUARD_END;
    }

    @Override
    public boolean isExit() {
        return true;
    }

    @Override
    public int process(Frame frame, int iPC) {
        frame.popGuard();
        frame.exitScope();
        return jump(frame, iPC + m_ofJmp, m_cExits);
    }

    @Override
    public void simulate(Scope scope) {
        scope.exitGuard();
        scope.exit(this);
    }

    // ----- JIT support ---------------------------------------------------------------------------

    @Override
    public void build(BuildContext bctx, CodeBuilder code) {
        bctx.exitScope(code);
        if (m_ofJmp > 1) {
            code.goto_(bctx.ensureLabel(code, getAddress() + m_ofJmp));
        } else {
            assert m_ofJmp == 1;
        }
    }
}
