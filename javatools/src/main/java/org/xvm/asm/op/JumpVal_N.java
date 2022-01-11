package org.xvm.asm.op;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.xvm.asm.Argument;
import org.xvm.asm.Constant;
import org.xvm.asm.Op;
import org.xvm.asm.Register;

import org.xvm.asm.constants.ArrayConstant;
import org.xvm.asm.constants.MatchAnyConstant;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.ExceptionHandle;
import org.xvm.runtime.ObjectHandle.GenericHandle;
import org.xvm.runtime.Utils;

import static org.xvm.util.Handy.readMagnitude;
import static org.xvm.util.Handy.readPackedInt;
import static org.xvm.util.Handy.writePackedLong;


/**
 * JMP_VAL_N #:(rvalue), #:(CONST, addr), addr-default ; if value equals a constant, jump to address, otherwise default
 * <ul>
 *     <li>with support for wildcard field matches (using MatchAnyConstant)</li>
 *     <li>with support for range matches (using RangeConstant)</li>
 * </ul>
 */
public class JumpVal_N
        extends OpSwitch
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
        super(aConstCase, aOpCase, opDefault);

        m_aArgCond = aArgVal;
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
        super(in, aconst);

        int   cArgs = readMagnitude(in);
        int[] anArg = new int[cArgs];
        for (int i = 0; i < cArgs; ++i)
            {
            anArg[i] = readPackedInt(in);
            }
        m_anArgCond = anArg;
        }

    @Override
    public void write(DataOutput out, ConstantRegistry registry)
            throws IOException
        {
        super.write(out, registry);

        if (m_aArgCond != null)
            {
            m_anArgCond = encodeArguments(m_aArgCond, registry);
            }

        int[] anArg = m_anArgCond;
        int   cArgs = anArg.length;
        writePackedLong(out, cArgs);
        for (int i = 0; i < cArgs; ++i)
            {
            writePackedLong(out, anArg[i]);
            }
        }

    @Override
    public int getOpCode()
        {
        return OP_JMP_VAL_N;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        try
            {
            ObjectHandle[] ahValue = frame.getArguments(m_anArgCond, m_anArgCond.length);

            if (anyDeferred(ahValue))
                {
                Frame.Continuation stepNext = frameCaller ->
                        ensureJumpMap(frame, iPC, ahValue);

                return new Utils.GetArguments(ahValue, stepNext).doNext(frame);
                }

            return ensureJumpMap(frame, iPC, ahValue);
            }
        catch (ExceptionHandle.WrapperException e)
            {
            return frame.raiseException(e);
            }
        }

    protected int ensureJumpMap(Frame frame, int iPC, ObjectHandle[] ahValue)
        {
        if (m_algorithm == null)
            {
            return explodeConstants(frame, iPC, ahValue, 0, new ObjectHandle[m_aofCase.length][]);
            }
        return complete(frame, iPC, ahValue);
        }

    protected int explodeConstants(Frame frame, int iPC, ObjectHandle[] ahValue, int iRow,
                                   ObjectHandle[][] aahCases)
        {
        for (int cRows = m_aofCase.length; iRow < cRows; iRow++)
            {
            int            cColumns     = ahValue.length;
            ArrayConstant  contValues   = (ArrayConstant) frame.getConstant(m_anConstCase[iRow]);
            Constant[]     aconstValues = contValues.getValue();
            ObjectHandle[] ahCases      = new ObjectHandle[cColumns];

            aahCases[iRow] = ahCases;

            assert aconstValues.length == cColumns;

            boolean fDeferred = false;
            for (int iC = 0; iC < cColumns; iC++)
                {
                Constant constCase = aconstValues[iC];
                if (constCase instanceof MatchAnyConstant)
                    {
                    ahCases[iC] = ObjectHandle.DEFAULT;
                    continue;
                    }

                ObjectHandle hCase = ahCases[iC] = frame.getConstHandle(constCase);
                if (isDeferred(hCase))
                    {
                    fDeferred = true;
                    }
                }

            if (fDeferred)
                {
                final int iRowNext = iRow + 1;
                Frame.Continuation stepNext =
                    frameCaller -> explodeConstants(frame, iPC, ahValue, iRowNext, aahCases);
                return new Utils.GetArguments(ahCases, stepNext).doNext(frame);
                }
            }

        if (m_aofCase.length < 64)
            {
            buildSmallJumpMaps(ahValue, aahCases);
            }
        else
            {
            buildLargeJumpMaps(ahValue, aahCases);
            }
        return complete(frame, iPC, ahValue);
        }

    protected int complete(Frame frame, int iPC, ObjectHandle[] ahValue)
        {
        return m_aofCase.length < 64
                ? findSmall(frame, iPC, ahValue)
                : findLarge(frame, iPC, ahValue);
        }

    protected int findSmall(Frame frame, int iPC, ObjectHandle[] ahValue)
        {
        Algorithm[]               aAlg   = m_aAlgorithm;
        Map<ObjectHandle, Long>[] aMap   = m_amapJumpSmall;
        long[]                    alWild = m_alWildcardSmall;
        long                      ixBits = -1;

        // first go over the native columns
        for (int iC = 0, cCols = ahValue.length; iC < cCols; iC++)
            {
            ObjectHandle hValue   = ahValue[iC];
            long         ixColumn = 0; // matching cases in this column
            switch (aAlg[iC])
                {
                case NativeRange:
                    {
                    List<Object[]> listRange = m_alistRangeSmall[iC];
                    for (int iR = 0, cR = listRange.size(); iR < cR; iR++)
                        {
                        Object[] ao = listRange.get(iR);

                        long lBits = (Long) ao[2];

                        // we only need to compare the range if there is a chance that it can impact
                        // the result
                        if ((lBits & ixBits) != 0)
                            {
                            ObjectHandle hLow  = (ObjectHandle) ao[0];
                            ObjectHandle hHigh = (ObjectHandle) ao[1];

                            if (hValue.compareTo(hLow) >= 0 && hValue.compareTo(hHigh) <= 0)
                                {
                                ixColumn |= lBits;
                                }
                            }
                        }
                    // fall through and process the exact match
                    }

                case NativeSimple:
                    {
                    Long LBits = aMap[iC].get(hValue);
                    if (LBits != null)
                        {
                        ixColumn |= LBits.longValue();
                        }
                    break;
                    }

                default:
                    continue;
                }

            // ixWild[i] == 0 means "no wildcards in column i"
            ixColumn |= alWild[iC];
            ixBits   &= ixColumn;
            if (ixBits == 0)
                {
                // no match
                return iPC + m_ofDefault;
                }
            }

        if (m_algorithm.isNative())
            {
            long lBit = Long.lowestOneBit(ixBits);
            return iPC + m_aofCase[Long.numberOfTrailingZeros(lBit)];
            }

        return findSmallNatural(frame, iPC, ahValue, ixBits);
        }

    protected int findSmallNatural(Frame frame, int iPC, ObjectHandle[] ahValue, long ixBits)
        {
        throw new UnsupportedOperationException();
        }

    protected int findLarge(Frame frame, int iPC, ObjectHandle[] ahValue)
        {
        throw new UnsupportedOperationException();
        }

    /**
     * This method is synchronized because it needs to update four different values atomically.
     */
    private synchronized void buildSmallJumpMaps(ObjectHandle[] ahValue, ObjectHandle[][] aahCases)
        {
        if (m_algorithm != null)
            {
            // the jump map was built concurrently
            return;
            }

        int[] anConstCase = m_anConstCase;
        int   cRows       = anConstCase.length;
        int   cColumns    = ahValue.length;

        Map<ObjectHandle, Long>[] amapJump        = new Map[cColumns];
        long[]                    alWildcardSmall = new long[cColumns];
        Algorithm[]               aAlgorithm      = new Algorithm[cColumns];
        Algorithm                 algorithm       = Algorithm.NativeSimple;

        // first check for native vs. natural comparison
        for (int iC = 0; iC < cColumns; iC++)
            {
            amapJump[iC] = new HashMap<>(cRows);
            if (ahValue[iC].isNativeEqual())
                {
                aAlgorithm[iC] = Algorithm.NativeSimple;
                }
            else
                {
                aAlgorithm[iC] = Algorithm.NaturalSimple;
                }
            }

        for (int iC = 0; iC < cColumns; iC++)
            {
            amapJump[iC] = new HashMap<>(cRows);
            }

        // now check for presence of ranges among the rows (cases)
        for (int iR = 0; iR < cRows; iR++ )
            {
            long lCaseBit = 1L << iR;
            for (int iC = 0; iC < cColumns; iC++)
                {
                ObjectHandle hCase = aahCases[iR][iC];

                if (hCase == ObjectHandle.DEFAULT)
                    {
                    alWildcardSmall[iC] |= lCaseBit;
                    continue;
                    }

                assert !hCase.isMutable();

                if (aAlgorithm[iC].isNative())
                    {
                    if (hCase.isNativeEqual())
                        {
                        amapJump[iC].compute(hCase, (h, LOld) ->
                            Long.valueOf(lCaseBit | (LOld == null ?  0 : LOld.longValue())));
                        }
                    else
                        {
                        // this must be a range of native values
                        aAlgorithm[iC] = Algorithm.NativeRange;

                        addRange((GenericHandle) hCase, lCaseBit, cColumns, iC);
                        }
                    }
                else // natural comparison
                    {
                    if (hCase.getType().isAssignableTo(ahValue[iC].getType()))
                        {
                        amapJump[iC].compute(hCase, (h, LOld) ->
                            Long.valueOf(lCaseBit | (LOld == null ?  0 : LOld.longValue())));
                        }
                    else
                        {
                        // this must be a range of native values
                        aAlgorithm[iC] = Algorithm.NaturalRange;

                        addRange((GenericHandle) hCase, lCaseBit, cColumns, iC);
                        }
                    }
                algorithm = algorithm.worstOf(aAlgorithm[iC]);
                }
            }

        m_amapJumpSmall   = amapJump;
        m_alWildcardSmall = alWildcardSmall;
        m_aAlgorithm      = aAlgorithm;
        m_algorithm       = algorithm;
        }

    /**
     * Add a range definition for the specified column.
     *
     * @param hRange    the Range value
     * @param lCaseBit  the case index bit
     * @param cColumns  the total number of columns
     * @param iC        the current column to add a range to
     */
    private void addRange(GenericHandle hRange, long lCaseBit, int cColumns, int iC)
        {
        ObjectHandle hLow  = hRange.getField(null, "lowerBound");
        ObjectHandle hHigh = hRange.getField(null, "upperBound");

        // TODO: if the range is small, replace it with the exact hits for native values
        ensureRangeList(cColumns, iC).add(
                new Object[]{hLow, hHigh, Long.valueOf(lCaseBit)});
        }

    private List<Object[]> ensureRangeList(int cColumns, int iCol)
        {
        List<Object[]>[] alist = m_alistRangeSmall;
        if (alist == null)
            {
            alist = m_alistRangeSmall = new List[cColumns];
            }
        List<Object[]> list = alist[iCol];
        if (list == null)
            {
            list = alist[iCol] = new ArrayList<>();
            }
        return list;
        }

    private synchronized void buildLargeJumpMaps(ObjectHandle[] ahValue, ObjectHandle[][] aahCases)
        {
        throw new UnsupportedOperationException();
        }

    @Override
    public void registerConstants(ConstantRegistry registry)
        {
        registerArguments(m_aArgCond, registry);

        super.registerConstants(registry);
        }

    @Override
    protected void appendArgDescription(StringBuilder sb)
        {
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
        }


    // ----- fields --------------------------------------------------------------------------------

    protected int[]      m_anArgCond;
    private   Argument[] m_aArgCond;

    /**
     * Cached array of jump maps for # cases < 64. The Long represents a bitset of matching cases.
     * The bits are 0-based (bit 0 representing case #0), therefore the value of 0 is invalid.
     */
    private transient Map<ObjectHandle, Long>[] m_amapJumpSmall;
    /**
     * The bitmask of wildcard cases per column.
     * The bits are 0-based (bit 0 representing case #0), therefore the value of 0L indicates an
     * absence of wildcards in the column.
     */
    private transient long[] m_alWildcardSmall;
    /**
     * A list of ranges per column;
     *  a[0] - lower bound (ObjectHandle);
     *  a[1] - upper bound (ObjectHandle);
     *  a[2] - the case mask (Long)
     */
    private transient List<Object[]>[] m_alistRangeSmall;

    // cached array of jump maps; for # cases >= 64
    private transient Map<ObjectHandle, BitSet>[] m_amapJumpLarge; // maps per column keyed by constant handle
    private transient BitSet[] m_lDefaultLarge; // bitmask of default cases per column // TODO GG this is not used ... can we remove?

    private transient Algorithm[] m_aAlgorithm; // algorithm per column
    private transient Algorithm   m_algorithm;  // the "worst" of the column algorithms
    }
