package org.xvm.asm.op;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.util.HashMap;
import java.util.Map;

import org.xvm.asm.Argument;
import org.xvm.asm.Constant;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;
import org.xvm.asm.OpJump;
import org.xvm.asm.Register;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.ExceptionHandle;
import org.xvm.runtime.Utils;

import static org.xvm.util.Handy.readMagnitude;
import static org.xvm.util.Handy.readPackedInt;
import static org.xvm.util.Handy.writePackedLong;


/**
 * JMP_VAL #:(rvalue), #:(CONST, addr), addr-default ; if value equals a constant, jump to address, otherwise default
 */
public class JumpVal
        extends Op
    {
    /**
     * Construct a JMP_VAL op.
     *
     * @param aArgVal    an array of value Arguments
     * @param aArgCase   an array of "case" Arguments
     * @param aOpCase    an array of Ops to jump to
     * @param opDefault  an Op to jump to in the "default" case
     */
    public JumpVal(Argument[] aArgVal, Argument[] aArgCase, Op[] aOpCase, Op opDefault)
        {
        assert aOpCase != null;

        m_aArgVal   = aArgVal;
        m_aArgCase  = aArgCase;
        m_aOpCase   = aOpCase;
        m_opDefault = opDefault;
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public JumpVal(DataInput in, Constant[] aconst)
            throws IOException
        {
        int   cArgs = readMagnitude(in);
        int[] anArg = new int[cArgs];
        for (int i = 0; i < cArgs; ++i)
            {
            anArg[i] = readPackedInt(in);
            }
        m_anArg = anArg;

        int   cCases    = readMagnitude(in);
        int[] anArgCase = new int[cCases];
        int[] aofCase   = new int[cCases];
        for (int i = 0; i < cCases; ++i)
            {
            anArgCase[i] = readPackedInt(in);
            aofCase  [i] = readPackedInt(in);
            }
        m_anArgCase = anArgCase;
        m_aofCase   = aofCase;

        m_ofDefault = readPackedInt(in);
        }

    @Override
    public void write(DataOutput out, ConstantRegistry registry)
            throws IOException
        {
        if (m_aArgVal != null)
            {
            m_anArg     = encodeArguments(m_aArgVal , registry);
            m_anArgCase = encodeArguments(m_aArgCase, registry);
            }

        out.writeByte(getOpCode());

        int[] anArg = m_anArg;
        int   cArgs = anArg.length;
        writePackedLong(out, cArgs);
        for (int i = 0; i < cArgs; ++i)
            {
            writePackedLong(out, anArg[i]);
            }

        int[] anArgCase = m_anArgCase;
        int[] aofCase   = m_aofCase;
        int   c         = anArgCase.length;

        writePackedLong(out, c);
        for (int i = 0; i < c; ++i)
            {
            writePackedLong(out, anArgCase[i]);
            writePackedLong(out, aofCase  [i]);
            }

        writePackedLong(out, m_ofDefault);
        }

    @Override
    public int getOpCode()
        {
        return OP_JMP_VAL;
        }

    @Override
    public void resolveAddress(MethodStructure.Code code, int iPC)
        {
        if (m_aOpCase != null && m_aofCase == null)
            {
            int c = m_aOpCase.length;
            m_aofCase = new int[c];
            for (int i = 0; i < c; i++)
                {
                m_aofCase[i] = code.resolveAddress(iPC, m_aOpCase[i]);
                }
            m_ofDefault = code.resolveAddress(iPC, m_opDefault);
            }
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        assert m_anArg.length == 1; // TODO support >1

        try
            {
            ObjectHandle hValue = frame.getArgument(m_anArg[0]);
            if (hValue == null)
                {
                return R_REPEAT;
                }

            if (isDeferred(hValue))
                {
                ObjectHandle[] ahValue = new ObjectHandle[] {hValue};
                Frame.Continuation stepNext = frameCaller ->
                    complete(frame, iPC, ahValue[0]);

                return new Utils.GetArguments(ahValue, stepNext).doNext(frame);
                }

            return complete(frame, iPC, hValue);
            }
        catch (ExceptionHandle.WrapperException e)
            {
            return frame.raiseException(e);
            }
        }

    protected int complete(Frame frame, int iPC, ObjectHandle hValue)
        {
        Integer Index = ensureJumpMap(frame).get(hValue);

        return Index == null
            ? iPC + m_ofDefault
            : iPC + Index.intValue();
        }

    private Map<ObjectHandle, Integer> ensureJumpMap(Frame frame)
        {
        Map<ObjectHandle, Integer> mapJump = m_mapJump;
        if (mapJump == null)
            {
            int[] anArgCase = m_anArgCase;
            int[] aofCase   = m_aofCase;
            int   cCases    = anArgCase.length;

            mapJump = new HashMap<>(cCases);

            for (int i = 0, c = anArgCase.length; i < c; i++ )
                {
                ObjectHandle hCase = frame.getConstHandle(anArgCase[i]);

                if (hCase instanceof ObjectHandle.DeferredCallHandle)
                    {
                    throw new UnsupportedOperationException("not implemented"); // TODO
                    }

                assert !hCase.isMutable();

                mapJump.put(hCase, Integer.valueOf(aofCase[i]));
                }

            m_mapJump = mapJump;
            }
        return mapJump;
        }

    @Override
    public void registerConstants(ConstantRegistry registry)
        {
        for (int i = 0, c = m_aArgVal.length; i < c; i++)
            {
            m_aArgVal[i] = registerArgument(m_aArgVal[i], registry);
            }

        for (int i = 0, c = m_aArgCase.length; i < c; i++)
            {
            m_aArgCase[i] = registerArgument(m_aArgCase[i], registry);
            }
        }

    @Override
    public String toString()
        {
        StringBuilder sb = new StringBuilder();

        sb.append(super.toString())
          .append(' ');

        int cArgVals  = m_aArgVal == null ? 0 : m_aArgVal.length;
        int cNArgVals = m_anArg   == null ? 0 : m_anArg  .length;
        int cArgs     = Math.max(cArgVals, cNArgVals);

        for (int i = 0; i < cArgs; ++i)
            {
            Argument arg  = i < cArgVals    ? m_aArgVal[i] : null;
            int      nArg = i < cNArgVals   ? m_anArg  [i] : Register.UNKNOWN;
            sb.append(Argument.toIdString(arg, nArg))
              .append(", ");
            }

        int cOps     = m_aOpCase == null ? 0 : m_aOpCase.length;
        int cOffsets = m_aofCase == null ? 0 : m_aofCase.length;
        int cLabels  = Math.max(cOps, cOffsets);

        sb.append(cLabels)
          .append(":[");

        int cArgCases  = m_aArgCase  == null ? 0 : m_aArgCase .length;
        int cNArgCases = m_anArgCase == null ? 0 : m_anArgCase.length;
        assert Math.max(cArgCases, cNArgCases) == cLabels;

        for (int i = 0; i < cLabels; ++i)
            {
            Argument arg  = i < cArgCases    ? m_aArgCase [i] : null;
            int      nArg = i < cNArgCases   ? m_anArgCase[i] : Register.UNKNOWN;
            Op       op   = i < cOps     ? m_aOpCase  [i] : null;
            int      of   = i < cOffsets ? m_aofCase  [i] : 0;

            if (i > 0)
                {
                sb.append(", ");
                }

            sb.append(Argument.toIdString(arg, nArg))
              .append(":")
              .append(OpJump.getLabelDesc(op, of));
            }

        sb.append("], ")
          .append(OpJump.getLabelDesc(m_opDefault, m_ofDefault));

        return sb.toString();
        }

    protected int[] m_anArg;
    protected int[] m_anArgCase;
    protected int[] m_aofCase;
    protected int   m_ofDefault;

    private Argument[] m_aArgVal;
    private Argument[] m_aArgCase;
    private Op[]       m_aOpCase;
    private Op         m_opDefault;

    // cached jump map
    private Map<ObjectHandle, Integer> m_mapJump;
    }
