package org.xvm.asm;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.lang.classfile.CodeBuilder;
import java.lang.classfile.Label;

import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;

import java.util.List;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.javajit.BuildContext;
import org.xvm.javajit.BuildContext.DoubleSlot;
import org.xvm.javajit.Builder;
import org.xvm.javajit.JitFlavor;
import org.xvm.javajit.RegisterInfo;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.ExceptionHandle;
import org.xvm.runtime.Utils;

import static java.lang.constant.ConstantDescs.CD_boolean;

import static org.xvm.javajit.Builder.CD_Container;
import static org.xvm.javajit.Builder.CD_Ctx;
import static org.xvm.javajit.Builder.CD_JavaString;
import static org.xvm.javajit.Builder.CD_TypeConstant;
import static org.xvm.javajit.Builder.CD_nType;
import static org.xvm.javajit.Builder.MD_TypeIsA;
import static org.xvm.javajit.Builder.MD_xvmType;

import static org.xvm.util.Handy.readPackedInt;
import static org.xvm.util.Handy.writePackedLong;


/**
 * Base class for conditional jump (JMP_*) op-codes.
 */
public abstract class OpCondJump
        extends Op {
    /**
     * Construct a unary conditional JMP_ op.
     *
     * @param arg  a value Argument
     * @param op   the op to jump to
     */
    protected OpCondJump(Argument arg, Op op) {
        assert !hasSecondArgument() && !isBinaryOp();
        m_argVal = arg;
        m_opDest = op;
    }

    /**
     * Construct a binary conditional JMP_ op.
     *
     * @param arg   a value Argument
     * @param arg2  a second value Argument
     * @param op    the op to jump to
     */
    protected OpCondJump(Argument arg, Argument arg2, Op op) {
        assert hasSecondArgument() && !isBinaryOp();
        m_argVal  = arg;
        m_argVal2 = arg2;
        m_opDest  = op;
    }

    /**
     * Construct a binary conditional JMP_ op.
     *
     * @param type  the compile-time type
     * @param arg   a value Argument
     * @param arg2  a second value Argument
     * @param op    the op to jump to
     */
    protected OpCondJump(TypeConstant type, Argument arg, Argument arg2, Op op) {
        assert isBinaryOp();
        m_typeCommon = type;
        m_argVal     = arg;
        m_argVal2    = arg2;
        m_opDest     = op;
    }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    protected OpCondJump(DataInput in, Constant[] aconst)
            throws IOException {
        if (isBinaryOp()) {
            m_nType = readPackedInt(in);
        }
        m_nArg = readPackedInt(in);
        if (hasSecondArgument()) {
            m_nArg2 = readPackedInt(in);
        }
        m_ofJmp = readPackedInt(in);
    }

    @Override
    public void write(DataOutput out, ConstantRegistry registry)
            throws IOException {
        super.write(out, registry);

        if (m_typeCommon != null) {
            m_nType = encodeArgument(m_typeCommon, registry);
            m_typeCommon = null;
        }
        if (m_argVal != null) {
            m_nArg = encodeArgument(m_argVal, registry);
        }
        if (m_argVal2 != null) {
            m_nArg2 = encodeArgument(m_argVal2, registry);
        }

        if (isBinaryOp()) {
            writePackedLong(out, m_nType);
        }
        writePackedLong(out, m_nArg);
        if (hasSecondArgument()) {
            writePackedLong(out, m_nArg2);
        }
        writePackedLong(out, m_ofJmp);
    }

    @Override
    public void resolveAddresses(Op[] aop) {
        if (m_opDest == null) {
            m_ofJmp  = adjustRelativeAddress(aop, m_ofJmp);
            m_opDest = aop[getAddress() + m_ofJmp];
        } else {
            m_ofJmp = calcRelativeAddress(m_opDest);
        }
        m_cExits = calcExits(m_opDest);
    }

    /**
     * @return true iff the op is a binary operator
     */
    protected boolean isBinaryOp() {
        return false;
    }

    /**
     * @return true iff the op has two arguments
     */
    protected boolean hasSecondArgument() {
        return isBinaryOp();
    }

    @Override
    public int process(Frame frame, int iPC) {
        return isBinaryOp() ? processBinaryOp(frame, iPC) : processUnaryOp(frame, iPC);
    }

    protected int processUnaryOp(Frame frame, int iPC) {
        try {
            ObjectHandle hValue = frame.getArgument(m_nArg);

            return isDeferred(hValue)
                    ? hValue.proceed(frame, frameCaller ->
                        completeUnaryOp(frameCaller, iPC, frameCaller.popStack()))
                    : completeUnaryOp(frame, iPC, hValue);
        } catch (ExceptionHandle.WrapperException e) {
            return frame.raiseException(e);
        }
    }

    protected int processBinaryOp(Frame frame, int iPC) {
        try {
            ObjectHandle[] ahArg      = frame.getArguments(new int[]{m_nArg, m_nArg2}, 2);
            TypeConstant   typeCommon = calculateCommonType(frame);

            if (anyDeferred(ahArg)) {
                Frame.Continuation stepNext = frameCaller ->
                    completeBinaryOp(frame, iPC, typeCommon, ahArg[0], ahArg[1]);

                return new Utils.GetArguments(ahArg, stepNext).doNext(frame);
            }

            return completeBinaryOp(frame, iPC, typeCommon, ahArg[0], ahArg[1]);
        } catch (ExceptionHandle.WrapperException e) {
            return frame.raiseException(e);
        }
    }

    protected TypeConstant calculateCommonType(Frame frame) {
        TypeConstant typeCommon = m_typeCommon;
        if (typeCommon == null) {
            m_typeCommon = typeCommon = (TypeConstant) frame.getConstant(m_nType);
        }
        return frame.resolveType(typeCommon);
    }

    /**
     * A completion of a unary op; must be overridden by all binary ops.
     */
    protected int completeUnaryOp(Frame frame, int iPC, ObjectHandle hValue) {
        throw new UnsupportedOperationException();
    }

    /**
     * A completion of a binary op; must be overridden by all binary ops.
     */
    protected int completeBinaryOp(Frame frame, int iPC, TypeConstant type,
                                   ObjectHandle hValue1, ObjectHandle hValue2) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void markReachable(Op[] aop) {
        super.markReachable(aop);

        m_opDest = findDestinationOp(aop, m_ofJmp);
        m_ofJmp  = calcRelativeAddress(m_opDest);
    }

    @Override
    public boolean checkRedundant(Op[] aop) {
        if (m_ofJmp == 1) {
            markRedundant();
            return true;
        }

        return false;
    }

    @Override
    public boolean branches(Op[] aop, List<Integer> list) {
        list.add(getRelativeAddress());
        return true;
    }

    /**
     * @return the number of instructions to jump (may be negative)
     */
    public int getRelativeAddress() {
        return m_ofJmp;
    }

    @Override
    public void registerConstants(ConstantRegistry registry) {
        if (isBinaryOp()) {
            m_typeCommon = (TypeConstant) registerArgument(m_typeCommon, registry);
        }
        m_argVal = registerArgument(m_argVal, registry);
        if (hasSecondArgument()) {
            m_argVal2 = registerArgument(m_argVal2, registry);
        }
    }

    /**
     * @return a String that identifies the argument, for debugging purposes
     */
    protected String getArgDesc() {
        return Argument.toIdString(m_argVal, m_nArg);
    }

    /**
     * @return a String that identifies the second argument, for debugging purposes
     */
    protected String getArg2Desc() {
        assert hasSecondArgument();
        return Argument.toIdString(m_argVal2, m_nArg2);
    }

    /**
     * @return a String to use for debugging to denote the destination of the jump
     */
    protected String getLabelDesc() {
        if (m_opDest instanceof org.xvm.asm.op.Label label) {
            return label.getName();
        } else if (m_ofJmp != 0) {
            return (m_ofJmp > 0 ? "+" : "") + m_ofJmp;
        } else if (m_opDest != null) {
            return "-> " + m_opDest;
        } else {
            return "???";
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(toName(getOpCode()))
          .append(' ');

        if (isBinaryOp()) {
           sb.append(Argument.toIdString(m_typeCommon, m_nType))
             .append(", ");
        }

        sb.append(getArgDesc());

        if (hasSecondArgument()) {
            sb.append(", ")
              .append(getArg2Desc());
        }

        sb.append(", ")
          .append(getLabelDesc());

        return sb.toString();
    }

    // ----- JIT support ---------------------------------------------------------------------------

    @Override
    public void build(BuildContext bctx, CodeBuilder code) {
        if (isBinaryOp()) {
            buildBinary(bctx, code);
        } else {
            buildUnary(bctx, code);
        }
    }

    protected void buildBinary(BuildContext bctx, CodeBuilder code) {
        // this is very similar to OpTest logic
        TypeConstant typeCmp  = bctx.getType(m_nType);
        RegisterInfo reg1     = bctx.ensureRegister(code, m_nArg);
        RegisterInfo reg2     = bctx.ensureRegister(code, m_nArg2);
        TypeConstant type1    = reg1.type();
        TypeConstant type2    = reg2.type();
        int          nOp      = getOpCode();
        Label        lblTrue  = bctx.ensureLabel(code, getAddress() + m_ofJmp);
        Label        lblFalse = code.newLabel();

        if (type1.isNullable() || type2.isNullable()) {
            // we are comparing two Nullable arguments where at least one is known to be "Null";
            // the only op for which comparison of the Null value with a non-Null can produce a True
            // is NEQ, for all others, the result would be negative;
            // the only ops, for which comparison of two Null values would produce a positive result
            // are: EQ, GE, LE
            switch (nOp) {
                case OP_JMP_NEQ ->
                    assembleNullCheck(code, reg1, reg2, lblFalse, lblTrue);
                case OP_JMP_EQ, OP_JMP_LTE, OP_JMP_GTE ->
                    assembleNullCheck(code, reg1, reg2, lblTrue, lblFalse);
                default ->
                    assembleNullCheck(code, reg1, reg2, lblFalse, lblFalse);
            }

            if (type1.isNullable()) {
                type1 = type1.removeNullable();
                reg1  = bctx.narrowRegister(code, reg1, type1);
            }
            if (type2.isNullable()) {
                type2 = type2.removeNullable();
                reg1  = bctx.narrowRegister(code, reg2, type2);
            }
            typeCmp = typeCmp.removeNullable();
        }

        // TODO: can we get rid of typeCompare?
        if (typeCmp.isFormalType()) {
            typeCmp = typeCmp.resolveConstraints();
        }
        assert typeCmp.equals(
            selectCommonType(type1, type2, ErrorListener.BLACKHOLE).removeNullable());

        typeCmp.buildCompare(bctx, code, nOp, reg1, reg2, lblTrue);

        code.labelBinding(lblFalse);
    }

    /**
     * Generate the code to check if specified registers are "Null" values. If the result of the
     * comparison can be computed, go to the corresponding label; otherwise - fall through.
     *
     * @param lblEqual  the label to go to in the case the value are equal
     * @param lblNotEq  the label to go to in the case values cannot possibly be equal
     */
    private void assembleNullCheck(CodeBuilder code, RegisterInfo reg1, RegisterInfo reg2,
                                   Label lblEqual, Label lblNotEq) {
        Label lblProceed = code.newLabel();

        if (reg1.type().isNullable()) {
            if (reg2.type().isNullable()) {
                Label lblNull1 = code.newLabel();
                Builder.checkNull(code, reg1, lblNull1);

                // (reg1 != Null)
                Builder.checkNotNull(code, reg2, lblProceed);  // (reg2 != Null) - proceed

                // (reg1 != Null && reg2 == Null) - negative result
                code.goto_(lblNotEq);

                // (reg1 == Null)
                code.labelBinding(lblNull1);
                Builder.checkNotNull(code, reg2, lblNotEq); // (reg2 != Null) - negative result

                // (reg1 == Null && reg2 == Null) - positive result
                code.goto_(lblEqual);
            } else {
                Builder.checkNotNull(code, reg1, lblProceed);
                code.goto_(lblNotEq); // (reg2 != Null && reg1 == Null) - negative result
            }
        } else { // (reg1 != Null)
            assert reg2.type().isNullable();
            Builder.checkNotNull(code, reg2, lblProceed);
            code.goto_(lblNotEq); // (reg1 != Null && reg2 == Null) - negative result
        }

        code.labelBinding(lblProceed);
    }


    protected void buildUnary(BuildContext bctx, CodeBuilder code) {
        int   nAddrJump = getAddress() + m_ofJmp;
        Label lblJump   = bctx.ensureLabel(code, nAddrJump);
        int   op        = getOpCode();

        switch (op) {
        case OP_JMP_COND, OP_JMP_NCOND:
            bctx.loadCtx(code);
            code.getfield(CD_Ctx, "container", CD_Container);
            bctx.loadArgument(code, m_nArg);
            code.invokevirtual(CD_Container, "isSpecified",
                               MethodTypeDesc.of(CD_boolean, CD_JavaString));
            if (op == OP_JMP_COND) {
                code.ifne(lblJump);
            } else {
                code.ifeq(lblJump);
            }
            return;

        case OP_JMP_TYPE, OP_JMP_NTYPE:
            buildTypeJump(bctx, code);
            return;
        }

        RegisterInfo reg = bctx.loadArgument(code, m_nArg);
        ClassDesc    cd  = reg.cd();
        if (cd.isPrimitive()) {
            if (reg instanceof DoubleSlot doubleSlot) {
                assert doubleSlot.flavor() == JitFlavor.MultiSlotPrimitive;
                code.iload(doubleSlot.extSlot()); // boolean Null indication
                switch (op) {
                    case OP_JMP_NULL  -> code.ifne(lblJump);
                    case OP_JMP_NNULL -> code.ifeq(lblJump);
                    default           -> throw new IllegalStateException();
                }
                return;
            }
            Builder.defaultLoad(code, cd);

            String desc = cd.descriptorString();
            switch (desc) {
            case "I", "S", "B", "C", "Z":
                switch (getOpCode()) {
                    case OP_JMP_ZERO  -> code.if_icmpeq(lblJump);
                    case OP_JMP_NZERO -> code.if_icmpne(lblJump);
                    default           -> throw new IllegalStateException();
                }
                break;

            case "J", "F", "D":
                switch (desc) {
                    case "J" -> code.lcmp();
                    case "F" -> code.fcmpl(); // REVIEW CP: fcmpl vs fcmpg?
                    case "D" -> code.dcmpl(); // REVIEW CP: ditto
                }
                switch (op) {
                    case OP_JMP_ZERO  -> code.ifeq(lblJump);
                    case OP_JMP_NZERO -> code.ifne(lblJump);
                    default -> throw new IllegalStateException();
                }
                break;

            default:
                throw new IllegalStateException();
            }
        } else {
            switch (op) {
            case OP_JMP_NULL:
                Builder.loadNull(code);
                code.if_acmpeq(lblJump);
                bctx.narrowRegister(code, reg, getAddress(), nAddrJump, reg.type().removeNullable());
                break;

            case OP_JMP_NNULL:
                Builder.loadNull(code);
                code.if_acmpne(lblJump);
                bctx.narrowRegister(code, reg, nAddrJump, -1, reg.type().removeNullable());
                break;

            default:
                throw new IllegalStateException();
            }
        }
    }

    private void buildTypeJump(BuildContext bctx, CodeBuilder code) {
        int   nAddrThis = getAddress();
        int   nAddrJump = nAddrThis + m_ofJmp;
        Label lblJump   = bctx.ensureLabel(code, nAddrJump);

        // this logic is almost identical to OpTest.buildTypeCheck();
        TypeConstant typeTarget = bctx.getArgumentType(m_nArg);
        if (m_nArg2 <= CONSTANT_OFFSET) {
            TypeConstant typeTest = bctx.getArgumentType(m_nArg2);
            assert typeTest.isTypeOfType();
            typeTest = typeTest.getParamType(0);

            if (typeTarget.isPrimitive()) {
                // we can statically compute the result, which most probably means that a formal
                // type was narrowed for a particular class flavor and the corresponding code
                // can be safely eliminated
                boolean isA = typeTarget.isA(typeTest);
                if ((getOpCode() == OP_JMP_TYPE) == isA) {
                    bctx.markDeadCode(nAddrThis + 1, nAddrJump);
                } else {
                    bctx.markDeadCode(nAddrJump, -1);
                }
                return;
            } else {
                RegisterInfo regTarget = bctx.loadArgument(code, m_nArg);
                bctx.loadCtx(code);
                code.invokevirtual(regTarget.cd(), "$xvmType", MD_xvmType); // target type
                Builder.loadTypeConstant(code, bctx.typeSystem, typeTest);  // test type
            }
            if (getOpCode() == OP_JMP_TYPE) {
                bctx.narrowRegister(code, m_nArg, nAddrJump, -1, typeTest);
            } else {
                bctx.narrowRegister(code, m_nArg, nAddrThis + 1, nAddrJump, typeTest);
            }
        } else {
            // dynamic types
            RegisterInfo regType = bctx.loadArgument(code, m_nArg2); // xType
            assert regType.type().isTypeOfType();
            code.getfield(CD_nType, "$type", CD_TypeConstant);
        }

        code.invokevirtual(CD_TypeConstant, "isA", MD_TypeIsA);

        if (getOpCode() == OP_JMP_TYPE) {
            code.ifne(lblJump);
        } else { // OP_JMP_NTYPE
            code.ifeq(lblJump);
        }
    }

    // ----- fields --------------------------------------------------------------------------------

    protected int m_nType;
    protected int m_nArg;
    protected int m_nArg2;
    protected int m_ofJmp;

    protected TypeConstant m_typeCommon; // the type to use for the comparison
    private   Argument     m_argVal;
    private   Argument     m_argVal2;
    private   Op           m_opDest;

    // number of exits to simulate on the jump
    protected transient int m_cExits;
}
