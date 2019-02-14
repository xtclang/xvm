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
 * JMP_VAL_N #:(rvalue), #:(CONST, addr), addr-default ; if value equals a constant, jump to address, otherwise default
 * <ul><li>with support for wildcard field matches (using MatchAnyConstant)
 * </li><li>with support for interval matches (using IntervalConstant)
 * </li></ul>
 */
public class JumpVal_N
        extends Op
    {
    /**
     * Construct a JMP_VAL_N op.
     *
     * @param aArgVal     an array of value Arguments (the "condition")
     * @param aConstCase  an array of "case" values (constants)
     * @param aOpCase     an array of Ops to jump to
     * @param opDefault   an Op to jump to in the "default" case
     */
    public JumpVal_N(Argument[] aArgVal, Constant[] aConstCase, Op[] aOpCase, Op opDefault)
        {
        assert aOpCase != null;

        m_aArgCond   = aArgVal;
        m_aConstCase = aConstCase;
        m_aOpCase    = aOpCase;
        m_opDefault  = opDefault;
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public JumpVal_N(DataInput in, Constant[] aconst)
            throws IOException
        {
        int   cArgs = readMagnitude(in);
        int[] anArg = new int[cArgs];
        for (int i = 0; i < cArgs; ++i)
            {
            anArg[i] = readPackedInt(in);
            }
        m_anArgCond = anArg;

        int   cCases    = readMagnitude(in);
        int[] anArgCase = new int[cCases];
        int[] aofCase   = new int[cCases];
        for (int i = 0; i < cCases; ++i)
            {
            anArgCase[i] = readPackedInt(in);
            aofCase  [i] = readPackedInt(in);
            }
        m_anConstCase = anArgCase;
        m_aofCase   = aofCase;

        m_ofDefault = readPackedInt(in);
        }

    @Override
    public void write(DataOutput out, ConstantRegistry registry)
            throws IOException
        {
        if (m_aArgCond != null)
            {
            m_anArgCond   = encodeArguments(m_aArgCond, registry);
            m_anConstCase = encodeArguments(m_aConstCase, registry);
            }

        out.writeByte(getOpCode());

        int[] anArg = m_anArgCond;
        int   cArgs = anArg.length;
        writePackedLong(out, cArgs);
        for (int i = 0; i < cArgs; ++i)
            {
            writePackedLong(out, anArg[i]);
            }

        int[] anArgCase = m_anConstCase;
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
        return OP_JMP_VAL_N;
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
        assert m_anArgCond.length == 1; // TODO GG support >1

        try
            {
            ObjectHandle hValue = frame.getArgument(m_anArgCond[0]);
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

        // TODO GG wildcards ("_") and intervals ("..")

        return Index == null
                ? iPC + m_ofDefault
                : iPC + Index.intValue();
        }

    private Map<ObjectHandle, Integer> ensureJumpMap(Frame frame)
        {
        Map<ObjectHandle, Integer> mapJump = m_mapJump;
        if (mapJump == null)
            {
            int[] anConstCase = m_anConstCase;
            int[] aofCase     = m_aofCase;
            int   cCases      = anConstCase.length;

            mapJump = new HashMap<>(cCases);

            for (int i = 0, c = anConstCase.length; i < c; i++ )
                {
                ObjectHandle hCase = frame.getConstHandle(anConstCase[i]);

                // case values are always constants
                assert !(hCase instanceof ObjectHandle.DeferredCallHandle);
                assert !hCase.isMutable();

                // TODO GG this can contain wildcards ("_") and intevals ("..")

                mapJump.put(hCase, Integer.valueOf(aofCase[i]));
                }

            m_mapJump = mapJump;
            }
        return mapJump;
        }

    @Override
    public void registerConstants(ConstantRegistry registry)
        {
        registerArguments(m_aArgCond, registry);
        registerArguments(m_aConstCase, registry);
        }

    @Override
    public String toString()
        {
        StringBuilder sb = new StringBuilder();

        sb.append(super.toString())
                .append(' ');

        int cArgConds  = m_aArgCond  == null ? 0 : m_aArgCond.length;
        int cNArgConds = m_anArgCond == null ? 0 : m_anArgCond.length;
        int cArgs      = Math.max(cArgConds, cNArgConds);

        for (int i = 0; i < cArgs; ++i)
            {
            Argument arg  = i < cArgConds  ? m_aArgCond [i] : null;
            int      nArg = i < cNArgConds ? m_anArgCond[i] : Register.UNKNOWN;
            sb.append(Argument.toIdString(arg, nArg))
                    .append(", ");
            }

        int cOps     = m_aOpCase == null ? 0 : m_aOpCase.length;
        int cOffsets = m_aofCase == null ? 0 : m_aofCase.length;
        int cLabels  = Math.max(cOps, cOffsets);

        sb.append(cLabels)
                .append(":[");

        int cConstCases  = m_aConstCase  == null ? 0 : m_aConstCase.length;
        int cNConstCases = m_anConstCase == null ? 0 : m_anConstCase.length;
        assert Math.max(cConstCases, cNConstCases) == cLabels;

        for (int i = 0; i < cLabels; ++i)
            {
            Constant arg  = i < cConstCases  ? m_aConstCase [i] : null;
            int      nArg = i < cNConstCases ? m_anConstCase[i] : Register.UNKNOWN;
            Op       op   = i < cOps         ? m_aOpCase    [i] : null;
            int      of   = i < cOffsets     ? m_aofCase    [i] : 0;

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

    protected int[] m_anArgCond;
    protected int[] m_anConstCase;
    protected int[] m_aofCase;
    protected int   m_ofDefault;

    private Argument[] m_aArgCond;
    private Constant[] m_aConstCase;
    private Op[]       m_aOpCase;
    private Op         m_opDefault;

    // cached jump map
    private Map<ObjectHandle, Integer> m_mapJump;
    }
