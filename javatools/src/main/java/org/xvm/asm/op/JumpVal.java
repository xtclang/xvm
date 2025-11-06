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

import org.xvm.asm.Argument;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.Op;

import org.xvm.asm.constants.ByteConstant;
import org.xvm.asm.constants.CharConstant;
import org.xvm.asm.constants.EnumValueConstant;
import org.xvm.asm.constants.IntConstant;
import org.xvm.asm.constants.RangeConstant;
import org.xvm.asm.constants.StringConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.javajit.BuildContext;
import org.xvm.javajit.Builder;
import org.xvm.javajit.RegisterInfo;

import org.xvm.runtime.ConstHeap;
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

    protected int ensureJumpMap(Frame frame, int iPC, ObjectHandle hValue) {
        return m_algorithm == null
                ? explodeConstants(frame, iPC, hValue)
                : complete(frame, iPC, hValue);
    }

    protected int explodeConstants(Frame frame, int iPC, ObjectHandle hValue) {
        ObjectHandle[] ahCase = new ObjectHandle[m_aofCase.length];
        for (int iRow = 0, cRows = m_aofCase.length; iRow < cRows; iRow++) {
            ahCase[iRow] = frame.getConstHandle(m_anConstCase[iRow]);
        }

        if (Op.anyDeferred(ahCase)) {
            Frame.Continuation stepNext = frameCaller -> {
                buildJumpMap(frameCaller, ahCase);
                return complete(frameCaller, iPC, hValue);
            };
            return new Utils.GetArguments(ahCase, stepNext).doNext(frame);
        }

        buildJumpMap(frame, ahCase);
        return complete(frame, iPC, hValue);
    }

    protected int complete(Frame frame, int iPC, ObjectHandle hValue) {
        Map<ObjectHandle, Integer> mapJump = m_mapJump;
        Integer Index;

        switch (m_algorithm) {
        case NativeSimple:
            Index = mapJump.get(hValue);
            break;

        case NativeRange: {
            // check the exact match first
            Index = mapJump.get(hValue);

            if (!hValue.isNativeEqual()) {
                break; // REVIEW: should we assert instead?
            }
            List<Object[]> listRanges = m_listRanges;
            for (Object[] ao : listRanges) {
                int index = (Integer) ao[2];
                boolean fLoEx = (index & LO_EX) != 0;
                boolean fHiEx = (index & HI_EX) != 0;

                index &= ~EXCLUDE_MASK;

                // we only need to compare the range if there is a chance that it can impact
                // the result (the range case precedes the exact match case)
                if (Index == null || Index.intValue() > index) {
                    ObjectHandle hLow  = (ObjectHandle) ao[0];
                    ObjectHandle hHigh = (ObjectHandle) ao[1];

                    int nCmpLo = hValue.compareTo(hLow);
                    int nCmpHi = hValue.compareTo(hHigh);

                    if ((fLoEx ? nCmpLo > 0 : nCmpLo >= 0) &&
                        (fHiEx ? nCmpHi < 0 : nCmpHi <= 0)) {
                        return jump(frame, iPC + m_aofCase[index], m_acExits[index]);
                    }
                }
            }
            break;
        }

        default:
            return findNatural(frame, iPC, hValue, 0);
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
    protected int findNatural(Frame frame, int iPC, ObjectHandle hValue, int iCase) {
        ObjectHandle[] ahCase = m_ahCase;
        int            cCases = ahCase.length;

        for (; iCase < cCases; iCase++) {
            ObjectHandle hCase    = ahCase[iCase];
            int          iCurrent = iCase; // effectively final

            switch (m_algorithm) {
            case NaturalRange: {
                if (hCase.getType().isA(frame.poolContext().typeRange())) {
                    GenericHandle hRange = (GenericHandle) hCase;
                    ObjectHandle  hLo    = hRange.getField(null, "lowerBound");
                    ObjectHandle  hHi    = hRange.getField(null, "upperBound");
                    BooleanHandle hLoEx  = (BooleanHandle) hRange.getField(null, "lowerExclusive");
                    BooleanHandle hHiEx  = (BooleanHandle) hRange.getField(null, "upperExclusive");

                    Frame.Continuation stepNext =
                        frameCaller -> findNatural(frameCaller, iPC, hValue, iCurrent + 1);

                    switch (checkRange(frame, m_typeCond, hValue, hLo, hHi,
                                hLoEx.get(), hHiEx.get(), true, stepNext)) {
                    case Op.R_NEXT:
                        if (frame.popStack() == xBoolean.TRUE) {
                            // it's a match
                            return jump(frame, iPC + m_aofCase[iCase], m_acExits[iCase]);
                        }
                        continue;

                    case Op.R_CALL:
                        frame.m_frameNext.addContinuation(frameCaller ->
                            frameCaller.popStack() == xBoolean.TRUE
                                ? jump(frameCaller, iPC + m_aofCase[iCurrent], m_acExits[iCurrent])
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

            case NaturalSimple: {
                switch (m_typeCond.callEquals(frame, hValue, hCase, Op.A_STACK)) {
                case Op.R_NEXT:
                    if (frame.popStack() == xBoolean.TRUE) {
                        // it's a match
                        return jump(frame, iPC + m_aofCase[iCase], m_acExits[iCase]);
                    }
                    continue;

                case Op.R_CALL:
                    frame.m_frameNext.addContinuation(frameCaller ->
                        frameCaller.popStack() == xBoolean.TRUE
                            ? jump(frameCaller, iPC + m_aofCase[iCurrent], m_acExits[iCurrent])
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
        return jump(frame, iPC + m_ofDefault, m_cDefaultExits);
    }

    /**
     * This method is synchronized because it needs to update three different values atomically.
     */
    private synchronized void buildJumpMap(Frame frame, ObjectHandle[] ahCase) {
        if (m_algorithm != null) {
            // the jump map was built concurrently
            return;
        }

        int                        cCases  = ahCase.length;
        Map<ObjectHandle, Integer> mapJump = new HashMap<>(cCases);

        Algorithm    algorithm  = Algorithm.NativeSimple;
        TypeConstant typeCond   = frame.getLocalType(m_nArgCond, null);
        TypeConstant typeRange  = frame.poolContext().typeRange();
        ConstHeap    heap       = frame.f_context.f_container.f_heap;
        ConstantPool poolTarget = frame.f_function.getConstantPool();

        for (int iCase = 0; iCase < cCases; iCase++ ) {
            ObjectHandle hCase = ahCase[iCase];

            assert !hCase.isMutable();

            // caching a constant linked to the current pool would "leak" the current container
            if (hCase.getComposition().getConstantPool() != poolTarget) {
                hCase = heap.relocateConst(hCase, frame.getConstant(m_anConstCase[iCase]));

                assert hCase != null;
                ahCase[iCase] = hCase;
            }

            TypeConstant typeCase = hCase.getType();
            boolean      fRange   = typeCase.isA(typeRange) && !typeCond.isA(typeRange);

            if (algorithm.isNative()) {
                if (hCase.isNativeEqual()) {
                    mapJump.put(hCase, Integer.valueOf(iCase));
                } else if (fRange) {
                    if (addRange((GenericHandle) hCase, iCase)) {
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

                    addRange((GenericHandle) hCase, iCase);
                } else {
                    algorithm = algorithm.worstOf(Algorithm.NaturalSimple);

                    mapJump.put(hCase, Integer.valueOf(iCase));
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
    private boolean addRange(GenericHandle hRange, int index) {
        ObjectHandle  hLo   = hRange.getField(null, "lowerBound");
        ObjectHandle  hHi   = hRange.getField(null, "upperBound");
        BooleanHandle hLoEx = (BooleanHandle) hRange.getField(null, "lowerExclusive");
        BooleanHandle hHiEx = (BooleanHandle) hRange.getField(null, "upperExclusive");

        // TODO: if the range is small and sequential (an interval), replace it with the exact hits for native values
        List<Object[]> list = m_listRanges;
        if (list == null) {
            list = m_listRanges = new ArrayList<>();
        }

        assert (index & EXCLUDE_MASK) == 0;
        if (hLoEx.get()) {
            index |= LO_EX;
        }
        if (hHiEx.get()) {
            index |= HI_EX;
        }

        list.add(new Object[]{hLo, hHi, Integer.valueOf(index)});
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
    public void build(BuildContext bctx, CodeBuilder code) {
        RegisterInfo regArg = bctx.loadArgument(code, m_nArgCond);

        int[] aofCase = m_aofCase;
        int   cRows   = aofCase.length;
        assert cRows > 0;

        // retrieve the base type
        Constant constant = bctx.getConstant(m_anConstCase[0]);
        if (constant instanceof RangeConstant range) {
            constant = range.getFirst();
        }

        switch (constant) {
        case ByteConstant _       -> buildByteSwitch(bctx, code, regArg);
        case CharConstant _       -> buildCharSwitch(bctx, code, regArg);
        case IntConstant _        -> buildLongSwitch(bctx, code, regArg);
        case StringConstant _     -> buildStringSwitch(bctx, code, regArg);
        case EnumValueConstant _  -> buildEnumSwitch(bctx, code, regArg);
        default                   -> buildIfLadder(bctx, code, regArg);
        }
    }

    private void buildByteSwitch(BuildContext bctx, CodeBuilder code, RegisterInfo regArg) {
        assert regArg.cd().isPrimitive() &&
               Builder.toTypeKind(regArg.cd()).slotSize() == 1;

        int[] aofCase = m_aofCase;
        int   cRows   = aofCase.length;
        int   nThis   = getAddress();
        int   iMin    = Integer.MAX_VALUE;
        int   iMax    = Integer.MIN_VALUE;

        List<SwitchCase> listCases = new ArrayList<>();
        for (int iRow = 0; iRow < cRows; iRow++) {
            Constant constant = bctx.getConstant(m_anConstCase[iRow]);
            Label    label    = bctx.ensureLabel(code, nThis + aofCase[iRow]);
            if (constant instanceof RangeConstant range) {
                int iFirst = ((ByteConstant) range.getEffectiveFirst()).getValue().intValue();
                int iLast  = ((ByteConstant) range.getEffectiveLast()).getValue().intValue();

                iMin = Math.min(iMin, iFirst);
                iMax = Math.max(iMax, iLast);

                for (int iVal = iFirst; iVal <= iLast; iVal++) {
                    listCases.add(SwitchCase.of(iVal, label));
                }
            } else {
                int iVal = ((ByteConstant) constant).getValue().intValue();

                iMin = Math.min(iMin, iVal);
                iMax = Math.max(iMax, iVal);

                listCases.add(SwitchCase.of(iVal, label));
            }
        }

        Label labelDflt = bctx.ensureLabel(code, nThis + m_ofDefault);
        code.tableswitch(iMin, iMax, labelDflt, listCases);
    }

    private void buildCharSwitch(BuildContext bctx, CodeBuilder code, RegisterInfo regArg) {
        throw new UnsupportedOperationException();
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
            code.ldc(lMin)
                .lsub()
                .l2i(); // (int) (lArg - lMin);

            List<SwitchCase> listCases = new ArrayList<>();
            for (int iRow = 0; iRow < cRows; iRow++) {
                Constant constant = aConst[iRow];
                Label    label    = bctx.ensureLabel(code, nThis + aofCase[iRow]);
                if (constant instanceof RangeConstant range) {
                    long  lFirst = range.getEffectiveFirst().getIntValue().getLong();
                    long  lLast  = range.getEffectiveLast().getIntValue().getLong();

                    for (long lVal = lFirst; lVal <= lLast; lVal++) {
                        int ix = (int) (lVal - lMin);
                        listCases.add(SwitchCase.of(ix, label));
                    }
                } else {
                    long lVal = constant.getIntValue().getLong();
                    int  ix   = (int) (lVal - lMin);
                    listCases.add(SwitchCase.of(ix, label));
                }
            }
            if (plan == Plan.TableSwitch) {
                code.tableswitch(0, cSpread, labelDflt, listCases);
            } else {
                code.lookupswitch(labelDflt, listCases);
            }
        break;

        case IfLadder:
            int nSlotArg = regArg.slot();
            for (int iRow = 0; iRow < cRows; iRow++) {
                if (iRow > 0) {
                    // copy the argument for the next cycle
                    code.lload(nSlotArg);
                }

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

        List<SwitchCase> listCases = new ArrayList<>();
        for (int iRow = 0; iRow < cRows; iRow++) {
            Constant constant = bctx.getConstant(m_anConstCase[iRow]);
            Label    label    = bctx.ensureLabel(code, nThis + aofCase[iRow]);
            if (constant instanceof RangeConstant range) {
                int iFirst = ((EnumValueConstant) range.getEffectiveFirst()).getPresumedOrdinal();
                int iLast  = ((EnumValueConstant) range.getEffectiveLast()).getPresumedOrdinal();

                iMin = Math.min(iMin, iFirst);
                iMax = Math.max(iMax, iLast);

                for (int iVal = iFirst; iVal <= iLast; iVal++) {
                    listCases.add(SwitchCase.of(iVal, label));
                }
            } else {
                int iVal = ((EnumValueConstant) constant).getPresumedOrdinal();

                iMin = Math.min(iMin, iVal);
                iMax = Math.max(iMax, iVal);

                listCases.add(SwitchCase.of(iVal, label));
            }
        }

        // enumValue -> enumValue.ordinal;
        bctx.loadCtx(code);
        code.invokevirtual(regArg.cd(), "ordinal$get$p", MethodTypeDesc.of(CD_long, CD_Ctx))
            .l2i();

        Label labelDflt = bctx.ensureLabel(code, nThis + m_ofDefault);
        code.tableswitch(iMin, iMax, labelDflt, listCases);
    }

    private void buildIfLadder(BuildContext bctx, CodeBuilder code, RegisterInfo regArg) {
        throw new UnsupportedOperationException();
    }

    enum Plan {TableSwitch, LookupSwitch, IfLadder}

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

    private static final int EXCLUDE_MASK = 0xC000_0000;
    private static final int LO_EX        = 0x8000_0000;
    private static final int HI_EX        = 0x4000_0000;
}