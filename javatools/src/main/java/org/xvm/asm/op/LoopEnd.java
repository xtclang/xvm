package org.xvm.asm.op;


import java.lang.classfile.CodeBuilder;

import java.util.List;

import org.xvm.asm.Op;
import org.xvm.asm.OpJump;

import org.xvm.javajit.BuildContext;

import org.xvm.runtime.Frame;


/**
 * LOOP_END rel_addr        ; rel_addr must be negative, and must point to a corresponding LOOP
 * <p/>
 * Each LOOP_END op must match up with a previous LOOP op.
 * <p/>
 * The LOOP_END op exits the scope and proceeds to the instruction at the location specified by
 * "rel_addr".
 */
public class LoopEnd
        extends Exit {
    /**
     * Constructor (for deserialization).
     */
    public LoopEnd() {
    }

    @Override
    public int getOpCode() {
        return OP_LOOP_END;
    }

    @Override
    public void resolveAddresses(Op[] aop) {
        if (m_opDest == null) {
            int depth = 1;
            for (int ip = getAddress()-1; ip >= 0; --ip) {
                Op  op   = aop[ip];
                int code = op.getOpCode();
                if (code == Op.OP_LOOP) {
                    if (--depth == 0) {
                        m_opDest = op;
                        break;
                    }
                } else if (code == Op.OP_LOOP_END) {
                    ++depth;
                }
            }
            assert m_opDest != null;
        }
        m_ofJmp = calcRelativeAddress(m_opDest);
        assert m_ofJmp < 0;
    }

    @Override
    public void markReachable(Op[] aop) {
        super.markReachable(aop);
        assert m_ofJmp < 0;
        m_opDest = aop[getAddress() + m_ofJmp];
        assert m_opDest.getOpCode() == Op.OP_LOOP;
    }

    @Override
    public boolean branches(Op[] aop, List<Integer> list) {
        list.add(m_ofJmp);
        return true;
    }

    @Override
    public boolean advances() {
        return false;
    }

    @Override
    public int process(Frame frame, int iPC) {
        frame.exitScope();
        return jump(frame, iPC + m_ofJmp, 0);
    }

    @Override
    public String toString() {
        return toName(getOpCode()) + ' ' + OpJump.getLabelDesc(m_opDest, m_ofJmp);
    }

    // ----- JIT support ---------------------------------------------------------------------------

    @Override
    public void build(BuildContext bctx, CodeBuilder code) {
        code.goto_(bctx.ensureLabel(code, getAddress() + m_ofJmp));

        super.build(bctx, code);
    }

    // ----- fields --------------------------------------------------------------------------------

    protected int m_ofJmp;

    private Op m_opDest;
}
