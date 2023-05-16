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
        assert argTarget != null && argReturn != null;

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
        assert argTarget != null && argValue != null && argReturn != null;

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
        super.write(out, registry);

        if (m_argTarget != null)
            {
            m_nTarget = encodeArgument(m_argTarget, registry);
            if (isBinaryOp())
                {
                m_nArgValue = encodeArgument(m_argValue,  registry);
                }
            m_nRetValue = encodeArgument(m_argReturn, registry);
            }

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

            if (frame.isNextRegister(m_nRetValue))
                {
                frame.introduceVarCopy(m_nRetValue, m_nTarget);
                }

            return isDeferred(hTarget)
                    ? hTarget.proceed(frame, frameCaller ->
                        completeUnary(frameCaller, frameCaller.popStack()))
                    : completeUnary(frame, hTarget);
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
            ObjectHandle[] ahArg = frame.getArguments(new int[] {m_nTarget, m_nArgValue}, 2);

            if (frame.isNextRegister(m_nRetValue))
                {
                frame.introduceVarCopy(m_nRetValue, m_nTarget);  // TODO GG type *must* come from the op method
                }

            if (anyDeferred(ahArg))
                {
                Frame.Continuation stepNext = frameCaller ->
                    completeBinary(frameCaller, ahArg[0], ahArg[1]);

                return new Utils.GetArguments(ahArg, stepNext).doNext(frame);
                }

            return completeBinary(frame, ahArg[0], ahArg[1]);
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
    public void resetSimulation()
        {
        resetRegister(m_argReturn);
        }

    @Override
    public void simulate(Scope scope)
        {
        checkNextRegister(scope, m_argReturn, m_nRetValue);
        }

    @Override
    public void registerConstants(ConstantRegistry registry)
        {
        m_argTarget = registerArgument(m_argTarget, registry);
        if (isBinaryOp())
            {
            m_argValue = registerArgument(m_argValue, registry);
            }
        m_argReturn = registerArgument(m_argReturn, registry);
        }

    @Override
    public String toString()
        {
        StringBuilder sb = new StringBuilder();
        sb.append(toName(getOpCode()))
          .append(' ')
          .append(Argument.toIdString(m_argTarget, m_nTarget));

        if (isBinaryOp())
            {
            sb.append(", ")
              .append(Argument.toIdString(m_argValue, m_nArgValue));
            }
        sb.append(", ")
          .append(Argument.toIdString(m_argReturn, m_nRetValue));
        return sb.toString();
        }

    protected int m_nTarget;
    protected int m_nArgValue;
    protected int m_nRetValue;

    private Argument m_argTarget;
    private Argument m_argValue;
    private Argument m_argReturn;
    }