package org.xvm.asm.op;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.Argument;
import org.xvm.asm.Constant;
import org.xvm.asm.OpInvocable;

import org.xvm.asm.constants.MethodConstant;

import org.xvm.runtime.CallChain;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.ExceptionHandle;
import org.xvm.runtime.Utils;

import static org.xvm.util.Handy.readPackedInt;
import static org.xvm.util.Handy.writePackedLong;


/**
 * NVOK_N1  rvalue-target, CONST-METHOD, #params:(rvalue), lvalue-return
 */
public class Invoke_N1
        extends OpInvocable
    {
    /**
     * Construct an NVOK_N1 op based on the passed arguments.
     *
     * @param argTarget    the target Argument
     * @param constMethod  the method constant
     * @param aArgValue    the array of Argument values
     * @param argReturn    the Argument to move the result into
     */
    public Invoke_N1(Argument argTarget, MethodConstant constMethod, Argument[] aArgValue, Argument argReturn)
        {
        super(argTarget, constMethod);

        m_aArgValue = aArgValue;
        m_argReturn = argReturn;
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public Invoke_N1(DataInput in, Constant[] aconst)
            throws IOException
        {
        super(in, aconst);

        m_anArgValue = readIntArray(in);
        m_nRetValue = readPackedInt(in);
        }

    @Override
    public void write(DataOutput out, ConstantRegistry registry)
            throws IOException
        {
        super.write(out, registry);

        if (m_aArgValue != null)
            {
            m_anArgValue = encodeArguments(m_aArgValue, registry);
            m_nRetValue  = encodeArgument(m_argReturn, registry);
            }

        writeIntArray(out, m_anArgValue);
        writePackedLong(out, m_nRetValue);
        }

    @Override
    public int getOpCode()
        {
        return OP_NVOK_N1;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        try
            {
            ObjectHandle hTarget = frame.getArgument(m_nTarget);

            if (isDeferred(hTarget))
                {
                ObjectHandle[] ahTarget = new ObjectHandle[] {hTarget};
                Frame.Continuation stepNext = frameCaller -> resolveArgs(frameCaller, ahTarget[0]);

                return new Utils.GetArguments(ahTarget, stepNext).doNext(frame);
                }

            return resolveArgs(frame, hTarget);
            }
        catch (ExceptionHandle.WrapperException e)
            {
            return frame.raiseException(e);
            }
        }

    protected int resolveArgs(Frame frame, ObjectHandle hTarget)
        {
        checkReturnRegister(frame, hTarget);

        CallChain chain = getCallChain(frame, hTarget);

        try
            {
            ObjectHandle[] ahArg = frame.getArguments(m_anArgValue, chain.getMaxVars());

            if (anyDeferred(ahArg))
                {
                Frame.Continuation stepNext =
                    frameCaller -> chain.invoke(frameCaller, hTarget, ahArg, m_nRetValue);
                return new Utils.GetArguments(ahArg, stepNext).doNext(frame);
                }
            return chain.invoke(frame, hTarget, ahArg, m_nRetValue);
            }
        catch (ExceptionHandle.WrapperException e)
            {
            return frame.raiseException(e);
            }
        }

    @Override
    public void registerConstants(ConstantRegistry registry)
        {
        super.registerConstants(registry);

        registerArguments(m_aArgValue, registry);
        }

    @Override
    protected String getParamsString()
        {
        return getParamsString(m_anArgValue, m_aArgValue);
        }

    private int[] m_anArgValue;

    private Argument[] m_aArgValue;
    }
