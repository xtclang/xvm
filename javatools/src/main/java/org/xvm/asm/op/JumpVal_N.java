package org.xvm.asm.op;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.lang.classfile.CodeBuilder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.xvm.asm.Argument;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.Op;
import org.xvm.asm.Register;

import org.xvm.asm.constants.ArrayConstant;
import org.xvm.asm.constants.MatchAnyConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.javajit.BuildContext;
import org.xvm.javajit.RegisterInfo;

import org.xvm.runtime.ConstHeap;
import org.xvm.runtime.Container;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.ExceptionHandle;
import org.xvm.runtime.ObjectHandle.GenericHandle;
import org.xvm.runtime.Utils;

import org.xvm.runtime.template.xBoolean;
import org.xvm.runtime.template.xBoolean.BooleanHandle;

import org.xvm.runtime.template._native.reflect.xRTType.TypeHandle;

import static org.xvm.util.Handy.readMagnitude;
import static org.xvm.util.Handy.readPackedInt;
import static org.xvm.util.Handy.readPackedLong;
import static org.xvm.util.Handy.writePackedLong;


/**
 * JMP_VAL_N #:(rvalue), #:(CONST, addr), addr-default ; if value equals a constant, jump to address, otherwise default
 * <ul>
 *     <li>with support for wildcard field matches (using MatchAnyConstant)</li>
 *     <li>with support for range matches (using RangeConstant)</li>
 * </ul>
 */
