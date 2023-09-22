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
        extends Op
    {
    /**
     * Construct a unary IS_ op.
     *
     * @param arg        the value Argument
     * @param argReturn  the location to store the test result
     */
    protected OpTest(Argument arg, Argument argReturn)
        {
        assert (!isBinaryOp());

        m_argVal1   = arg;
        m_argReturn = argReturn;
        }

    /**
     * Construct a binary IS_ op.
     *
     * @param arg1       the first value Argument
     * @param arg2       the second value Argument
     * @param argReturn  the location to store the test result
     */
    protected OpTest(Argument arg1, Argument arg2, Argument argReturn)
        {
        assert (isBinaryOp());

        m_argVal1   = arg1;
        m_argVal2   = arg2;
        m_argReturn = argReturn;
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    protected OpTest(DataInput in, Constant[] aconst)
            throws IOException
        {
        m_nValue1 = readPackedInt(in);
        if (isBinaryOp())
            {
            m_nValue2 = readPackedInt(in);
            m_nType   = readPackedInt(in);
            }
        m_nRetValue = readPackedInt(in);
        }

    @Override
    public void write(DataOutput out, ConstantRegistry registry)
            throws IOException
        {
        super.write(out, registry);

        if (m_argVal1 != null)
            {
            m_nValue1 = encodeArgument(m_argVal1, registry);
            if (isBinaryOp())
                {
                m_nValue2 = encodeArgument(m_argVal2, registry);

                // encode the common type and discard it to be recalculated using the correct pool
                // (Note: IsType is a binary op that doesn't get injected with the common type)
                TypeConstant typeCommon = m_typeCommon;
                if (typeCommon == null)
                    {
                    m_nType = -1;
                    }
                else
                    {
                    m_nType      = encodeArgument(typeCommon, registry);
                    m_typeCommon = null;
                    }
                }
            m_nRetValue = encodeArgument(m_argReturn, registry);
            }

        writePackedLong(out, m_nValue1);
        if (isBinaryOp())
            {
            writePackedLong(out, m_nValue2);
            writePackedLong(out, m_nType);
            }
        writePackedLong(out, m_nRetValue);
        }

    /**
     * A "virtual constant" indicating whether or not this op is a binary one (has two arguments).
     *
     * @return true iff the op has two arguments
     */
    protected boolean isBinaryOp()
        {
        return false;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        if (frame.isNextRegister(m_nRetValue))
            {
            frame.introduceResolvedVar(m_nRetValue, getResultType(frame));
            }
        return isBinaryOp() ? processBinaryOp(frame) : processUnaryOp(frame);
        }

    protected int processUnaryOp(Frame frame)
        {
        try
            {
            ObjectHandle hValue = frame.getArgument(m_nValue1);

            return isDeferred(hValue)
                    ? hValue.proceed(frame, frameCaller ->
                        completeUnaryOp(frameCaller, frameCaller.popStack()))
                    : completeUnaryOp(frame, hValue);
            }
        catch (ExceptionHandle.WrapperException e)
            {
            return frame.raiseException(e);
            }
        }

    protected int processBinaryOp(Frame frame)
        {
        try
            {
            ObjectHandle[] ahArg = frame.getArguments(new int[]{m_nValue1, m_nValue2}, 2);

            TypeConstant typeCommon = calculateCommonType(frame);

            if (anyDeferred(ahArg))
                {
                Frame.Continuation stepNext = frameCaller ->
                    completeBinaryOp(frame, typeCommon, ahArg[0], ahArg[1]);

                return new Utils.GetArguments(ahArg, stepNext).doNext(frame);
                }

            return completeBinaryOp(frame, typeCommon, ahArg[0], ahArg[1]);
            }
        catch (ExceptionHandle.WrapperException e)
            {
            return frame.raiseException(e);
            }
        }

    protected TypeConstant calculateCommonType(Frame frame)
        {
        TypeConstant typeCommon = m_typeCommon;
        if (typeCommon == null)
            {
            m_typeCommon = typeCommon = (TypeConstant) frame.getConstant(m_nType);
            }
        return frame.resolveType(typeCommon);
        }

    /**
     * A completion of a unary op; must me overridden by all binary ops.
     */
    protected int completeUnaryOp(Frame frame, ObjectHandle hValue)
        {
        throw new UnsupportedOperationException();
        }

    /**
     * A completion of a binary op; must me overridden by all binary ops.
     */
    protected int completeBinaryOp(Frame frame, TypeConstant type,
                                   ObjectHandle hValue1, ObjectHandle hValue2)
        {
        throw new UnsupportedOperationException();
        }

    /**
     * @return the result type for this Op
     */
    protected TypeConstant getResultType(Frame frame)
        {
        return frame.poolContext().typeBoolean();
        }

    @Override
    public void registerConstants(ConstantRegistry registry)
        {
        m_argVal1 = registerArgument(m_argVal1, registry);
        if (isBinaryOp())
            {
            m_argVal2 = registerArgument(m_argVal2, registry);
            m_typeCommon = (TypeConstant) registerArgument(m_typeCommon, registry);
            }
        m_argReturn = registerArgument(m_argReturn, registry);
        }

    @Override
    public void resetSimulation()
        {
        resetRegister(m_argReturn);
        }

    @Override
    public void simulate(Scope scope)
        {
        checkNextRegister(scope, m_argReturn, m_nRetValue);
        }

    /**
     * Used by the compiler and verifier to inject the common type.
     */
    public void setCommonType(TypeConstant type)
        {
        m_typeCommon = type;
        }

    @Override
    public String toString()
        {
        return super.toString()
                + ' '
                + Argument.toIdString(m_argVal1, m_nValue1)
                + ", "
                + Argument.toIdString(m_argVal2, m_nValue2)
                + ", "
                + Argument.toIdString(m_argReturn, m_nRetValue);
        }

    protected int m_nValue1;
    protected int m_nValue2;
    protected int m_nRetValue;

    protected Argument m_argVal1;
    protected Argument m_argVal2;
    protected Argument m_argReturn;

    // the type to use for the comparison
    // TODO: it should be injected by the verifier and removed from the serialization logic
    protected int m_nType;
    protected TypeConstant m_typeCommon;
    }