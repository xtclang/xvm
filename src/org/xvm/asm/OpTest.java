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
     * @param argReturn  the location to store the Boolean result
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
     * @param argReturn  the location to store the Boolean result
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
            }
        m_nRetValue = readPackedInt(in);
        }

    @Override
    public void write(DataOutput out, ConstantRegistry registry)
            throws IOException
        {
        if (m_argVal1 != null)
            {
            m_nValue1 = encodeArgument(m_argVal1, registry);
            if (isBinaryOp())
                {
                m_nValue2 = encodeArgument(m_argVal2, registry);
                }
            m_nRetValue = encodeArgument(m_argReturn, registry);
            }

        out.writeByte(getOpCode());
        writePackedLong(out, m_nValue1);
        if (isBinaryOp())
            {
            writePackedLong(out, m_nValue2);
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
            frame.introduceResolvedVar(m_nRetValue, frame.f_context.f_pool.typeBoolean());
            }
        return isBinaryOp() ? processBinaryOp(frame) : processUnaryOp(frame);
        }

    protected int processUnaryOp(Frame frame)
        {
        try
            {
            ObjectHandle hValue = frame.getArgument(m_nValue1);
            if (hValue == null)
                {
                return R_REPEAT;
                }

            if (isDeferred(hValue))
                {
                ObjectHandle[] ahValue = new ObjectHandle[] {hValue};
                Frame.Continuation stepNext = frameCaller ->
                    completeUnaryOp(frame, ahValue[0]);

                return new Utils.GetArguments(ahValue, stepNext).doNext(frame);
                }

            return completeUnaryOp(frame, hValue);
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
            ObjectHandle hValue1 = frame.getArgument(m_nValue1);
            ObjectHandle hValue2 = frame.getArgument(m_nValue2);
            if (hValue1 == null || hValue2 == null)
                {
                return R_REPEAT;
                }

            TypeConstant type1;
            TypeConstant type2;
            boolean fAnyProp = false;

            if (isDeferred(hValue1))
                {
                type1 = frame.getLocalType(m_nValue1, null);
                fAnyProp = true;
                }
            else
                {
                type1 = frame.getArgumentType(m_nValue1);
                }

            if (isDeferred(hValue2))
                {
                type2 = frame.getLocalType(m_nValue2, null);
                fAnyProp = true;
                }
            else
                {
                type2 = frame.getArgumentType(m_nValue2);
                }

            TypeConstant typeCommon = selectCommonType(type1, type2, ErrorListener.BLACKHOLE);
            if (typeCommon == null)
                {
                // this shouldn't have compiled
                System.err.printf("Suspicious comparison: " + type1.getValueString()
                    + " and " + type2.getValueString());
                }

            if (fAnyProp)
                {
                ObjectHandle[] ahValue = new ObjectHandle[] {hValue1, hValue2};
                Frame.Continuation stepNext = frameCaller ->
                    completeBinaryOp(frame, typeCommon, ahValue[0], ahValue[1]);

                return new Utils.GetArguments(ahValue, stepNext).doNext(frame);
                }

            return completeBinaryOp(frame, typeCommon, hValue1, hValue2);
            }
        catch (ExceptionHandle.WrapperException e)
            {
            return frame.raiseException(e);
            }
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

    @Override
    public void registerConstants(ConstantRegistry registry)
        {
        m_argVal1 = registerArgument(m_argVal1, registry);
        if (isBinaryOp())
            {
            m_argVal2 = registerArgument(m_argVal2, registry);
            }
        m_argReturn = registerArgument(m_argReturn, registry);
        }

    @Override
    public void simulate(Scope scope)
        {
        // TODO: remove when deprecated construction is removed
        if (m_argReturn == null)
            {
            if (scope.isNextRegister(m_nRetValue))
                {
                scope.allocVar();
                }
            }
        else
            {
            checkNextRegister(scope, m_argReturn);
            }
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
    }
