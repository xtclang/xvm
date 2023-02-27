package org.xvm.asm.op;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.xvm.asm.Argument;
import org.xvm.asm.Constant;
import org.xvm.asm.Op;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.ExceptionHandle;
import org.xvm.runtime.ObjectHandle.GenericHandle;
import org.xvm.runtime.Utils;

import org.xvm.runtime.template.xBoolean;
import org.xvm.runtime.template.xBoolean.BooleanHandle;

import static org.xvm.util.Handy.readPackedInt;
import static org.xvm.util.Handy.writePackedLong;


/**
 * JMP_VAL rvalue, #:(CONST, addr), addr-default ; if value equals a constant, jump to address, otherwise default
 * <p/>
 * Note: No support for wild-cards or ranges.
 */
public class JumpVal
        extends OpSwitch
    {
    /**
     * Construct a JMP_VAL op.
     *
     * @param argCond     a value Argument (the "condition")
     * @param aConstCase  an array of "case" values (constants)
     * @param aOpCase     an array of Ops to jump to
     * @param opDefault   an Op to jump to in the "default" case
     */
    public JumpVal(Argument argCond, Constant[] aConstCase, Op[] aOpCase, Op opDefault)
        {
        super(aConstCase, aOpCase, opDefault);

        m_argCond = argCond;
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
        super(in, aconst);

        m_nArgCond = readPackedInt(in);
        }

    @Override
    public void write(DataOutput out, ConstantRegistry registry)
            throws IOException
        {
        super.write(out, registry);

        if (m_argCond != null)
            {
            m_nArgCond = encodeArgument(m_argCond, registry);
            }

        writePackedLong(out, m_nArgCond);
        }

    @Override
    public int getOpCode()
        {
        return OP_JMP_VAL;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        try
            {
            ObjectHandle hValue = frame.getArgument(m_nArgCond);

            return isDeferred(hValue)
                    ? hValue.proceed(frame, frameCaller ->
                         ensureJumpMap(frame, iPC, frameCaller.popStack()))
                    : ensureJumpMap(frame, iPC, hValue);
            }
        catch (ExceptionHandle.WrapperException e)
            {
            return frame.raiseException(e);
            }
        }

    protected int ensureJumpMap(Frame frame, int iPC, ObjectHandle hValue)
        {
        return m_algorithm == null
                ? explodeConstants(frame, iPC, hValue)
                : complete(frame, iPC, hValue);
        }

    protected int explodeConstants(Frame frame, int iPC, ObjectHandle hValue)
        {
        ObjectHandle[] ahCase = new ObjectHandle[m_aofCase.length];
        for (int iRow = 0, cRows = m_aofCase.length; iRow < cRows; iRow++)
            {
            ahCase[iRow] = frame.getConstHandle(m_anConstCase[iRow]);
            }

        if (Op.anyDeferred(ahCase))
            {
            Frame.Continuation stepNext = frameCaller ->
                {
                buildJumpMap(frameCaller, ahCase);
                return complete(frameCaller, iPC, hValue);
                };
            return new Utils.GetArguments(ahCase, stepNext).doNext(frame);
            }

        buildJumpMap(frame, ahCase);
        return complete(frame, iPC, hValue);
        }

    protected int complete(Frame frame, int iPC, ObjectHandle hValue)
        {
        Map<ObjectHandle, Integer> mapJump = m_mapJump;
        Integer Index;

        switch (m_algorithm)
            {
            case NativeSimple:
                Index = mapJump.get(hValue);
                break;

            case NativeRange:
                {
                // check the exact match first
                Index = mapJump.get(hValue);

                List<Object[]> listRanges = m_listRanges;
                for (int iR = 0, cR = listRanges.size(); iR < cR; iR++)
                    {
                    Object[] ao = listRanges.get(iR);

                    int     index = (Integer) ao[2];
                    boolean fLoEx = (index & LO_EX) != 0;
                    boolean fHiEx = (index & HI_EX) != 0;

                    index &= ~EXCLUDE_MASK;

                    // we only need to compare the range if there is a chance that it can impact
                    // the result (the range case precedes the exact match case)
                    if (Index == null || Index.intValue() > index)
                        {
                        ObjectHandle hLow  = (ObjectHandle) ao[0];
                        ObjectHandle hHigh = (ObjectHandle) ao[1];

                        if (hValue.isNativeEqual())
                            {
                            int nCmpLo = hValue.compareTo(hLow);
                            int nCmpHi = hValue.compareTo(hHigh);

                            if (    (fLoEx ? nCmpLo > 0 : nCmpLo >= 0) &&
                                    (fHiEx ? nCmpHi < 0 : nCmpHi <= 0))
                                {
                                return iPC + index;
                                }
                            }
                        }
                    }
                break;
                }

            default:
                return findNatural(frame, iPC, hValue, 0);
            }

        return Index == null
            ? iPC + m_ofDefault
            : iPC + Index;
        }

    /**
     * Check if the specified values matches any of the cases starting at the specified index.
     *
     * @return one of Op.R_NEXT, Op.R_CALL, Op.R_EXCEPTION or the next iPC value
     */
    protected int findNatural(Frame frame, int iPC, ObjectHandle hValue, int iCase)
        {
        ObjectHandle[] ahCase = m_ahCase;
        int            cCases = ahCase.length;

        for (; iCase < cCases; iCase++)
            {
            ObjectHandle hCase    = ahCase[iCase];
            int          iCurrent = iCase; // effectively final

            switch (m_algorithm)
                {
                case NaturalRange:
                    {
                    if (hCase.getType().isA(frame.poolContext().typeRange()))
                        {
                        GenericHandle hRange = (GenericHandle) hCase;
                        ObjectHandle  hLo    = hRange.getField(null, "lowerBound");
                        ObjectHandle  hHi    = hRange.getField(null, "upperBound");
                        BooleanHandle hLoEx  = (BooleanHandle) hRange.getField(null, "lowerExclusive");
                        BooleanHandle hHiEx  = (BooleanHandle) hRange.getField(null, "upperExclusive");

                        Frame.Continuation stepNext =
                            frameCaller -> findNatural(frameCaller, iPC, hValue, iCurrent + 1);

                        switch (checkRange(frame, m_typeCond, hValue, hLo, hHi,
                                    hLoEx.get(), hHiEx.get(), true, stepNext))
                            {
                            case Op.R_NEXT:
                                if (frame.popStack() == xBoolean.TRUE)
                                    {
                                    // it's a match
                                    return iPC + m_aofCase[iCase];
                                    }
                            continue;

                            case Op.R_CALL:
                                frame.m_frameNext.addContinuation(frameCaller ->
                                    frameCaller.popStack() == xBoolean.TRUE
                                        ? iPC + m_aofCase[iCurrent]
                                        : findNatural(frameCaller, iPC, hValue, iCurrent + 1));
                                return Op.R_CALL;

                            case Op.R_EXCEPTION:
                                return Op.R_EXCEPTION;

                            default:
                                throw new IllegalStateException();
                            }
                        }
                    // fall through
                    }

                case NaturalSimple:
                    {
                    switch (m_typeCond.callEquals(frame, hValue, hCase, Op.A_STACK))
                        {
                        case Op.R_NEXT:
                            if (frame.popStack() == xBoolean.TRUE)
                                {
                                // it's a match
                                return iPC + m_aofCase[iCase];
                                }
                            continue;

                        case Op.R_CALL:
                            frame.m_frameNext.addContinuation(frameCaller ->
                                frameCaller.popStack() == xBoolean.TRUE
                                    ? iPC + m_aofCase[iCurrent]
                                    : findNatural(frameCaller, iPC, hValue, iCurrent + 1));
                            return Op.R_CALL;

                        case Op.R_EXCEPTION:
                            return Op.R_EXCEPTION;

                        default:
                            throw new IllegalStateException();
                        }
                    }
                }
            }
        // nothing matched
        return iPC + m_ofDefault;
        }

    /**
     * This method is synchronized because it needs to update three different values atomically.
     */
    private synchronized void buildJumpMap(Frame frame, ObjectHandle[] ahCase)
        {
        if (m_algorithm != null)
            {
            // the jump map was built concurrently
            return;
            }

        int[] aofCase = m_aofCase;
        int   cCases  = ahCase.length;

        Map<ObjectHandle, Integer> mapJump = new HashMap<>(cCases);

        Algorithm    algorithm = Algorithm.NativeSimple;
        TypeConstant typeCond  = frame.getLocalType(m_nArgCond, null);
        TypeConstant typeRange = frame.poolContext().typeRange();

        for (int i = 0; i < cCases; i++ )
            {
            ObjectHandle hCase = ahCase[i];

            assert !hCase.isMutable();

            TypeConstant typeCase = hCase.getType();
            boolean      fRange   = typeCase.isA(typeRange) && !typeCond.isA(typeRange);

            if (algorithm.isNative())
                {
                if (hCase.isNativeEqual())
                    {
                    mapJump.put(hCase, Integer.valueOf(aofCase[i]));
                    }
                else if (fRange)
                    {
                    if (addRange((GenericHandle) hCase, aofCase[i]))
                        {
                        algorithm = Algorithm.NativeRange;
                        }
                    else
                        {
                        algorithm = Algorithm.NaturalRange;
                        }
                    }
                else
                    {
                    algorithm = Algorithm.NaturalSimple;
                    }
                }
            else // natural comparison
                {
                if (fRange)
                    {
                    algorithm = Algorithm.NaturalRange;

                    addRange((GenericHandle) hCase, i);
                    }
                else
                    {
                    algorithm = algorithm.worstOf(Algorithm.NaturalSimple);

                    mapJump.put(hCase, Integer.valueOf(aofCase[i]));
                    }
                }
            }

        m_ahCase    = ahCase;
        m_mapJump   = mapJump;
        m_algorithm = algorithm;
        m_typeCond  = typeCond;
        }

    /**
     * Add a range definition for the specified column.
     *
     * @param hRange  the Range value
     * @param index   the case index
     *
     * @return true iff the range element is native
     */
    private boolean addRange(GenericHandle hRange, int index)
        {
        ObjectHandle  hLo   = hRange.getField(null, "lowerBound");
        ObjectHandle  hHi   = hRange.getField(null, "upperBound");
        BooleanHandle hLoEx = (BooleanHandle) hRange.getField(null, "lowerExclusive");
        BooleanHandle hHiEx = (BooleanHandle) hRange.getField(null, "upperExclusive");

        // TODO: if the range is small and sequential (an interval), replace it with the exact hits for native values
        List<Object[]> list = m_listRanges;
        if (list == null)
            {
            list = m_listRanges = new ArrayList<>();
            }

        assert (index & EXCLUDE_MASK) == 0;
        if (hLoEx.get())
            {
            index |= LO_EX;
            }
        if (hHiEx.get())
            {
            index |= HI_EX;
            }

        list.add(new Object[]{hLo, hHi, Integer.valueOf(index)});
        return hLo.isNativeEqual();
        }

    @Override
    public void registerConstants(ConstantRegistry registry)
        {
        m_argCond = m_argCond.registerConstants(registry);

        super.registerConstants(registry);
        }

    @Override
    protected void appendArgDescription(StringBuilder sb)
        {
        sb.append(Argument.toIdString(m_argCond, m_nArgCond))
          .append(", ");
        }


    // ----- fields --------------------------------------------------------------------------------

    protected int      m_nArgCond;
    private   Argument m_argCond;

    /**
     * Cached array of case constant values.
     */
    protected transient ObjectHandle[] m_ahCase;

    /**
     * Cached jump map. The Integer represents the matching case.
     */
    private transient Map<ObjectHandle, Integer> m_mapJump;

    /**
     * Cached algorithm value.
     */
    private transient Algorithm m_algorithm;

    /**
     * Cached condition type
     */
    private transient TypeConstant m_typeCond;

    /**
     * A list of ranges;
     *  a[0] - lower bound (ObjectHandle);
     *  a[1] - upper bound (ObjectHandle);
     *  a[2] - the case index (Integer) masked by the exclusivity bits
     */
    private transient List<Object[]> m_listRanges;

    private final static int EXCLUDE_MASK = 0xC000_0000;
    private final static int LO_EX        = 0x8000_0000;
    private final static int HI_EX        = 0x4000_0000;
    }