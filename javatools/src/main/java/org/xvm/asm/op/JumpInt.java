package org.xvm.asm.op;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.util.List;

import org.xvm.asm.Argument;
import org.xvm.asm.Constant;
import org.xvm.asm.Op;
import org.xvm.asm.OpJump;

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
        extends Op
    {
    /**
     * Construct a JMP_INT op.
     *
     * @param arg         a value Argument of type Int64
     * @param aOpCase     an array of Ops to jump to
     * @param opDefault   an Op to jump to in the "default" case
     */
    public JumpInt(Argument arg, Op[] aOpCase, Op opDefault)
        {
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
            throws IOException
        {
        m_nArg      = readPackedInt(in);
        m_aofCase   = readIntArray(in);
        m_ofDefault = readPackedInt(in);
        }

    @Override
    public void write(DataOutput out, ConstantRegistry registry)
            throws IOException
        {
        super.write(out, registry);

        if (m_argVal != null)
            {
            m_nArg = encodeArgument(m_argVal, registry);
            }

        writePackedLong(out, m_nArg);
        writeIntArray(out, m_aofCase);
        writePackedLong(out, m_ofDefault);
        }

    @Override
    public int getOpCode()
        {
        return OP_JMP_INT;
        }

    @Override
    public void resolveAddresses(Op[] aop)
        {
        if (m_aOpCase == null)
            {
            int c = m_aofCase.length;
            m_aOpCase = new Op[c];
            for (int i = 0; i < c; i++)
                {
                m_aOpCase[i] = calcRelativeOp(aop, m_aofCase[i]);
                }
            m_opDefault = calcRelativeOp(aop, m_ofDefault);
            }
        else
            {
            int c = m_aOpCase.length;
            m_aofCase = new int[c];
            for (int i = 0; i < c; i++)
                {
                m_aofCase[i] = calcRelativeAddress(m_aOpCase[i]);
                }
            m_ofDefault = calcRelativeAddress(m_opDefault);
            }
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        try
            {
            ObjectHandle hValue = frame.getArgument(m_nArg);

            return isDeferred(hValue)
                    ? hValue.proceed(frame, frameCaller ->
                        complete(frameCaller, iPC, frameCaller.popStack()))
                    : complete(frame, iPC, hValue);
            }
        catch (ExceptionHandle.WrapperException e)
            {
            return frame.raiseException(e);
            }
        }

    protected int complete(Frame frame, int iPC, ObjectHandle hValue)
        {
        long lValue = ((JavaLong) hValue).getValue();
        return lValue >= 0 && lValue < m_aofCase.length
            ? iPC + m_aofCase[(int) lValue]
            : iPC + m_ofDefault;
        }

    @Override
    public void markReachable(Op[] aop)
        {
        super.markReachable(aop);

        Op[]  aOpCase = m_aOpCase;
        int[] aofCase = m_aofCase;
        for (int i = 0, c = aofCase.length; i < c; ++i)
            {
            aOpCase[i] = findDestinationOp(aop, aofCase[i]);
            aofCase[i] = calcRelativeAddress(aOpCase[i]);
            }

        m_opDefault = findDestinationOp(aop, m_ofDefault);
        m_ofDefault = calcRelativeAddress(m_opDefault);
        }

    @Override
    public boolean branches(Op[] aop, List<Integer> list)
        {
        resolveAddresses(aop);
        for (int i : m_aofCase)
            {
            list.add(i);
            }
        list.add(m_ofDefault);
        return true;
        }

    @Override
    public boolean advances()
        {
        return false;
        }

    @Override
    public void registerConstants(ConstantRegistry registry)
        {
        m_argVal = registerArgument(m_argVal, registry);
        }

    @Override
    public String toString()
        {
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

        for (int i = 0; i < cLabels; ++i)
            {
            if (i > 0)
                {
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

    protected int   m_nArg;
    protected int[] m_aofCase;
    protected int   m_ofDefault;

    private Argument m_argVal;
    private Op[]     m_aOpCase;
    private Op       m_opDefault;
    }
