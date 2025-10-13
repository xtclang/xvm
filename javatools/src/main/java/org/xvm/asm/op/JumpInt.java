package org.xvm.asm.op;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.lang.classfile.CodeBuilder;
import java.lang.classfile.Label;
import java.lang.classfile.instruction.SwitchCase;

import java.util.ArrayList;
import java.util.List;

import org.xvm.asm.Argument;
import org.xvm.asm.Constant;
import org.xvm.asm.Op;
import org.xvm.asm.OpJump;

import org.xvm.javajit.BuildContext;
import org.xvm.javajit.BuildContext.Slot;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.JavaLong;
import org.xvm.runtime.ObjectHandle.ExceptionHandle;

import static org.xvm.util.Handy.readPackedInt;
import static org.xvm.util.Handy.writePackedLong;


/**
 * JMP_INT rvalue, #:(addr), addr-default ; if value equals (0,1,2,...), jump to address, otherwise default
 */
public class JumpInt
        extends Op {
    /**
     * Construct a JMP_INT op.
     *
     * @param arg         a value Argument of type Int64
     * @param aOpCase     an array of Ops to jump to
     * @param opDefault   an Op to jump to in the "default" case
     */
    public JumpInt(Argument arg, Op[] aOpCase, Op opDefault) {
        assert aOpCase != null;

        m_argVal    = arg;
        m_aOpCase   = aOpCase;
        m_opDefault = opDefault;
    }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public JumpInt(DataInput in, Constant[] aconst)
            throws IOException {
        m_nArg      = readPackedInt(in);
        m_aofCase   = readIntArray(in);
        m_ofDefault = readPackedInt(in);
    }

    @Override
    public void write(DataOutput out, ConstantRegistry registry)
            throws IOException {
        super.write(out, registry);

        if (m_argVal != null) {
            m_nArg = encodeArgument(m_argVal, registry);
        }

        writePackedLong(out, m_nArg);
        writeIntArray(out, m_aofCase);
        writePackedLong(out, m_ofDefault);
    }

    @Override
    public int getOpCode() {
        return OP_JMP_INT;
    }

    @Override
    public void resolveAddresses(Op[] aop) {
        int cCases;
        if (m_aOpCase == null) {
            int ofThis = getAddress();

            cCases    = m_aofCase.length;
            m_aOpCase = new Op[cCases];
            for (int i = 0; i < cCases; i++) {
                int ofOp = adjustRelativeAddress(aop, m_aofCase[i]);
                m_aofCase[i] = ofOp;
                m_aOpCase[i] = aop[ofThis + ofOp];
            }

            int ofOp = adjustRelativeAddress(aop, m_ofDefault);
            m_ofDefault = ofOp;
            m_opDefault = aop[ofThis + ofOp];
        } else {
            cCases    = m_aOpCase.length;
            m_aofCase = new int[cCases];
            for (int i = 0; i < cCases; i++) {
                m_aofCase[i] = calcRelativeAddress(m_aOpCase[i]);
            }
            m_ofDefault = calcRelativeAddress(m_opDefault);
        }

        m_acExits = new int[cCases];
        for (int i = 0; i < cCases; i++) {
            m_acExits[i] = calcExits(m_aOpCase[i]);
        }
        m_cDefaultExits = calcExits(m_opDefault);
    }

    @Override
    public int process(Frame frame, int iPC) {
        try {
            ObjectHandle hValue = frame.getArgument(m_nArg);

            return isDeferred(hValue)
                    ? hValue.proceed(frame, frameCaller ->
                        complete(frameCaller, iPC, frameCaller.popStack()))
                    : complete(frame, iPC, hValue);
        } catch (ExceptionHandle.WrapperException e) {
            return frame.raiseException(e);
        }
    }

    protected int complete(Frame frame, int iPC, ObjectHandle hValue) {
        int nCase = (int) ((JavaLong) hValue).getValue();
        return nCase >= 0 && nCase < m_aofCase.length
                ? jump(frame, iPC + m_aofCase[nCase], m_acExits[nCase])
                : jump(frame, iPC + m_ofDefault, m_cDefaultExits);
    }

    @Override
    public void markReachable(Op[] aop) {
        super.markReachable(aop);

        Op[]  aOpCase = m_aOpCase;
        int[] aofCase = m_aofCase;
        for (int i = 0, c = aofCase.length; i < c; ++i) {
            aOpCase[i] = findDestinationOp(aop, aofCase[i]);
            aofCase[i] = calcRelativeAddress(aOpCase[i]);
        }

        m_opDefault = findDestinationOp(aop, m_ofDefault);
        m_ofDefault = calcRelativeAddress(m_opDefault);
    }

    @Override
    public boolean branches(Op[] aop, List<Integer> list) {
        resolveAddresses(aop);
        for (int i : m_aofCase) {
            list.add(i);
        }
        list.add(m_ofDefault);
        return true;
    }

    @Override
    public boolean advances() {
        return false;
    }

    @Override
    public void registerConstants(ConstantRegistry registry) {
        m_argVal = registerArgument(m_argVal, registry);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        int cOps     = m_aOpCase == null ? 0 : m_aOpCase.length;
        int cOffsets = m_aofCase == null ? 0 : m_aofCase.length;
        int cLabels  = Math.max(cOps, cOffsets);

        sb.append(super.toString())
          .append(' ')
          .append(Argument.toIdString(m_argVal, m_nArg))
          .append(", ")
          .append(cLabels)
          .append(":[");

        for (int i = 0; i < cLabels; ++i) {
            if (i > 0) {
                sb.append(", ");
            }

            Op  op = i < cOps     ? m_aOpCase[i] : null;
            int of = i < cOffsets ? m_aofCase[i] : 0;
            sb.append(OpJump.getLabelDesc(op, of));
        }

        sb.append("], ")
          .append(OpJump.getLabelDesc(m_opDefault, m_ofDefault));

        return sb.toString();
    }

    // ----- JIT support ---------------------------------------------------------------------------

    @Override
    public void build(BuildContext bctx, CodeBuilder code) {
        Slot slotArg = bctx.loadArgument(code, m_nArg);

        switch (slotArg.cd().descriptorString()) {
        case "I", "S", "B", "C", "Z":
            break;

        case "J":
            code.l2i();
            break;

        default:
            throw new IllegalStateException();
        }

        int[] aofCase = m_aofCase;
        int   cCases  = aofCase.length;
        int   nThis   = getAddress();

        Label            labelDflt = bctx.ensureLabel(code, nThis + m_ofDefault);
        List<SwitchCase> listCases = new ArrayList<>();
        for (int iCase = 0; iCase < cCases; iCase++) {
            listCases.add(SwitchCase.of(iCase, bctx.ensureLabel(code, nThis + aofCase[iCase])));
        }
        code.tableswitch(0, cCases - 1, labelDflt, listCases);
    }

    // ----- fields --------------------------------------------------------------------------------

    protected int   m_nArg;
    protected int[] m_aofCase;
    protected int   m_ofDefault;

    private Argument m_argVal;
    private Op[]     m_aOpCase;
    private Op       m_opDefault;

    private transient int[] m_acExits;
    private transient int   m_cDefaultExits;
}
