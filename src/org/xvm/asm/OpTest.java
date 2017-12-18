package org.xvm.asm;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.ExceptionHandle;
import org.xvm.runtime.TypeComposition;
import org.xvm.runtime.Utils;

import org.xvm.runtime.template.xBoolean;

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
            frame.introduceResolvedVar(xBoolean.TYPE);
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

            if (isProperty(hValue))
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

            TypeComposition clz1;
            TypeComposition clz2;
            boolean fAnyProp = false;

            if (isProperty(hValue1))
                {
                clz1 = frame.getLocalClass(m_nValue1);
                fAnyProp = true;
                }
            else
                {
                clz1 = frame.getArgumentClass(m_nValue1);
                }

            if (isProperty(hValue2))
                {
                clz2 = frame.getLocalClass(m_nValue2);
                fAnyProp = true;
                }
            else
                {
                clz2 = frame.getArgumentClass(m_nValue2);
                }

            if (clz1 != clz2)
                {
                // this shouldn't have compiled
                throw new IllegalStateException();
                }

            if (fAnyProp)
                {
                ObjectHandle[] ahValue = new ObjectHandle[] {hValue1, hValue2};
                Frame.Continuation stepNext = frameCaller ->
                    completeBinaryOp(frame, clz1, ahValue[0], ahValue[1]);

                return new Utils.GetArguments(ahValue, stepNext).doNext(frame);
                }

            return completeBinaryOp(frame, clz1, hValue1, hValue2);
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
    protected int completeBinaryOp(Frame frame, TypeComposition clz,
                                   ObjectHandle hValue1, ObjectHandle hValue2)
        {
        throw new UnsupportedOperationException();
        }

    @Override
    public void registerConstants(ConstantRegistry registry)
        {
        registerArgument(m_argVal1, registry);
        if (isBinaryOp())
            {
            registerArgument(m_argVal2, registry);
            }
        registerArgument(m_argReturn, registry);
        }

    @Override
    public void simulate(Scope scope)
        {
        checkNextRegister(scope, m_argReturn);

        // TODO: remove when deprecated construction is removed
        if (scope.isNextRegister(m_nRetValue))
            {
            scope.allocVar();
            }
        }

    protected int m_nValue1;
    protected int m_nValue2;
    protected int m_nRetValue;

    protected Argument m_argVal1;
    protected Argument m_argVal2;
    protected Argument m_argReturn;
    }
