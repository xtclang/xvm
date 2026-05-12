package org.xvm.asm.op;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.lang.classfile.CodeBuilder;
import java.lang.classfile.Label;

import java.util.List;

import org.xvm.asm.Argument;
import org.xvm.asm.Constant;
import org.xvm.asm.Op;
import org.xvm.asm.OpJump;
import org.xvm.asm.Register;

import org.xvm.asm.constants.ArrayConstant;
import org.xvm.asm.constants.MatchAnyConstant;
import org.xvm.asm.constants.ParameterizedTypeConstant;
import org.xvm.asm.constants.RangeConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.javajit.BuildContext;
import org.xvm.javajit.Builder;
import org.xvm.javajit.RegisterInfo;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.template.xBoolean;
import org.xvm.runtime.template.xOrdered;

import static org.xvm.javajit.Builder.CD_TypeConstant;
import static org.xvm.javajit.Builder.CD_nObj;
import static org.xvm.javajit.Builder.MD_TypeIsA;
import static org.xvm.javajit.Builder.MD_xvmType;

import static org.xvm.util.Handy.readMagnitude;
import static org.xvm.util.Handy.readPackedInt;
import static org.xvm.util.Handy.writePackedLong;

/**
 * Base class for switch (JmpVal_*) op-codes.
 */
