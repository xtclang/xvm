package org.xvm.asm;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.lang.classfile.CodeBuilder;
import java.lang.classfile.Label;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.javajit.BuildContext;
import org.xvm.javajit.Builder;
import org.xvm.javajit.JitFlavor;
import org.xvm.javajit.RegisterInfo;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.ExceptionHandle;
import org.xvm.runtime.Utils;

import static org.xvm.javajit.Builder.CD_nType;
import static org.xvm.javajit.Builder.CD_TypeConstant;
import static org.xvm.javajit.Builder.MD_TypeIsA;
import static org.xvm.javajit.Builder.MD_xvmType;

import static org.xvm.util.Handy.readPackedInt;
import static org.xvm.util.Handy.writePackedLong;


/**
 * Base class for test (IS_*) op-codes.
 */
public abstract class OpTest
        extends Op {
    /**
     * Construct a unary IS_ op.
     *
     * @param arg        the value Argument
     * @param argReturn  the location to store the test result
     */
    protected OpTest(Argument arg, Argument argReturn) {
        assert !isBinaryOp() && !hasSecondArgument();
        m_argVal1   = arg;
        m_argReturn = argReturn;
    }

    /**
     * Construct a two argument IS_ op.
     *
     * @param arg1       the first value Argument
     * @param arg2       the second value Argument
     * @param argReturn  the location to store the test result
     */
    protected OpTest(Argument arg1, Argument arg2, Argument argReturn) {
        assert hasSecondArgument() && !isBinaryOp();
        m_argVal1   = arg1;
        m_argVal2   = arg2;
        m_argReturn = argReturn;
    }

    /**
     * Construct a binary IS_ op.
     *
     * @param type       the compile-time type
     * @param arg1       the first value Argument
     * @param arg2       the second value Argument
     * @param argReturn  the location to store the test result
     */
    protected OpTest(TypeConstant type, Argument arg1, Argument arg2, Argument argReturn) {
        assert isBinaryOp();
        m_typeCommon = type;
        m_argVal1    = arg1;
        m_argVal2    = arg2;
        m_argReturn  = argReturn;
    }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    protected OpTest(DataInput in, Constant[] aconst)
            throws IOException {
        if (isBinaryOp()) {
            m_nType = readPackedInt(in);
        }
        m_nValue1 = readPackedInt(in);
        if (hasSecondArgument()) {
            m_nValue2 = readPackedInt(in);
        }
        m_nRetValue = readPackedInt(in);
    }

    @Override
    public void write(DataOutput out, ConstantRegistry registry)
            throws IOException {
        super.write(out, registry);

        if (m_typeCommon != null) {
            // encode the common type and discard it to be recalculated using the correct pool
            m_nType      = encodeArgument(m_typeCommon, registry);
            m_typeCommon = null;
        }
        if (m_argVal1 != null) {
            m_nValue1 = encodeArgument(m_argVal1, registry);
        }
        if (m_argVal2 != null) {
            m_nValue2 = encodeArgument(m_argVal2, registry);
        }
        if (m_argReturn != null) {
            m_nRetValue = encodeArgument(m_argReturn, registry);
        }

        if (isBinaryOp()) {
            writePackedLong(out, m_nType);
        }
        writePackedLong(out, m_nValue1);
        if (hasSecondArgument()) {
            writePackedLong(out, m_nValue2);
        }
        writePackedLong(out, m_nRetValue);
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
        if (frame.isNextRegister(m_nRetValue)) {
            frame.introduceResolvedVar(m_nRetValue, getResultType(frame));
        }
        return isBinaryOp() ? processBinaryOp(frame) : processUnaryOp(frame);
    }

    protected int processUnaryOp(Frame frame) {
        try {
            ObjectHandle hValue = frame.getArgument(m_nValue1);

            return isDeferred(hValue)
                    ? hValue.proceed(frame, frameCaller ->
                        completeUnaryOp(frameCaller, frameCaller.popStack()))
                    : completeUnaryOp(frame, hValue);
        } catch (ExceptionHandle.WrapperException e) {
            return frame.raiseException(e);
        }
    }

    protected int processBinaryOp(Frame frame) {
        try {
            ObjectHandle[] ahArg = frame.getArguments(new int[]{m_nValue1, m_nValue2}, 2);

            TypeConstant typeCommon = calculateCommonType(frame);

            if (anyDeferred(ahArg)) {
                Frame.Continuation stepNext = frameCaller ->
                    completeBinaryOp(frame, typeCommon, ahArg[0], ahArg[1]);

                return new Utils.GetArguments(ahArg, stepNext).doNext(frame);
            }

            return completeBinaryOp(frame, typeCommon, ahArg[0], ahArg[1]);
        } catch (ExceptionHandle.WrapperException e) {
            return frame.raiseException(e);
        }
    }

    protected TypeConstant calculateCommonType(Frame frame) {
        TypeConstant typeCommon = m_typeCommon;
        if (typeCommon == null) {
            m_typeCommon = typeCommon = frame.getConstant(m_nType, TypeConstant.class);
        }
        return frame.resolveType(typeCommon);
    }

    /**
     * A completion of a unary op; must be overridden by all binary ops.
     */
    protected int completeUnaryOp(Frame frame, ObjectHandle hValue) {
        throw new UnsupportedOperationException();
    }

    /**
     * A completion of a binary op; must be overridden by all binary ops.
     */
    protected int completeBinaryOp(Frame frame, TypeConstant type,
                                   ObjectHandle hValue1, ObjectHandle hValue2) {
        throw new UnsupportedOperationException();
    }

    /**
     * @return the result type for this Op
     */
    protected TypeConstant getResultType(Frame frame) {
        return frame.poolContext().typeBoolean();
    }

    @Override
    public void registerConstants(ConstantRegistry registry) {
        if (isBinaryOp()) {
            m_typeCommon = (TypeConstant) registerArgument(m_typeCommon, registry);
        }
        m_argVal1 = registerArgument(m_argVal1, registry);
        if (hasSecondArgument()) {
            m_argVal2 = registerArgument(m_argVal2, registry);
        }
        m_argReturn = registerArgument(m_argReturn, registry);
    }

    @Override
    public void resetSimulation() {
        resetRegister(m_argReturn);
    }

    @Override
    public void simulate(Scope scope) {
        checkNextRegister(scope, m_argReturn, m_nRetValue);
    }

    @Override
    public String toString() {
        var sb = new StringBuilder();
        sb.append(super.toString())
          .append(' ');
        if (isBinaryOp()) {
            sb.append(Argument.toIdString(m_typeCommon, m_nType))
              .append(", ");
        }
        sb.append(Argument.toIdString(m_argVal1, m_nValue1));
        if (hasSecondArgument()) {
            sb.append(", ")
              .append(Argument.toIdString(m_argVal2, m_nValue2));
        }
        return sb.append(", ")
                 .append(Argument.toIdString(m_argReturn, m_nRetValue))
                 .toString();
    }

    // ----- JIT support ---------------------------------------------------------------------------

    @Override
    public void computeTypes(BuildContext bctx) {
        bctx.typeMatrix.assign(getAddress(), m_nRetValue,
            getOpCode() == OP_CMP ? bctx.pool().typeOrdered() : bctx.pool().typeBoolean());
    }

    @Override
    public void build(BuildContext bctx, CodeBuilder code) {
        if (isBinaryOp()) {
            buildBinary(bctx, code);
        } else {
            buildUnary(bctx, code);
        }
    }

    protected void buildBinary(BuildContext bctx, CodeBuilder code) {
        // this is very similar to OpCondJump logic
        TypeConstant typeCmp = bctx.getTypeConstant(m_nType).getCanonicalJitType();
        RegisterInfo reg1    = bctx.ensureRegister(code, m_nValue1);
        RegisterInfo reg2    = bctx.ensureRegister(code, m_nValue2);
        TypeConstant type1   = reg1.type();
        TypeConstant type2   = reg2.type();
        int          nOp     = getOpCode();
        Label        lblEnd  = code.newLabel();

        if (type1.isNullable() || type2.isNullable()) {
            assembleNullCheck(code, reg1, reg2, lblEnd);

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
        TypeConstant typeCommon = selectCommonType(type1, type2, ErrorListener.BLACKHOLE).removeNullable();
        assert typeCmp.isA(typeCommon) && typeCommon.isA(typeCmp);

        typeCmp.buildCompare(bctx, code, nOp, reg1, reg2, /*lblTrue*/ null);

        code.labelBinding(lblEnd);
        if (nOp == OP_CMP) {
            bctx.storeValue(code, bctx.ensureRegInfo(m_nRetValue, bctx.pool().typeOrdered()));
        } else {
            bctx.storeValue(code, bctx.ensureRegInfo(m_nRetValue, bctx.pool().typeBoolean()));
        }
    }

    /**
     * Generate the code to check if specified registers are "Null" values. If the result of this op
     * can be computed, place the corresponding value on the Java stack and go to the specified
     * label; otherwise - fall through.
     *
     * @param lblEnd  the label to go to in the case a result has been computed (either positive or
     *                negative) and placed on Java stack
     */
    private void assembleNullCheck(CodeBuilder code, RegisterInfo reg1, RegisterInfo reg2,
                                   Label lblEnd) {
        Label lblEqual   = code.newLabel();
        Label lblNotEq   = code.newLabel();
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

        // we are comparing two Nullable arguments where at least one is known to be "Null";
        // the only op for which comparison of the Null value with a non-Null can produce a True
        // is NEQ, for all others, the result would be negative;
        // the only ops, for which comparison of two Null values would produce a positive result are:
        // EQ, GE, LE

        code.labelBinding(lblNotEq);
        switch (getOpCode()) {
            case OP_IS_NEQ -> code.iconst_1();
            default        -> code.iconst_0();
        }
        code.goto_(lblEnd);

        code.labelBinding(lblEqual);
        switch (getOpCode()) {
            case OP_IS_EQ, OP_IS_GTE, OP_IS_LTE -> code.iconst_1();
            default                             -> code.iconst_0();
        }
        code.goto_w(lblEnd);

        code.labelBinding(lblProceed);
    }

    protected void buildUnary(BuildContext bctx, CodeBuilder code) {
        switch (getOpCode()) {
            case OP_IS_NULL, OP_IS_NNULL -> buildNullCheck(bctx, code);
            case OP_IS_TYPE, OP_IS_NTYPE -> buildTypeCheck(bctx, code);
            default                      -> throw new IllegalStateException();
        }
        bctx.storeValue(code, bctx.ensureRegInfo(m_nRetValue, bctx.pool().typeBoolean()));
    }

    private void buildNullCheck(BuildContext bctx, CodeBuilder code) {
        RegisterInfo regArg = bctx.loadArgument(code, m_nValue1);

        Label   labelTrue = code.newLabel();
        Label   labelEnd  = code.newLabel();
        boolean fNot      = getOpCode() == OP_IS_NNULL;
        if (regArg instanceof BuildContext.DoubleSlot slotMulti) {
            assert slotMulti.flavor() == JitFlavor.MultiSlotPrimitive;

            code.iload(slotMulti.extSlot()); // True indicates Null
            if (fNot) {
                code.ifeq(labelTrue);
            } else {
                code.ifne(labelTrue);
            }
        } else {
            assert regArg.type().isNullable();

            Builder.loadNull(code);
            if (fNot) {
                code.if_acmpne(labelTrue);
            } else {
                code.if_acmpeq(labelTrue);
            }
        }

        code.iconst_0() // false
            .goto_(labelEnd)
            .labelBinding(labelTrue)
            .iconst_1() // true
            .labelBinding(labelEnd);
    }

    private void buildTypeCheck(BuildContext bctx, CodeBuilder code) {
        TypeConstant typeTarget = bctx.getArgumentType(m_nValue1);
        if (m_nValue2 <= CONSTANT_OFFSET) {
            TypeConstant typeTest = bctx.getArgumentType(m_nValue2);
            assert typeTest.isTypeOfType();
            typeTest = typeTest.getParamType(0);

            if (typeTarget.isPrimitive()) {
                // we can statically compute the result
                if (getOpCode() == OP_IS_TYPE) {
                    if (typeTarget.isA(typeTest)) {code.iconst_1();} else {code.iconst_0();}
                } else { // OP_IS_NTYPE
                    if (typeTarget.isA(typeTest)) {code.iconst_0();} else {code.iconst_1();}
                }
                return;
            } else {
                RegisterInfo regTarget = bctx.loadArgument(code, m_nValue1);
                bctx.loadCtx(code);
                code.invokevirtual(regTarget.cd(), "$xvmType", MD_xvmType); // target type
                Builder.loadTypeConstant(code, bctx.typeSystem, typeTest);   // test type
            }
        } else {
            RegisterInfo regType = bctx.loadArgument(code, m_nValue2); // xType
            assert regType.type().isTypeOfType();
            code.getfield(CD_nType, "$type", CD_TypeConstant);
        }

        code.invokevirtual(CD_TypeConstant, "isA", MD_TypeIsA);

        if (getOpCode() == OP_IS_NTYPE) {
            code.iconst_m1().ixor(); // "not"
        }
    }

    // ----- fields --------------------------------------------------------------------------------

    protected int m_nType;
    protected int m_nValue1;
    protected int m_nValue2;
    protected int m_nRetValue;

    protected TypeConstant m_typeCommon; // the "compile time type" to use for the comparison
    protected Argument     m_argVal1;
    protected Argument     m_argVal2;
    protected Argument     m_argReturn;
}