public class JumpVal_N
        extends OpSwitch {
    /**
     * Construct a JMP_VAL_N op.
     *
     * @param aArgVal     an array of value Arguments (the "condition")
     * @param afIsSwitch  a bit array of indicators for "isA" instead of "equals" testing
     * @param aConstCase  an array of "case" values (constants)
     * @param aOpCase     an array of Ops to jump to
     * @param opDefault   an Op to jump to in the "default" case
     */
    public JumpVal_N(Argument[] aArgVal, long afIsSwitch, Constant[] aConstCase, Op[] aOpCase, Op opDefault) {
        super(aConstCase, aOpCase, opDefault);

        m_aArgCond   = aArgVal;
        m_afIsSwitch = afIsSwitch;
    }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public JumpVal_N(DataInput in, Constant[] aconst)
            throws IOException {
        super(in, aconst);

        int   cArgs = readMagnitude(in);
        int[] anArg = new int[cArgs];

        m_afIsSwitch = readPackedLong(in);
        for (int i = 0; i < cArgs; ++i) {
            anArg[i] = readPackedInt(in);
        }
        m_anArgCond = anArg;
    }

    @Override
    public void write(DataOutput out, ConstantRegistry registry)
            throws IOException {
        super.write(out, registry);

        if (m_aArgCond != null) {
            m_anArgCond = encodeArguments(m_aArgCond, registry);
        }

        int[] anArg = m_anArgCond;
        int   cArgs = anArg.length;
        writePackedLong(out, cArgs);
        writePackedLong(out, m_afIsSwitch);
        for (int i = 0; i < cArgs; ++i) {
            writePackedLong(out, anArg[i]);
        }
    }

    @Override
    public int getOpCode() {
        return OP_JMP_VAL_N;
    }

    @Override
    public int process(Frame frame, int iPC) {
        try {
            ObjectHandle[] ahValue = frame.getArguments(m_anArgCond, m_anArgCond.length);

            if (anyDeferred(ahValue)) {
                Frame.Continuation stepNext = frameCaller ->
                        ensureJumpMap(frame, iPC, ahValue);

                return new Utils.GetArguments(ahValue, stepNext).doNext(frame);
            }

            return ensureJumpMap(frame, iPC, ahValue);
        } catch (ExceptionHandle.WrapperException e) {
            return frame.raiseException(e);
        }
    }

    private int ensureJumpMap(Frame frame, int iPC, ObjectHandle[] ahValue) {
        SwitchCache cache = frame.container().getRuntimeOpCache(this, CacheCategory.SWITCH,
                SwitchCache.class);
        return cache == null
                ? explodeConstants(frame, iPC, ahValue, 0, new ObjectHandle[m_aofCase.length][])
                : complete(frame, iPC, ahValue, cache);
    }

    private int explodeConstants(Frame frame, int iPC, ObjectHandle[] ahValue, int iRow,
                                 ObjectHandle[][] aahCases) {
        Container    container  = frame.container();
        ConstHeap    heap       = container.getConstHeap();
        ConstantPool poolTarget = frame.function().getConstantPool();

        for (int cRows = m_aofCase.length; iRow < cRows; iRow++) {
            int            cColumns     = ahValue.length;
            ArrayConstant  contValues   = frame.getConstant(m_anConstCase[iRow], ArrayConstant.class);
            Constant[]     aconstValues = contValues.getValue();
            ObjectHandle[] ahCases      = new ObjectHandle[cColumns];

            aahCases[iRow] = ahCases;

            assert aconstValues.length == cColumns;

            boolean fDeferred = false;
            for (int iC = 0; iC < cColumns; iC++) {
                Constant constCase = aconstValues[iC];
                if (constCase instanceof MatchAnyConstant) {
                    ahCases[iC] = ObjectHandle.DEFAULT;
                    continue;
                }

                ObjectHandle hCase = ahCases[iC] = frame.getConstHandle(constCase);
                if (isDeferred(hCase)) {
                    fDeferred = true;
                } else {
                    // caching a constant linked to the current pool would "leak" the current container
                    if (hCase.getComposition().getConstantPool() != poolTarget) {
                        hCase = heap.relocateConst(container, hCase, constCase);

                        assert hCase != null;
                        ahCases[iC] = hCase;
                    }
                }
            }

            if (fDeferred) {
                final int iRowNext = iRow + 1;
                Frame.Continuation stepNext =
                    frameCaller -> explodeConstants(frame, iPC, ahValue, iRowNext, aahCases);
                return new Utils.GetArguments(ahCases, stepNext).doNext(frame);
            }
        }

        SwitchCache cache;
        if (m_aofCase.length < 64) {
            cache = buildSmallJumpMaps(frame, aahCases);
        } else {
            cache = buildLargeJumpMaps(frame, aahCases);
        }
        return complete(frame, iPC, ahValue, cache);
    }

    private int complete(Frame frame, int iPC, ObjectHandle[] ahValue, SwitchCache cache) {
        return m_aofCase.length < 64
                ? findSmall(frame, iPC, ahValue, cache)
                : findLarge(frame, iPC, ahValue, cache);
    }

    @SuppressWarnings("fallthrough")
    private int findSmall(Frame frame, int iPC, ObjectHandle[] ahValue, SwitchCache cache) {
        Algorithm[]               aAlg   = cache.columnAlgorithms();
        Map<ObjectHandle, Long>[] aMap   = cache.smallJumpMaps();
        long[]                    alWild = cache.smallWildcards();
        long                      afIs   = m_afIsSwitch;
        long                      ixBits = -1;

        // first go over the native columns
        for (int iCol = 0, cCols = ahValue.length; iCol < cCols; iCol++) {
            ObjectHandle hValue   = ahValue[iCol];
            long         ixColumn = 0; // matching cases in this column
            switch (aAlg[iCol]) {
            case NativeRange: {
                List<RangeMatch> listRange = cache.smallRanges()[iCol];
                for (int iRange = 0, cR = listRange.size(); iRange < cR; iRange++) {
                    RangeMatch range = listRange.get(iRange);

                    // we only need to compare the range if there is a chance that it can impact
                    // the result
                    long lBit = range.caseBits();
                    if ((lBit & ixBits) != 0) {
                        if (hValue.isNativeEqual() &&
                            hValue.compareTo(range.lower()) >= 0 &&
                            hValue.compareTo(range.upper()) <= 0) {
                            ixColumn |= lBit;
                        }
                    }
                }
                // fall through and process the exact match
            }

            case NativeSimple: {
                if ((afIs & (1L << iCol)) == 0) {
                    Long LBits = aMap[iCol].get(hValue);
                    if (LBits != null) {
                        ixColumn |= LBits.longValue();
                    }
                } else {
                    // this is an "is(_)" column
                    TypeConstant     typeVal  = hValue.getUnsafeType();
                    ObjectHandle[][] aahCases = cache.cases();

                    for (int iRow = 0, cRows = aahCases.length; iRow < cRows; iRow++) {
                        ObjectHandle hCase = aahCases[iRow][iCol];
                        if (hCase == ObjectHandle.DEFAULT) {
                            // wildcard bits are merged after exact type checks
                            continue;
                        }

                        if (typeVal.isA(((TypeHandle) hCase).getDataType())) {
                            ixColumn |= (1L << iRow);
                        }
                    }
                }
                break;
            }

            default:
                continue;
            }

            // ixWild[i] == 0 means "no wildcards in column i"
            ixColumn |= alWild[iCol];
            ixBits   &= ixColumn;
        }

        if (ixBits == 0) {
            // no match
            return iPC + m_ofDefault;
        }

        if (cache.algorithm().isNative()) {
            // even if the value is not "isNativeEqual", there was not a single non-native value
            // among all the case values and ranges, which means that wildcards took care of it

            long lCaseBit = Long.lowestOneBit(ixBits);
            return iPC + m_aofCase[Long.numberOfTrailingZeros(lCaseBit)];
        }

        return findSmallNatural(frame, iPC, ahValue, ixBits, 0, 0, cache);
    }

    @SuppressWarnings("fallthrough")
    private int findSmallNatural(Frame frame, int iPC, ObjectHandle[] ahValue, long ixBits,
                                 int iRow, int iCol, SwitchCache cache) {
        ObjectHandle[][] aahCases = cache.cases();
        Algorithm[]      aAlg     = cache.columnAlgorithms();
        int              cRows    = aahCases.length;
        int              cColumns = ahValue.length;

        NextRow:
        for (; iRow < cRows; iRow++) {
            long lCaseBit = 1L << iRow;
            if ((ixBits & lCaseBit) == 0) {
                // this row has already been rejected
                continue;
            }
            ObjectHandle[] ahCases     = aahCases[iRow];
            int            iCurrentRow = iRow; // effectively final

            NextColumn:
            for (; iCol < cColumns; iCol++) {
                ObjectHandle hCase = ahCases[iCol];
                if (hCase == ObjectHandle.DEFAULT) {
                    continue;
                }
                TypeConstant typeColumn  = cache.columnTypes()[iCol];
                ObjectHandle hValue      = ahValue[iCol];
                int          iCurrentCol = iCol;

                switch (aAlg[iCol]) {
                case NaturalRange: {
                    if (hCase.getType().isA(frame.poolContext().typeRange())) {
                        GenericHandle hRange = (GenericHandle) hCase;
                        ObjectHandle  hLo    = hRange.getField(null, "lowerBound");
                        ObjectHandle  hHi    = hRange.getField(null, "upperBound");
                        BooleanHandle hLoEx  = (BooleanHandle) hRange.getField(null, "lowerExclusive");
                        BooleanHandle hHiEx  = (BooleanHandle) hRange.getField(null, "upperExclusive");

                        Frame.Continuation stepNext =
                            frameCaller -> findSmallNatural(frameCaller, iPC, ahValue, ixBits,
                                iCurrentRow, iCurrentCol + 1, cache);

                        switch (checkRange(frame, typeColumn, hValue, hLo, hHi,
                                    hLoEx.get(), hHiEx.get(), true, stepNext)) {
                        case Op.R_NEXT:
                            if (xBoolean.isTrue(frame.popStack())) {
                                continue NextColumn;
                            }
                            continue NextRow;

                        case Op.R_CALL:
                            frame.m_frameNext.addContinuation(frameCaller ->
                                xBoolean.isTrue(frameCaller.popStack())
                                    ? findSmallNatural(frameCaller, iPC, ahValue, ixBits,
                                        iCurrentRow, iCurrentCol + 1, cache)
                                    : findSmallNatural(frameCaller, iPC, ahValue, ixBits,
                                        iCurrentRow + 1, 0, cache));
                            return Op.R_CALL;

                        case Op.R_EXCEPTION:
                            return Op.R_EXCEPTION;

                        default:
                            throw new IllegalStateException();
                        }
                    }
                    // fall through
                }

                case NaturalSimple: {
                    switch (typeColumn.callEquals(frame, hValue, hCase, Op.A_STACK)) {
                    case Op.R_NEXT:
                        if (xBoolean.isTrue(frame.popStack())) {
                            continue NextColumn;
                        }
                        continue NextRow;

                    case Op.R_CALL:
                        frame.m_frameNext.addContinuation(frameCaller ->
                            xBoolean.isTrue(frameCaller.popStack())
                                ? findSmallNatural(frameCaller, iPC, ahValue, ixBits,
                                    iCurrentRow, iCurrentCol + 1, cache)
                                : findSmallNatural(frameCaller, iPC, ahValue, ixBits,
                                    iCurrentRow + 1, 0, cache));
                        return Op.R_CALL;

                    case Op.R_EXCEPTION:
                        return Op.R_EXCEPTION;

                    default:
                        throw new IllegalStateException();
                    }
                }
                }
            }
            // this row matched
            return iPC + m_aofCase[Long.numberOfTrailingZeros(lCaseBit)];
        }

        // nothing matched
        return iPC + m_ofDefault;
    }

    private int findLarge(Frame frame, int iPC, ObjectHandle[] ahValue, SwitchCache cache) {
        throw new UnsupportedOperationException();
    }

    @SuppressWarnings("unchecked")
    private SwitchCache buildSmallJumpMaps(Frame frame, ObjectHandle[][] aahCases) {
        int[]            anConstCase = m_anConstCase;
        int[]            anArg       = m_anArgCond;
        long             afIs        = m_afIsSwitch;
        int              cRows       = anConstCase.length;
        int              cColumns    = anArg.length;

        Map<ObjectHandle, Long>[] amapJump    = new Map[cColumns];
        long[]                    alWild      = new long[cColumns];
        Algorithm[]               aAlgorithm  = new Algorithm[cColumns];
        Algorithm                 algorithm   = Algorithm.NativeSimple;
        TypeConstant[]            atypeColumn = new TypeConstant[cColumns];
        List<RangeMatch>[]        alistRange  = newRangeLists(cColumns);

        Arrays.fill(aAlgorithm, Algorithm.NativeSimple); // assume native
        for (int iC = 0; iC < cColumns; iC++) {
            if ((afIs & (1L << iC)) == 0) {
                amapJump[iC] = new HashMap<>(cRows);
            }
            atypeColumn[iC] = frame.getLocalType(anArg[iC], null);
        }

        // now check for native/natural/ranges among the rows (cases)
        TypeConstant typeRange = frame.poolContext().typeRange();

        for (int iR = 0; iR < cRows; iR++ ) {
            long           lCaseBit = 1L << iR;
            ObjectHandle[] ahCases  = aahCases[iR];

            for (int iC = 0; iC < cColumns; iC++) {
                ObjectHandle hCase = ahCases[iC];

                if (hCase == ObjectHandle.DEFAULT) {
                    alWild[iC] |= lCaseBit;
                    continue;
                }

                assert !hCase.isMutable();

                TypeConstant typeCase    = hCase.getType();
                TypeConstant typeColumn  = atypeColumn[iC];
                boolean      fRange      = typeCase.isA(typeRange) && !typeColumn.isA(typeRange);

                if (aAlgorithm[iC].isNative()) {
                    if (hCase.isNativeEqual()) {
                        Map<ObjectHandle, Long> mapJump = amapJump[iC];
                        if (mapJump != null) {
                            mapJump.compute(hCase, (h, LOld) ->
                                Long.valueOf(lCaseBit | (LOld == null ?  0 : LOld.longValue())));
                        }
                    } else if (fRange) {
                        // assume native element
                        if (addRange((GenericHandle) hCase, lCaseBit, alistRange, iC)) {
                            aAlgorithm[iC] = aAlgorithm[iC].worstOf(Algorithm.NativeRange);
                        } else {
                            aAlgorithm[iC] = Algorithm.NaturalRange;
                        }
                    } else {
                        aAlgorithm[iC] = Algorithm.NaturalSimple;
                    }
                } else { // natural comparison
                    if (fRange) {
                        aAlgorithm[iC] = Algorithm.NaturalRange;

                        addRange((GenericHandle) hCase, lCaseBit, alistRange, iC);
                    } else {
                        amapJump[iC].compute(hCase, (h, LOld) ->
                            Long.valueOf(lCaseBit | (LOld == null ?  0 : LOld.longValue())));
                    }
                }
                algorithm = algorithm.worstOf(aAlgorithm[iC]);
            }
        }

        SwitchCache cache = new SwitchCache(aahCases, atypeColumn, copyMaps(amapJump), alWild,
                copyRanges(alistRange), aAlgorithm, algorithm);
        return frame.container().putRuntimeOpCacheIfAbsent(this, CacheCategory.SWITCH, cache,
                SwitchCache.class);
    }

    /**
     * Add a range definition for the specified column.
     *
     * @param hRange    the Range value
     * @param lCaseBit  the case index bit
     * @param alist     the range lists by column
     * @param iC        the current column to add a range to
     *
     * @return true iff the range element is native
     */
    private boolean addRange(GenericHandle hRange, long lCaseBit, List<RangeMatch>[] alist, int iC) {
        ObjectHandle hLow  = hRange.getField(null, "lowerBound");
        ObjectHandle hHigh = hRange.getField(null, "upperBound");

        // TODO: if the range is small, replace it with the exact hits for native values
        ensureRangeList(alist, iC).add(new RangeMatch(hLow, hHigh, lCaseBit));
        return hLow.isNativeEqual();
    }

    private List<RangeMatch> ensureRangeList(List<RangeMatch>[] alist, int iCol) {
        List<RangeMatch> list = alist[iCol];
        if (list == null) {
            list = alist[iCol] = new ArrayList<>();
        }
        return list;
    }

    @SuppressWarnings("unchecked")
    private static List<RangeMatch>[] newRangeLists(int cColumns) {
        return new List[cColumns];
    }

    @SuppressWarnings("unchecked")
    private static Map<ObjectHandle, Long>[] copyMaps(Map<ObjectHandle, Long>[] amapJump) {
        Map<ObjectHandle, Long>[] copy = new Map[amapJump.length];
        Arrays.setAll(copy, i -> amapJump[i] == null ? null : Map.copyOf(amapJump[i]));
        return copy;
    }

    @SuppressWarnings("unchecked")
    private static List<RangeMatch>[] copyRanges(List<RangeMatch>[] alistRange) {
        List<RangeMatch>[] copy = new List[alistRange.length];
        Arrays.setAll(copy, i -> alistRange[i] == null ? List.of() : List.copyOf(alistRange[i]));
        return copy;
    }

    private SwitchCache buildLargeJumpMaps(Frame frame, ObjectHandle[][] aahCases) {
        assert frame != null; // just to mitigate IDEA errors
        assert aahCases != null;
        throw new UnsupportedOperationException();
    }

    @Override
    public void registerConstants(ConstantRegistry registry) {
        registerArguments(m_aArgCond, registry);

        super.registerConstants(registry);
    }

    @Override
    protected void appendArgDescription(StringBuilder sb) {
        int cArgConds  = m_aArgCond  == null ? 0 : m_aArgCond.length;
        int cNArgConds = m_anArgCond == null ? 0 : m_anArgCond.length;
        int cArgs      = Math.max(cArgConds, cNArgConds);

        for (int i = 0; i < cArgs; ++i) {
            Argument arg  = i < cArgConds  ? m_aArgCond [i] : null;
            int      nArg = i < cNArgConds ? m_anArgCond[i] : Register.UNKNOWN;
            sb.append(Argument.toIdString(arg, nArg))
                    .append(", ");
        }
    }

    // ----- JIT support ---------------------------------------------------------------------------

    @Override
    public int build(BuildContext bctx, CodeBuilder code) {
        RegisterInfo[] regArgs = new RegisterInfo[m_anArgCond.length];
        for (int i = 0; i < m_anArgCond.length; i++) {
            regArgs[i] = bctx.ensureRegister(code, m_anArgCond[i]);
        }
        buildIfLadder(bctx, code, regArgs);
        return -1;
    }

    // ----- fields --------------------------------------------------------------------------------

    private int[]      m_anArgCond;
    private final long m_afIsSwitch;
    private Argument[] m_aArgCond;

    /**
     * Owner-local first-execution switch table. These arrays replace the old m_aahCases,
     * m_amapJumpSmall, m_alWildcardSmall, m_alistRangeSmall, m_aAlgorithm, m_algorithm, and
     * m_atypeColumn fields, but keep the same runtime data shape. The values are not decoded
     * bytecode metadata; they contain ObjectHandle and TypeConstant instances produced through a
     * particular Frame/Container. Storing them on the decoded op made the first executing owner
     * visible to later containers. Storing them under frame.container() lets every container build
     * and reuse its own switch table without changing switch matching behavior.
     */
    private record SwitchCache(ObjectHandle[][] cases, TypeConstant[] columnTypes,
                               Map<ObjectHandle, Long>[] smallJumpMaps, long[] smallWildcards,
                               List<RangeMatch>[] smallRanges, Algorithm[] columnAlgorithms,
                               Algorithm algorithm) {}

    private record RangeMatch(ObjectHandle lower, ObjectHandle upper, long caseBits) {}

    private enum CacheCategory {SWITCH}
}