public abstract class OpSwitch
        extends Op {
    /**
     * Construct an op.
     *
     * @param aConstCase  an array of "case" values (constants)
     * @param aOpCase     an array of Ops to jump to
     * @param opDefault   an Op to jump to in the "default" case
     */
    protected OpSwitch(Constant[] aConstCase, Op[] aOpCase, Op opDefault) {
        assert aOpCase != null;

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
    protected OpSwitch(DataInput in, Constant[] aconst)
            throws IOException {
        int   cCases    = readMagnitude(in);
        int[] anArgCase = new int[cCases];
        int[] aofCase   = new int[cCases];
        for (int i = 0; i < cCases; ++i) {
            anArgCase[i] = readPackedInt(in);
            aofCase  [i] = readPackedInt(in);
        }
        m_anConstCase = anArgCase;
        m_aofCase     = aofCase;

        m_ofDefault = readPackedInt(in);
    }

    @Override
    public void write(DataOutput out, ConstantRegistry registry)
            throws IOException {
        super.write(out, registry);

        if (m_aConstCase != null) {
            m_anConstCase = encodeArguments(m_aConstCase, registry);
        }

        int[] anArgCase = m_anConstCase;
        int[] aofCase   = m_aofCase;
        int   c         = anArgCase.length;

        writePackedLong(out, c);
        for (int i = 0; i < c; ++i) {
            writePackedLong(out, anArgCase[i]);
            writePackedLong(out, aofCase  [i]);
        }

        writePackedLong(out, m_ofDefault);
    }

    @Override
    public void resolveAddresses(Op[] aop) {
        int cCases;
        if (m_aOpCase == null) {
            int ofThis = getAddress();

            cCases    = m_aofCase.length;
            m_aOpCase = new Op[cCases];
            for (int i = 0; i < cCases; i++) {
                int ofOp = adjustRelativeAddress(aop, m_aofCase[i]);
                m_aofCase[i] = ofOp;
                m_aOpCase[i] = aop[ofThis + ofOp];
            }
            int ofOp = adjustRelativeAddress(aop, m_ofDefault);
            m_ofDefault = ofOp;
            m_opDefault = aop[ofThis + ofOp];
        } else {
            cCases    = m_aOpCase.length;
            m_aofCase = new int[cCases];
            for (int i = 0; i < cCases; i++) {
                m_aofCase[i] = calcRelativeAddress(m_aOpCase[i]);
            }
            m_ofDefault = calcRelativeAddress(m_opDefault);
        }

        m_acExits = new int[cCases];
        for (int i = 0; i < cCases; i++) {
            m_acExits[i] = calcExits(m_aOpCase[i]);
        }
        m_cDefaultExits = calcExits(m_opDefault);
    }

    @Override
    public void markReachable(Op[] aop) {
        super.markReachable(aop);

        Op[]  aOpCase = m_aOpCase;
        int[] aofCase = m_aofCase;
        for (int i = 0, c = aofCase.length; i < c; ++i) {
            aOpCase[i] = findDestinationOp(aop, aofCase[i]);
            aofCase[i] = calcRelativeAddress(aOpCase[i]);
        }

        m_opDefault = findDestinationOp(aop, m_ofDefault);
        m_ofDefault = calcRelativeAddress(m_opDefault);
    }

    @Override
    public boolean branches(Op[] aop, List<Integer> list) {
        resolveAddresses(aop);
        for (int i : m_aofCase) {
            list.add(i);
        }
        list.add(m_ofDefault);
        return true;
    }

    @Override
    public boolean advances() {
        return false;
    }

    @Override
    public void registerConstants(ConstantRegistry registry) {
        registerArguments(m_aConstCase, registry);
    }

    /**
     * Make natural calls to determine if the specified value sits in the range (inclusive) between
     * the specified low and high values. The resulting Boolean value should be placed on the
     * caller's stack.
     *
     * @param fLow  if true, check the low value, otherwise the high value
     *
     * @return one of Op.R_NEXT, Op.R_CALL or Op.R_EXCEPTION values
     */
    protected int checkRange(Frame frame, TypeConstant typeCompare, ObjectHandle hValue,
                             ObjectHandle hLo, ObjectHandle hHi,
                             boolean fLoEx,    boolean      fHiEx,
                             boolean fLow, Frame.Continuation continuation) {
        switch (typeCompare.callCompare(frame, hValue, fLow ? hLo : hHi, Op.A_STACK)) {
        case Op.R_NEXT:
            ObjectHandle hResult = frame.popStack();
            if (fLow) {
                boolean fMatch = fLoEx
                                    ? hResult == xOrdered.GREATER
                                    : hResult != xOrdered.LESSER;   // GREATER or EQUAL
                if (!fMatch) {
                    // we're done; no match
                    frame.pushStack(xBoolean.FALSE);
                    return continuation.proceed(frame);
                }

                return checkRange(frame, typeCompare, hValue,
                        hLo, hHi, fLoEx, fHiEx, false, continuation);
            } else {
                boolean fMatch = fHiEx
                                    ? hResult == xOrdered.LESSER
                                    : hResult != xOrdered.GREATER;  // LESSER or EQUAL
                frame.pushStack(xBoolean.makeHandle(fMatch));
                return continuation.proceed(frame);
            }

        case Op.R_CALL:
            Frame.Continuation stepNext = frameCaller -> {
                ObjectHandle hR = frameCaller.popStack();
                if (fLow) {
                    boolean fMatch = fLoEx
                                        ? hR == xOrdered.GREATER
                                        : hR != xOrdered.LESSER;   // GREATER or EQUAL
                    if (!fMatch) {
                        // we're done; no match
                        frameCaller.pushStack(xBoolean.FALSE);
                        return continuation.proceed(frameCaller);
                    }

                    return checkRange(frameCaller, typeCompare, hValue,
                            hLo, hHi, fLoEx, fHiEx, false, continuation);
                } else {
                    boolean fMatch = fHiEx
                                        ? hR == xOrdered.LESSER
                                        : hR != xOrdered.GREATER;  // LESSER or EQUAL
                    frameCaller.pushStack(xBoolean.makeHandle(fMatch));
                    return continuation.proceed(frameCaller);
                }
            };
            frame.m_frameNext.addContinuation(stepNext);
            return Op.R_CALL;

        case Op.R_EXCEPTION:
            return Op.R_EXCEPTION;

        default:
            throw new IllegalStateException();
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        sb.append(super.toString())
          .append(' ');

        appendArgDescription(sb);

        int cOps     = m_aOpCase == null ? 0 : m_aOpCase.length;
        int cOffsets = m_aofCase == null ? 0 : m_aofCase.length;
        int cLabels  = Math.max(cOps, cOffsets);

        sb.append(cLabels)
          .append(":\n");

        int cConstCases  = m_aConstCase  == null ? 0 : m_aConstCase.length;
        int cNConstCases = m_anConstCase == null ? 0 : m_anConstCase.length;
        assert Math.max(cConstCases, cNConstCases) == cLabels;

        for (int i = 0; i < cLabels; ++i) {
            Constant arg  = i < cConstCases  ? m_aConstCase [i] : null;
            int      nArg = i < cNConstCases ? m_anConstCase[i] : Register.UNKNOWN;
            Op       op   = i < cOps         ? m_aOpCase    [i] : null;
            int      of   = i < cOffsets     ? m_aofCase    [i] : 0;

            if (i > 0) {
                sb.append(",\n");
            }

            sb.append(Argument.toIdString(arg, nArg))
                    .append(": ")
                    .append(OpJump.getLabelDesc(op, of));
        }

        sb.append("\ndefault: ")
                .append(OpJump.getLabelDesc(m_opDefault, m_ofDefault));

        return sb.toString();
    }

    protected abstract void appendArgDescription(StringBuilder sb);


    // ----- JIT support ---------------------------------------------------------------------------

    @Override
    public void computeTypes(BuildContext bctx) {
        int nAddrThis = getAddress();
        for (int ofCase : m_aofCase) {
            bctx.typeMatrix.follow(nAddrThis, nAddrThis + ofCase, -1);
        }
        bctx.typeMatrix.follow(nAddrThis, nAddrThis + m_ofDefault, -1);
    }

    // ----- JIT support ---------------------------------------------------------------------------

    protected void buildIfLadder(BuildContext bctx, CodeBuilder code, RegisterInfo... regArgs) {
        assert regArgs.length != 0;

        int[] aofCase   = m_aofCase;
        int   cRows     = aofCase.length;
        int   nThis     = getAddress();
        Label labelDflt = bctx.ensureLabel(code, nThis + m_ofDefault);
        Label rowLabel  = code.newLabel();

        for (int iRow = 0; iRow < cRows; iRow++) {
            Label    label    = bctx.ensureLabel(code, nThis + aofCase[iRow]);
            Constant constant = bctx.getConstant(m_anConstCase[iRow]);
            if (constant instanceof ArrayConstant array) {
                Constant[] caseConsts = array.getValue();
                assert caseConsts.length == regArgs.length;

                Label colLabel = code.newLabel();
                int   numCases = caseConsts.length - 1;
                for (int column = 0; column <= numCases; column++) {
                    if (caseConsts[column] instanceof MatchAnyConstant) {
                        if (column == numCases) {
                            code.goto_(label);
                        }
                        continue;
                    }
                    Label jmp = column == numCases ? label : colLabel;
                    buildSwitchCase(bctx, code, regArgs[column], caseConsts[column], jmp);
                    code.goto_(rowLabel)
                        .labelBinding(colLabel);
                    colLabel = code.newLabel();
                }
                code.labelBinding(rowLabel);
                rowLabel = code.newLabel();
            } else {
                assert regArgs.length == 1;
                buildSwitchCase(bctx, code, regArgs[0], constant, label);
            }
        }
        code.goto_(labelDflt);
    }

    /**
     * Build the check for a specific case
     *
     * @param bctx     the current build context
     * @param code      the CodeBuilder
     * @param reg       the register referencing the value being switched on
     * @param constant  the constant for this case test
     * @param label     the label to jump to if the case is matched
     */
    private static void buildSwitchCase(BuildContext bctx, CodeBuilder code, RegisterInfo reg,
                                        Constant constant, Label label) {
        TypeConstant type = reg.type();
        if (constant.getType().isOnlyNullable()) {
            // case argument is Null, reg must be a nullable type, so just build a Null check
            if (type.isNullable()) {
                // only build a null check if the type is nullable
                // the type may not be nullable if we already built the null check
                // (e.g. in JumpVal)
                Builder.pop(code, reg.cd());
                Builder.checkNull(code, reg, label);
            }
        } else {
            Label isNull = null;
            if (reg.type().isNullable()) {
                // the type is nullable, but this case is not Null, so we first need a Null check
                // then a narrow. If the value is Null, we will jump over the rest of this case
                // check as it will never match.
                Builder.pop(code, reg.cd());
                isNull = code.newLabel();
                Builder.checkNull(code, reg, isNull);
                // now narrow the register for the rest of the case check
                reg = bctx.narrowRegister(code, reg, type.removeNullable());
                reg.load(code);
            }

            switch (constant) {
                case RangeConstant range -> buildRangeCase(bctx, code, reg, range, label);
                case ParameterizedTypeConstant typeConst ->
                        buildTypeCase(bctx, code, reg, typeConst, label);
                default -> reg.type().buildCompare(bctx, code, OP_IS_EQ, reg, constant, label);
            }

            if (isNull != null) {
                // we did a null check so bind the label here
                code.labelBinding(isNull);
            }
        }
    }

    /**
     * Build the check for a range case: 1..10
     *
     * @param bctx   the current build context
     * @param code   the CodeBuilder
     * @param reg    the register referencing the value being switched on
     * @param range  the {@link RangeConstant} for this case test
     * @param label  the label to jump to if the case is matched
     */
    protected static void buildRangeCase(BuildContext bctx, CodeBuilder code, RegisterInfo reg,
            RangeConstant range, Label label) {

        TypeConstant type       = reg.type();
        Constant     first      = range.getFirst();
        Constant     last       = range.getLast();
        Label        rangeLabel = code.newLabel();
        int          nOpFirst   = range.isFirstExcluded() ? OP_IS_LTE : OP_IS_LT;
        int          nOpLast    = range.isFirstExcluded() ? OP_IS_LT : OP_IS_LTE;
        type.buildCompare(bctx, code, nOpFirst, reg, first, rangeLabel);
        type.buildCompare(bctx, code, nOpLast, reg, last, label);
        code.labelBinding(rangeLabel);
    }

    /**
     * Build the check for a type case: o.is(_)
     *
     * @param bctx       the current build context
     * @param code       the CodeBuilder
     * @param reg        the register referencing the value being switched on
     * @param typeConst  the {@link ParameterizedTypeConstant} for this case test
     * @param label      the label to jump to if the case is matched
     */
    protected static void buildTypeCase(BuildContext bctx, CodeBuilder code, RegisterInfo reg,
            TypeConstant typeConst, Label label) {

        assert typeConst.isTypeOfType();
        typeConst = typeConst.getParamType(0);

        TypeConstant type = reg.type();
        if (type.isJavaPrimitive()) {
            // we can statically compute the result
            if (type.isA(typeConst)) {
                code.ifne(label);
            }
        } else {
            reg.load(code);
            if (reg.type().isJitInterface()) {
                code.checkcast(CD_nObj);
            }
            bctx.loadCtx(code);
            code.invokevirtual(CD_nObj, "$xvmType", MD_xvmType);
            bctx.loadTypeConstant(code, typeConst);
            code.invokevirtual(CD_TypeConstant, "isA", MD_TypeIsA);
            code.ifne(label);
        }
    }

    // ----- fields and enums ----------------------------------------------------------------------

    protected int[] m_anConstCase;
    protected int[] m_aofCase;
    protected int   m_ofDefault;

    private Constant[] m_aConstCase;
    private Op[]       m_aOpCase;
    private Op         m_opDefault;

    protected transient int[] m_acExits;
    protected transient int   m_cDefaultExits;

    enum Algorithm {
        NativeSimple, NativeRange, NaturalSimple, NaturalRange;

        boolean isNative() {
            return switch (this) {
                case NativeSimple, NativeRange -> true;
                default -> false;
            };
        }

        Algorithm worstOf(Algorithm that) {
            return this.compareTo(that) <= 0 ? that : this;
        }
    }
}