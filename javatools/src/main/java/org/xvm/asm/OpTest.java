package org.xvm.asm;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.ExceptionHandle;
import org.xvm.runtime.Utils;

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
            m_typeCommon = typeCommon = (TypeConstant) frame.getConstant(m_nType);
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
        StringBuilder sb = new StringBuilder();
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

    protected int m_nType;
    protected int m_nValue1;
    protected int m_nValue2;
    protected int m_nRetValue;

    protected TypeConstant m_typeCommon; // the "compile time type" to use for the comparison
    protected Argument     m_argVal1;
    protected Argument     m_argVal2;
    protected Argument     m_argReturn;
}