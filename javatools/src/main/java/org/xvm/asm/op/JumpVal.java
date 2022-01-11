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
        if (m_algorithm == null)
            {
            return explodeConstants(frame, iPC, hValue);
            }
        return complete(frame, iPC, hValue);
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
                buildJumpMap(hValue, ahCase);
                return complete(frameCaller, iPC, hValue);
                };
            return new Utils.GetArguments(ahCase, stepNext).doNext(frame);
            }

        buildJumpMap(hValue, ahCase);
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
            : iPC + Index;
        }

    protected int findNatural(Frame frame, int iPC, ObjectHandle hValue)
        {
        throw new UnsupportedOperationException();
        }

    /**
     * This method is synchronized because it needs to update three different values atomically.
     */
    private synchronized void buildJumpMap(ObjectHandle hValue, ObjectHandle[] ahCase)
        {
        if (m_algorithm != null)
            {
            // the jump map was built concurrently
            return;
            }

        int[] aofCase = m_aofCase;
        int   cCases  = ahCase.length;

        Map<ObjectHandle, Integer> mapJump = new HashMap<>(cCases);

        Algorithm algorithm = Algorithm.NativeSimple;

        for (int i = 0; i < cCases; i++ )
            {
            ObjectHandle hCase = ahCase[i];

            assert !hCase.isMutable();

            if (hValue.isNativeEqual())
                {
                if (hCase.isNativeEqual())
                    {
                    mapJump.put(hCase, Integer.valueOf(aofCase[i]));
                    }
                else
                    {
                    // this must be a range of native values
                    algorithm = Algorithm.NativeRange;

                    addRange((GenericHandle) hCase, aofCase[i]);
                    }
                }
            else // natural comparison
                {
                if (hCase.getType().isAssignableTo(hValue.getType()))
                    {
                    algorithm = algorithm.worstOf(Algorithm.NaturalSimple);

                    mapJump.put(hCase, Integer.valueOf(aofCase[i]));
                    }
                else
                    {
                    // this must be a range of native values
                    algorithm = Algorithm.NaturalRange;

                    addRange((GenericHandle) hCase, i);
                    }
                }
            }

        m_mapJump   = mapJump;
        m_algorithm = algorithm;
        }

    /**
     * Add a range definition for the specified column.
     *
     * @param hRange  the Range value
     * @param index   the case index
     */
    private void addRange(GenericHandle hRange, int index)
        {
        ObjectHandle hLow  = hRange.getField(null, "lowerBound");
        ObjectHandle hHigh = hRange.getField(null, "upperBound");

        // TODO: if the range is small and sequential (an interval), replace it with the exact hits for native values
        List<Object[]> list = m_listRanges;
        if (list == null)
            {
            list = m_listRanges = new ArrayList<>();
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

    // lazily calculated jump map and the algorithm
    private transient Map<ObjectHandle, Integer> m_mapJump;
    private transient Algorithm m_algorithm;

    /**
     * A list of ranges;
     *  a[0] - lower bound (ObjectHandle);
     *  a[1] - upper bound (ObjectHandle);
     *  a[2] - the case index (Integer)
     */
    private transient List<Object[]> m_listRanges;
    }
