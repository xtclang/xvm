package org.xvm.asm;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.ExceptionHandle;
import org.xvm.runtime.Utils;

import static org.xvm.util.Handy.readPackedInt;
import static org.xvm.util.Handy.writePackedLong;


/**
 * Base class for GP_ op codes.
 */
public abstract class OpGeneral
        extends Op
    {
    /**
     * Construct a unary op for the passed arguments.
     *
     * @param argTarget  the target Argument
     * @param argReturn  the Argument to move the result into
     */
    protected OpGeneral(Argument argTarget, Argument argReturn)
        {
        assert(!isBinaryOp());

        m_argTarget = argTarget;
        m_argReturn = argReturn;
        }

    /**
     * Construct a binary op for the passed arguments.
     *
     * @param argTarget  the target Argument
     * @param argValue   the second value Argument
     * @param argReturn  the Argument to store the result into
     */
    protected OpGeneral(Argument argTarget, Argument argValue, Argument argReturn)
        {
        assert(isBinaryOp());

        m_argTarget = argTarget;
        m_argValue  = argValue;
        m_argReturn = argReturn;
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    protected OpGeneral(DataInput in, Constant[] aconst)
            throws IOException
        {
        m_nTarget = readPackedInt(in);
        if (isBinaryOp())
            {
            m_nArgValue = readPackedInt(in);
            }
        m_nRetValue = readPackedInt(in);
        }

    @Override
    public void write(DataOutput out, ConstantRegistry registry)
            throws IOException
        {
        if (m_argTarget != null)
            {
            m_nTarget = encodeArgument(m_argTarget, registry);
            if (isBinaryOp())
                {
                m_nArgValue = encodeArgument(m_argValue,  registry);
                }
            m_nRetValue = encodeArgument(m_argReturn, registry);
            }

        out.writeByte(getOpCode());

        writePackedLong(out, m_nTarget);
        if (isBinaryOp())
            {
            writePackedLong(out, m_nArgValue);
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
        // majority of the ops are binary; let's default to that
        return true;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        return isBinaryOp() ? processBinaryOp(frame) : processUnaryOp(frame);
        }

    protected int processUnaryOp(Frame frame)
        {
        try
            {
            ObjectHandle hTarget = frame.getArgument(m_nTarget);
            if (hTarget == null)
                {
                return R_REPEAT;
                }

            if (frame.isNextRegister(m_nRetValue))
                {
                frame.introduceVarCopy(m_nTarget);
                }

            if (isDeferred(hTarget))
                {
                ObjectHandle[] ahValue = new ObjectHandle[] {hTarget};
                Frame.Continuation stepNext = frameCaller ->
                    completeUnary(frameCaller, ahValue[0]);

                return new Utils.GetArguments(ahValue, stepNext).doNext(frame);
                }

            return completeUnary(frame, hTarget);
            }
        catch (ExceptionHandle.WrapperException e)
            {
            return frame.raiseException(e);
            }
        }

    protected int completeUnary(Frame frame, ObjectHandle hTarget)
        {
        throw new UnsupportedOperationException();
        }

    protected int processBinaryOp(Frame frame)
        {
        try
            {
            ObjectHandle hTarget = frame.getArgument(m_nTarget);
            ObjectHandle hArg = frame.getArgument(m_nArgValue);
            if (hTarget == null || hArg == null)
                {
                return R_REPEAT;
                }

            if (frame.isNextRegister(m_nRetValue))
                {
                frame.introduceVarCopy(m_nTarget);
                }

            if (isDeferred(hTarget) || isDeferred(hArg))
                {
                ObjectHandle[] ahValue = new ObjectHandle[] {hTarget, hArg};
                Frame.Continuation stepNext = frameCaller ->
                    completeBinary(frameCaller, ahValue[0], ahValue[1]);

                return new Utils.GetArguments(ahValue, stepNext).doNext(frame);
                }

            return completeBinary(frame, hTarget, hArg);
            }
        catch (ExceptionHandle.WrapperException e)
            {
            return frame.raiseException(e);
            }
        }

    protected int completeBinary(Frame frame, ObjectHandle hTarget, ObjectHandle hArg)
        {
        throw new UnsupportedOperationException();
        }

    @Override
    public void simulate(Scope scope)
        {
        if (m_argReturn == null)
            {
            // TODO: remove when deprecated construction is removed
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
    public void registerConstants(ConstantRegistry registry)
        {
        registerArgument(m_argTarget, registry);
        if (isBinaryOp())
            {
            registerArgument(m_argValue, registry);
            }
        registerArgument(m_argReturn, registry);
        }

    protected int m_nTarget;
    protected int m_nArgValue;
    protected int m_nRetValue;

    private Argument m_argTarget;
    private Argument m_argValue;
    private Argument m_argReturn;
    }
