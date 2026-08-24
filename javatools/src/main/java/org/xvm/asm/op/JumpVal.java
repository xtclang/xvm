package org.xvm.asm.op;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.lang.classfile.CodeBuilder;
import java.lang.classfile.Label;

import java.lang.classfile.instruction.SwitchCase;

import java.lang.constant.MethodTypeDesc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.xvm.asm.Argument;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.Op;

import org.xvm.asm.constants.ArrayConstant;
import org.xvm.asm.constants.ByteConstant;
import org.xvm.asm.constants.EnumValueConstant;
import org.xvm.asm.constants.RangeConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.javajit.BuildContext;
import org.xvm.javajit.Builder;
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

import static java.lang.constant.ConstantDescs.CD_long;

import static org.xvm.javajit.Builder.CD_Ctx;

import static org.xvm.util.Handy.readPackedInt;
import static org.xvm.util.Handy.writePackedLong;


/**
 * JMP_VAL rvalue, #:(CONST, addr), addr-default ; if value equals a constant, jump to address, otherwise default
 * <p/>
 * Note: No support for wild-cards or ranges.
 */
public class JumpVal
        extends OpSwitch {
    /**
     * Construct a JMP_VAL op.
     *
     * @param argCond     a value Argument (the "condition")
     * @param aConstCase  an array of "case" values (constants)
     * @param aOpCase     an array of Ops to jump to
     * @param opDefault   an Op to jump to in the "default" case
     */
    public JumpVal(Argument argCond, Constant[] aConstCase, Op[] aOpCase, Op opDefault) {
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
            throws IOException {
        super(in, aconst);

        m_nArgCond = readPackedInt(in);
    }

    @Override
    public void write(DataOutput out, ConstantRegistry registry)
            throws IOException {
        super.write(out, registry);

        if (m_argCond != null) {
            m_nArgCond = encodeArgument(m_argCond, registry);
        }

        writePackedLong(out, m_nArgCond);
    }

    @Override
    public int getOpCode() {
        return OP_JMP_VAL;
    }

    @Override
    public int process(Frame frame, int iPC) {
        try {
            ObjectHandle hValue = frame.getArgument(m_nArgCond);

            return isDeferred(hValue)
                    ? hValue.proceed(frame, frameCaller ->
                         ensureJumpMap(frame, iPC, frameCaller.popStack()))
                    : ensureJumpMap(frame, iPC, hValue);
        } catch (ExceptionHandle.WrapperException e) {
            return frame.raiseException(e);
        }
    }

    private int ensureJumpMap(Frame frame, int iPC, ObjectHandle hValue) {
        SwitchCache cache = frame.container().getRuntimeOpCache(this, CacheCategory.SWITCH,
                SwitchCache.class);
        return cache == null
                ? explodeConstants(frame, iPC, hValue)
                : complete(frame, iPC, hValue, cache);
    }

    private int explodeConstants(Frame frame, int iPC, ObjectHandle hValue) {
        ObjectHandle[] ahCase = new ObjectHandle[m_aofCase.length];
        for (int iRow = 0, cRows = m_aofCase.length; iRow < cRows; iRow++) {
            ahCase[iRow] = frame.getConstHandle(m_anConstCase[iRow]);
        }

        if (Op.anyDeferred(ahCase)) {
            Frame.Continuation stepNext = frameCaller -> {
                SwitchCache cache = buildJumpMap(frameCaller, ahCase);
                return complete(frameCaller, iPC, hValue, cache);
            };
            return new Utils.GetArguments(ahCase, stepNext).doNext(frame);
        }

        SwitchCache cache = buildJumpMap(frame, ahCase);
        return complete(frame, iPC, hValue, cache);
    }

    protected int complete(Frame frame, int iPC, ObjectHandle hValue, SwitchCache cache) {
        Map<ObjectHandle, Integer> mapJump = cache.jumpMap();
        Integer Index;

        switch (cache.algorithm()) {
        case NativeSimple:
            Index = mapJump.get(hValue);
            break;

        case NativeRange: {
            // check the exact match first
            Index = mapJump.get(hValue);

            if (!hValue.isNativeEqual()) {
                break; // REVIEW: should we assert instead?
            }
            for (RangeMatch range : cache.ranges()) {
                int index = range.encodedIndex();
                boolean fLoEx = (index & LO_EX) != 0;
                boolean fHiEx = (index & HI_EX) != 0;

                index &= ~EXCLUDE_MASK;

                // we only need to compare the range if there is a chance that it can impact
                // the result (the range case precedes the exact match case)
                if (Index == null || Index.intValue() > index) {
                    int nCmpLo = hValue.compareTo(range.lower());
                    int nCmpHi = hValue.compareTo(range.upper());

                    if ((fLoEx ? nCmpLo > 0 : nCmpLo >= 0) &&
                        (fHiEx ? nCmpHi < 0 : nCmpHi <= 0)) {
                        return jump(frame, iPC + m_aofCase[index], m_acExits[index]);
                    }
                }
            }
            break;
        }

        default:
            return findNatural(frame, iPC, hValue, 0, cache);
        }

        return Index == null
                ? jump(frame, iPC + m_ofDefault, m_cDefaultExits)
                : jump(frame, iPC + m_aofCase[Index], m_acExits[Index]);
    }

    /**
     * Check if the specified values matches any of the cases starting at the specified index.
     *
     * @return one of Op.R_NEXT, Op.R_CALL, Op.R_EXCEPTION or the next iPC value
     */
    @SuppressWarnings("fallthrough")
    private int findNatural(Frame frame, int iPC, ObjectHandle hValue, int iCase,
                            SwitchCache cache) {
        ObjectHandle[] ahCase = cache.cases();
        int            cCases = ahCase.length;

        for (; iCase < cCases; iCase++) {
            ObjectHandle hCase    = ahCase[iCase];
            int          iCurrent = iCase; // effectively final

            switch (cache.algorithm()) {
            case NaturalRange: {
                if (hCase.getType().isA(frame.poolContext().typeRange())) {
                    GenericHandle hRange = (GenericHandle) hCase;
                    ObjectHandle  hLo    = hRange.getField(null, "lowerBound");
                    ObjectHandle  hHi    = hRange.getField(null, "upperBound");
                    BooleanHandle hLoEx  = (BooleanHandle) hRange.getField(null, "lowerExclusive");
                    BooleanHandle hHiEx  = (BooleanHandle) hRange.getField(null, "upperExclusive");

                    Frame.Continuation stepNext =
                        frameCaller -> findNatural(frameCaller, iPC, hValue, iCurrent + 1, cache);

                    switch (checkRange(frame, cache.conditionType(), hValue, hLo, hHi,
                                hLoEx.get(), hHiEx.get(), true, stepNext)) {
                    case Op.R_NEXT:
                        if (xBoolean.isTrue(frame.popStack())) {
                            // it's a match
                            return jump(frame, iPC + m_aofCase[iCase], m_acExits[iCase]);
                        }
                        continue;

                    case Op.R_CALL:
                        frame.m_frameNext.addContinuation(frameCaller ->
                            xBoolean.isTrue(frameCaller.popStack())
                                ? jump(frameCaller, iPC + m_aofCase[iCurrent], m_acExits[iCurrent])
                                : findNatural(frameCaller, iPC, hValue, iCurrent + 1, cache));
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
                switch (cache.conditionType().callEquals(frame, hValue, hCase, Op.A_STACK)) {
                case Op.R_NEXT:
                    if (xBoolean.isTrue(frame.popStack())) {
                        // it's a match
                        return jump(frame, iPC + m_aofCase[iCase], m_acExits[iCase]);
                    }
                    continue;

                case Op.R_CALL:
                    frame.m_frameNext.addContinuation(frameCaller ->
                        xBoolean.isTrue(frameCaller.popStack())
                            ? jump(frameCaller, iPC + m_aofCase[iCurrent], m_acExits[iCurrent])
                            : findNatural(frameCaller, iPC, hValue, iCurrent + 1, cache));
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
        return jump(frame, iPC + m_ofDefault, m_cDefaultExits);
    }

    private SwitchCache buildJumpMap(Frame frame, ObjectHandle[] ahCase) {
        int                        cCases  = ahCase.length;
        Map<ObjectHandle, Integer> mapJump = new HashMap<>(cCases);
        List<RangeMatch>           ranges  = new ArrayList<>();

        Algorithm    algorithm  = Algorithm.NativeSimple;
        TypeConstant typeCond   = frame.getLocalType(m_nArgCond, null);
        TypeConstant typeRange  = frame.poolContext().typeRange();
        Container    container  = frame.container();
        ConstHeap    heap       = container.getConstHeap();
        ConstantPool poolTarget = frame.function().getConstantPool();

        for (int iCase = 0; iCase < cCases; iCase++ ) {
            ObjectHandle hCase = ahCase[iCase];

            assert !hCase.isMutable();

            // caching a constant linked to the current pool would "leak" the current container
            if (hCase.getComposition().getConstantPool() != poolTarget) {
                hCase = heap.relocateConst(container, hCase, frame.getConstant(m_anConstCase[iCase]));

                assert hCase != null;
                ahCase[iCase] = hCase;
            }

            TypeConstant typeCase = hCase.getType();
            boolean      fRange   = typeCase.isA(typeRange) && !typeCond.isA(typeRange);

            if (algorithm.isNative()) {
                if (hCase.isNativeEqual()) {
                    mapJump.put(hCase, Integer.valueOf(iCase));
                } else if (fRange) {
                    if (addRange((GenericHandle) hCase, iCase, ranges)) {
                        algorithm = Algorithm.NativeRange;
                    } else {
                        algorithm = Algorithm.NaturalRange;
                    }
                } else {
                    algorithm = Algorithm.NaturalSimple;
                }
            } else { // natural comparison
                if (fRange) {
                    algorithm = Algorithm.NaturalRange;

                    addRange((GenericHandle) hCase, iCase, ranges);
                } else {
                    algorithm = algorithm.worstOf(Algorithm.NaturalSimple);

                    mapJump.put(hCase, Integer.valueOf(iCase));
                }
            }
        }

        SwitchCache cache = new SwitchCache(ahCase, Map.copyOf(mapJump), algorithm, typeCond,
                List.copyOf(ranges));
        return frame.container().putRuntimeOpCacheIfAbsent(this, CacheCategory.SWITCH, cache,
                SwitchCache.class);
    }

    /**
     * Add a range definition for the specified column.
     *
     * @param hRange  the Range value
     * @param index   the case index
     *
     * @return true iff the range element is native
     */
    private boolean addRange(GenericHandle hRange, int index, List<RangeMatch> ranges) {
        ObjectHandle  hLo   = hRange.getField(null, "lowerBound");
        ObjectHandle  hHi   = hRange.getField(null, "upperBound");
        BooleanHandle hLoEx = (BooleanHandle) hRange.getField(null, "lowerExclusive");
        BooleanHandle hHiEx = (BooleanHandle) hRange.getField(null, "upperExclusive");

        // TODO: if the range is small and sequential (an interval), replace it with the exact hits for native values
        assert (index & EXCLUDE_MASK) == 0;
        if (hLoEx.get()) {
            index |= LO_EX;
        }
        if (hHiEx.get()) {
            index |= HI_EX;
        }

        ranges.add(new RangeMatch(hLo, hHi, index));
        return hLo.isNativeEqual();
    }

    @Override
    public void registerConstants(ConstantRegistry registry) {
        m_argCond = m_argCond.registerConstants(registry);

        super.registerConstants(registry);
    }

    @Override
    protected void appendArgDescription(StringBuilder sb) {
        sb.append(Argument.toIdString(m_argCond, m_nArgCond))
          .append(", ");
    }


    // ----- JIT support ---------------------------------------------------------------------------

    @Override
    public int build(BuildContext bctx, CodeBuilder code) {
        int[] aofCase = m_aofCase;
        int   cRows   = aofCase.length;
        assert cRows > 0;

        if (cRows == 1) {
            // only a single case, so just build an if ladder
            RegisterInfo regArg = bctx.ensureRegister(code, m_nArgCond);
            buildIfLadder(bctx, code, regArg);
        } else {
            // retrieve the base type
            Constant constant = bctx.getConstant(m_anConstCase[0]);
            if (constant.getType().isOnlyNullable()) {
                // the first case is "Null", so we need to use the next case to work out the types
                constant = bctx.getConstant(m_anConstCase[1]);
            }

            if (constant instanceof ArrayConstant array) {
                constant = array.getValue()[0];
            }
            if (constant instanceof RangeConstant range) {
                constant = range.getFirst();
            }

            RegisterInfo regArg = bctx.ensureRegister(code, m_nArgCond);
            if (regArg.type().isNullable()) {
                // the type being switched on is nullable, so check to see whether any of the
                // cases is Null. If so, build a null check to jump to that case's label, otherwise
                // build a null check to jump to the default case label.
                int   nThis     = getAddress();
                Label labelNull = bctx.ensureLabel(code, nThis + m_ofDefault);
                for (int iRow = 0; iRow < cRows; iRow++) {
                    Constant maybeNullConstant = bctx.getConstant(m_anConstCase[iRow]);
                    if (maybeNullConstant.getType().isOnlyNullable()) {
                        labelNull = bctx.ensureLabel(code, nThis + aofCase[iRow]);
                        break;
                    }
                }
                assert labelNull != null;
                Builder.checkNull(code, regArg, labelNull);
                // now we have already dealt with Null, we can narrow the argument register
                regArg = bctx.narrowRegister(code, regArg, regArg.type().removeNullable());
            }

            switch (constant.getType().getSingleUnderlyingClass(true).getName()) {
                case "Int8", "UInt8"    -> buildByteSwitch(bctx, code, regArg);
                case "Char"             -> buildCharSwitch(bctx, code, regArg);
                case "Int16",  "Int32",
                     "UInt16", "UInt32" -> buildIntSwitch(bctx, code, regArg);
                case "Int64", "UInt64"  -> buildLongSwitch(bctx, code, regArg);
                case "String"           -> buildStringSwitch(bctx, code, regArg);
                default -> {
                    if (constant instanceof EnumValueConstant) {
                        buildEnumSwitch(bctx, code, regArg);
                    } else {
                        buildIfLadder(bctx, code, regArg);
                    }
                }
            }
        }
        return -1;
    }

    private void buildByteSwitch(BuildContext bctx, CodeBuilder code, RegisterInfo regArg) {
        assert regArg.cd().isPrimitive() &&
               Builder.toTypeKind(regArg.cd()).slotSize() == 1;

        int[] aofCase = m_aofCase;
        int   cRows   = aofCase.length;
        int   nThis   = getAddress();
        int   iMin    = Integer.MAX_VALUE;
        int   iMax    = Integer.MIN_VALUE;

        Map<Integer, Label> mapCases = new TreeMap<>();
        for (int iRow = 0; iRow < cRows; iRow++) {
            Constant constant = bctx.getConstant(m_anConstCase[iRow]);
            Label    label    = bctx.ensureLabel(code, nThis + aofCase[iRow]);
            if (constant instanceof RangeConstant range) {
                int iFirst = ((ByteConstant) range.getEffectiveFirst()).getValue().intValue();
                int iLast = ((ByteConstant) range.getEffectiveLast()).getValue().intValue();

                iMin = Math.min(iMin, iFirst);
                iMax = Math.max(iMax, iLast);

                for (int iVal = iFirst; iVal <= iLast; iVal++) {
                    mapCases.putIfAbsent(iVal, label);
                }
            } else if (constant instanceof EnumValueConstant) {
                // must be the Null case, which we have already handled
                continue;
            } else {
                int iVal = ((ByteConstant) constant).getValue().intValue();

                iMin = Math.min(iMin, iVal);
                iMax = Math.max(iMax, iVal);

                mapCases.putIfAbsent(iVal, label);
            }
        }

        Label labelDflt = bctx.ensureLabel(code, nThis + m_ofDefault);
        regArg.load(code);
        code.tableswitch(iMin, iMax, labelDflt, toSwitchCases(mapCases));
    }

    private void buildCharSwitch(BuildContext bctx, CodeBuilder code, RegisterInfo regArg) {
        // a primitive Char is an int, so we can just build an int switch
        buildIntSwitch(bctx, code, regArg);
    }

    private void buildLongSwitch(BuildContext bctx, CodeBuilder code, RegisterInfo regArg) {
        assert regArg.cd().descriptorString().equals("J");

        int[] aofCase = m_aofCase;
        int   cRows   = aofCase.length;
        long  lMin    = Long.MAX_VALUE;
        long  lMax    = Long.MIN_VALUE;
        int   cCases  = cRows;
        int   cSpread = 0;

        Constant[] aConst = new Constant[cRows];
        for (int iRow = 0; iRow < cRows; iRow++) {
            Constant constant = aConst[iRow] = bctx.getConstant(m_anConstCase[iRow]);
            if (constant instanceof RangeConstant range) {
                long lFirst = range.getEffectiveFirst().getIntValue().getLong();
                long lLast  = range.getEffectiveLast().getIntValue().getLong();

                lMin = Math.min(lMin, lFirst);
                lMax = Math.max(lMax, lLast);

                cCases += (int) (lLast - lFirst); // no need for "+1" since we already counted it
            } else if (constant instanceof EnumValueConstant) {
                // must be the Null case, which we have already handled
                continue;
            } else {
                long lVal = constant.getIntValue().getLong();

                lMin = Math.min(lMin, lVal);
                lMax = Math.max(lMax, lVal);
            }
        }

        Plan plan;
        if (lMax - lMin > Integer.MAX_VALUE) {
            // JVM's lookupswitch and tableswitch instructions only accept int operands
            plan = Plan.IfLadder;
        } else {
            cSpread = (int) (lMax - lMin);

            int nDensity = cSpread / cCases;
            if (cSpread > 256 || (cSpread > 32 && nDensity < 4)) {
                plan = Plan.LookupSwitch;
            } else {
                plan = Plan.TableSwitch;
            }
        }

        int   nThis     = getAddress();
        Label labelDflt = bctx.ensureLabel(code, nThis + m_ofDefault);
        switch (plan) {
        case TableSwitch, LookupSwitch:
            regArg.load(code);
            code.ldc(lMin)
                .lsub()
                .l2i(); // (int) (lArg - lMin);

            Map<Integer, Label> mapCases = new TreeMap<>();
            for (int iRow = 0; iRow < cRows; iRow++) {
                Constant constant = aConst[iRow];
                Label    label    = bctx.ensureLabel(code, nThis + aofCase[iRow]);
                if (constant instanceof RangeConstant range) {
                    long  lFirst = range.getEffectiveFirst().getIntValue().getLong();
                    long  lLast  = range.getEffectiveLast().getIntValue().getLong();

                    for (long lVal = lFirst; lVal <= lLast; lVal++) {
                        int ix = (int) (lVal - lMin);
                        mapCases.putIfAbsent(ix, label);
                    }
                } else {
                    long lVal = constant.getIntValue().getLong();
                    int  ix   = (int) (lVal - lMin);
                    mapCases.putIfAbsent(ix, label);
                }
            }
            List<SwitchCase> listCases = toSwitchCases(mapCases);
            if (plan == Plan.TableSwitch) {
                code.tableswitch(0, cSpread, labelDflt, listCases);
            } else {
                code.lookupswitch(labelDflt, listCases);
            }
        break;

        case IfLadder:
            int nSlotArg = regArg.slot();
            for (int iRow = 0; iRow < cRows; iRow++) {
                code.lload(nSlotArg);

                Constant constant = aConst[iRow];
                Label    label    = bctx.ensureLabel(code, nThis + aofCase[iRow]);
                if (constant instanceof RangeConstant range) {
                    long  lFirst  = range.getEffectiveFirst().getIntValue().getLong();
                    long  lLast   = range.getEffectiveLast().getIntValue().getLong();
                    Label lblNext = code.newLabel();
                    code.ldc(lFirst)
                        .lcmp()
                        .iflt(lblNext)
                        .lload(nSlotArg)
                        .ldc(lLast)
                        .lcmp()
                        .ifle(label)
                        .labelBinding(lblNext);
                } else {
                    code.ldc(constant.getIntValue().getLong())
                        .lcmp()
                        .ifeq(label);
                }
            }
        }
        code.goto_(labelDflt);
    }

    private void buildIntSwitch(BuildContext bctx, CodeBuilder code, RegisterInfo regArg) {
        assert regArg.cd().descriptorString().equals("I");

        int[] aofCase = m_aofCase;
        int   cRows   = aofCase.length;
        int   nMin    = Integer.MAX_VALUE;
        int   nMax    = Integer.MIN_VALUE;
        int   cCases  = cRows;
        int   cSpread = 0;

        Constant[] aConst = new Constant[cRows];
        for (int iRow = 0; iRow < cRows; iRow++) {
            Constant constant = aConst[iRow] = bctx.getConstant(m_anConstCase[iRow]);
            if (constant instanceof RangeConstant range) {
                int lFirst = range.getEffectiveFirst().getIntValue().getInt();
                int lLast  = range.getEffectiveLast().getIntValue().getInt();

                nMin = Math.min(nMin, lFirst);
                nMax = Math.max(nMax, lLast);

                cCases += (lLast - lFirst); // no need for "+1" since we already counted it
            } else if (constant instanceof EnumValueConstant) {
                // must be the Null case, which we have already handled
                continue;
            } else {
                int nVal = constant.getIntValue().getInt();
                nMin = Math.min(nMin, nVal);
                nMax = Math.max(nMax, nVal);
            }
        }

        Plan plan;
        cSpread = nMax - nMin;

        int nDensity = cSpread / cCases;
        if (cSpread > 256 || (cSpread > 32 && nDensity < 4)) {
            plan = Plan.LookupSwitch;
        } else {
            plan = Plan.TableSwitch;
        }

        int   nThis     = getAddress();
        Label labelDflt = bctx.ensureLabel(code, nThis + m_ofDefault);

        regArg.load(code);
        code.ldc(nMin)
            .isub(); // (lArg - lMin);

        Map<Integer, Label> mapCases = new TreeMap<>();
        for (int iRow = 0; iRow < cRows; iRow++) {
            Constant constant = aConst[iRow];
            Label    label    = bctx.ensureLabel(code, nThis + aofCase[iRow]);
            if (constant instanceof RangeConstant range) {
                int nFirst = range.getEffectiveFirst().getIntValue().getInt();
                int nLast  = range.getEffectiveLast().getIntValue().getInt();

                for (int nVal = nFirst; nVal <= nLast; nVal++) {
                    int ix = nVal - nMin;
                    mapCases.putIfAbsent(ix, label);
                }
            } else {
                int nVal = constant.getIntValue().getInt();
                int ix   = nVal - nMin;
                mapCases.putIfAbsent(ix, label);
            }
        }
        List<SwitchCase> listCases = toSwitchCases(mapCases);

        if (plan == Plan.TableSwitch) {
            code.tableswitch(0, cSpread, labelDflt, listCases);
        } else {
            code.lookupswitch(labelDflt, listCases);
        }

        code.goto_(labelDflt);
    }

    private void buildStringSwitch(BuildContext bctx, CodeBuilder code, RegisterInfo regArg) {
        // TODO: optimize
        buildIfLadder(bctx, code, regArg);
    }

    private void buildEnumSwitch(BuildContext bctx, CodeBuilder code, RegisterInfo regArg) {
        assert regArg.type().isEnum();

        int[] aofCase = m_aofCase;
        int   cRows   = aofCase.length;
        int   nThis   = getAddress();
        int   iMin    = Integer.MAX_VALUE;
        int   iMax    = Integer.MIN_VALUE;

        Map<Integer, Label> mapCases = new TreeMap<>();
        for (int iRow = 0; iRow < cRows; iRow++) {
            Constant constant = bctx.getConstant(m_anConstCase[iRow]);
            Label    label    = bctx.ensureLabel(code, nThis + aofCase[iRow]);

            if (constant.getType().isOnlyNullable()) {
                // the Null case has already been handled before narrowing the argument register
                continue;
            }

            if (constant instanceof RangeConstant range) {
                int iFirst = ((EnumValueConstant) range.getEffectiveFirst()).getPresumedOrdinal();
                int iLast  = ((EnumValueConstant) range.getEffectiveLast()).getPresumedOrdinal();

                iMin = Math.min(iMin, iFirst);
                iMax = Math.max(iMax, iLast);

                for (int iVal = iFirst; iVal <= iLast; iVal++) {
                    mapCases.putIfAbsent(iVal, label);
                }
            } else {
                int iVal = ((EnumValueConstant) constant).getPresumedOrdinal();

                iMin = Math.min(iMin, iVal);
                iMax = Math.max(iMax, iVal);

                mapCases.putIfAbsent(iVal, label);
            }
        }

        // enumValue -> enumValue.ordinal;
        regArg.load(code);
        bctx.loadCtx(code);
        code.invokevirtual(regArg.cd(), "ordinal$get$p", MethodTypeDesc.of(CD_long, CD_Ctx))
            .l2i();

        Label labelDflt = bctx.ensureLabel(code, nThis + m_ofDefault);
        code.tableswitch(iMin, iMax, labelDflt, toSwitchCases(mapCases));
    }

    /**
     * Convert switch cases to the JVM-required key order. Ecstasy cases may overlap; the first
     * matching case wins.
     */
    private static List<SwitchCase> toSwitchCases(Map<Integer, Label> mapCases) {
        List<SwitchCase> listCases = new ArrayList<>(mapCases.size());
        for (Map.Entry<Integer, Label> entry : mapCases.entrySet()) {
            listCases.add(SwitchCase.of(entry.getKey(), entry.getValue()));
        }
        return listCases;
    }

    enum Plan {TableSwitch, LookupSwitch, IfLadder}

    // ----- fields --------------------------------------------------------------------------------

    protected int      m_nArgCond;
    private   Argument m_argCond;

    /**
     * Owner-local first-execution switch table. These handles and type constants come from a
     * concrete frame/container, so they must not be stored on the shared decoded op.
     */
    protected record SwitchCache(ObjectHandle[] cases, Map<ObjectHandle, Integer> jumpMap,
                                  Algorithm algorithm, TypeConstant conditionType,
                                  List<RangeMatch> ranges) {}

    private record RangeMatch(ObjectHandle lower, ObjectHandle upper, int encodedIndex) {}

    private enum CacheCategory {SWITCH}

    private static final int EXCLUDE_MASK = 0xC000_0000;
    private static final int LO_EX        = 0x8000_0000;
    private static final int HI_EX        = 0x4000_0000;
}
