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

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.ExceptionHandle;
import org.xvm.runtime.ObjectHandle.GenericHandle;
import org.xvm.runtime.Utils;

import static org.xvm.util.Handy.readPackedInt;
import static org.xvm.util.Handy.writePackedLong;


/**
 * JMP_VAL rvalue, #:(CONST, addr), addr-default ; if value equals a constant, jump to address, otherwise default
 * <p/>
 * Note: No support for wild-cards or intervals.
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
            if (hValue == null)
                {
                return R_REPEAT;
                }

            if (isDeferred(hValue))
                {
                ObjectHandle[] ahValue = new ObjectHandle[] {hValue};
                Frame.Continuation stepNext = frameCaller ->
                    collectCaseConstants(frame, iPC, ahValue[0]);

                return new Utils.GetArguments(ahValue, stepNext).doNext(frame);
                }

            return collectCaseConstants(frame, iPC, hValue);
            }
        catch (ExceptionHandle.WrapperException e)
            {
            return frame.raiseException(e);
            }
        }

    protected int collectCaseConstants(Frame frame, int iPC, ObjectHandle hValue)
        {
        if (m_ahCases == null)
            {
            m_ahCases = new ObjectHandle[m_aofCase.length];

            return explodeConstants(frame, iPC, hValue);
            }
        return complete(frame, iPC, hValue);
        }

    protected int explodeConstants(Frame frame, int iPC, ObjectHandle hValue)
        {
        ObjectHandle[] ahCases = m_ahCases;
        for (int iRow = 0, cRows = m_aofCase.length; iRow < cRows; iRow++)
            {
            ahCases[iRow] = frame.getConstHandle(m_anConstCase[iRow]);
            }

        if (Op.anyDeferred(ahCases))
            {
            Frame.Continuation stepNext = frameCaller ->
                {
                buildJumpMap(hValue);
                return complete(frame, iPC, hValue);
                };
            return new Utils.GetArguments(ahCases, stepNext).doNext(frame);
            }

        buildJumpMap(hValue);
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

            case NativeInterval:
                {
                // check the exact match first
                Index = mapJump.get(hValue);

                List<Object[]> listInterval = m_listIntervals;
                for (int iR = 0, cR = listInterval.size(); iR < cR; iR++)
                    {
                    Object[] ao = listInterval.get(iR);

                    int index = (Integer) ao[2];

                    // we only need to compare the range if there is a chance that it can impact
                    // the result (the range case precedes the exact match case)
                    if (Index == null || Index.intValue() > index)
                        {
                        ObjectHandle hLow  = (ObjectHandle) ao[0];
                        ObjectHandle hHigh = (ObjectHandle) ao[1];

                        if (hValue.compareTo(hLow) >= 0 && hValue.compareTo(hHigh) <= 0)
                            {
                            Index = index;
                            break;
                            }
                        }
                    }
                break;
                }

            default:
                return findNatural(frame, iPC, hValue);
            }

        return Index == null
            ? iPC + m_ofDefault
            : iPC + m_aofCase[Index.intValue()];
        }

    protected int findNatural(Frame frame, int iPC, ObjectHandle hValue)
        {
        throw new UnsupportedOperationException();
        }

    private void buildJumpMap(ObjectHandle hValue)
        {
        ObjectHandle[] ahCase  = m_ahCases;
        int[]          aofCase = m_aofCase;
        int            cCases  = ahCase.length;

        Map<ObjectHandle, Integer> mapJump = new HashMap<>(cCases);

        m_mapJump   = mapJump;
        m_algorithm = Algorithm.NativeSimple;

        for (int i = 0; i < cCases; i++ )
            {
            ObjectHandle hCase = m_ahCases[i];

            assert !hCase.isMutable();

            if (hValue.isNativeEqual())
                {
                if (hCase.isNativeEqual())
                    {
                    mapJump.put(hCase, Integer.valueOf(aofCase[i]));
                    }
                else
                    {
                    // this must be an interval of native values
                    m_algorithm = Algorithm.NativeInterval;

                    addInterval((GenericHandle) hCase, i);
                    }
                }
            else // natural comparison
                {
                if (hCase.getType().isAssignableTo(hValue.getType()))
                    {
                    m_algorithm = m_algorithm.worstOf(Algorithm.NaturalSimple);

                    mapJump.put(hCase, Integer.valueOf(aofCase[i]));
                    }
                else
                    {
                    // this must be an interval of native values
                    m_algorithm = Algorithm.NaturalInterval;

                    addInterval((GenericHandle) hCase, i);
                    }
                }
            }
        }

    /**
     * Add an interval definition for the specified column.
     *
     * @param hInterval the Interval value
     * @param index     the case index
     */
    private void addInterval(GenericHandle hInterval, int index)
        {
        ObjectHandle hLow  = hInterval.getField("lowerBound");
        ObjectHandle hHigh = hInterval.getField("upperBound");

        // TODO: if the interval is small, replace it with the exact hits for native values
        List<Object[]> list = m_listIntervals;
        if (list == null)
            {
            list = m_listIntervals = new ArrayList<>();
            }
        list.add(new Object[]{hLow, hHigh, Integer.valueOf(index)});
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
     * Cached array of ObjectHandles for cases.
     */
    private transient ObjectHandle[] m_ahCases;

    // cached jump map
    private Map<ObjectHandle, Integer> m_mapJump;

    /**
     * A list of intervals;
     *  a[0] - lower bound (ObjectHandle);
     *  a[1] - upper bound (ObjectHandle);
     *  a[2] - the case index (Integer)
     */
    private transient List<Object[]> m_listIntervals;

    private transient Algorithm m_algorithm;  // the algorithm
    }
