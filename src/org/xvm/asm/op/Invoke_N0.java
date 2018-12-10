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


/**
 * NVOK_N0 rvalue-target, CONST-METHOD, #params:(rvalue)
 */
public class Invoke_N0
        extends OpInvocable
    {
    /**
     * Construct an NVOK_N0 op based on the passed arguments.
     *
     * @param argTarget    the target Argument
     * @param constMethod  the method constant
     * @param aArgValue    the array of Argument values
     */
    public Invoke_N0(Argument argTarget, MethodConstant constMethod, Argument[] aArgValue)
        {
        super(argTarget, constMethod);

        m_aArgValue = aArgValue;
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public Invoke_N0(DataInput in, Constant[] aconst)
            throws IOException
        {
        super(in, aconst);

        m_anArgValue = readIntArray(in);
        }

    @Override
    public void write(DataOutput out, ConstantRegistry registry)
            throws IOException
        {
        super.write(out, registry);

        if (m_aArgValue != null)
            {
            m_anArgValue = encodeArguments(m_aArgValue, registry);
            }

        writeIntArray(out, m_anArgValue);
        }

    @Override
    public int getOpCode()
        {
        return OP_NVOK_N0;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        try
            {
            ObjectHandle hTarget = frame.getArgument(m_nTarget);
            if (hTarget == null)
                {
                return R_REPEAT;
                }

            if (isDeferred(hTarget))
                {
                // we won't know the number of method vars until later,
                // will have to resize the arg array then
                ObjectHandle[] ahArg = frame.getArguments(m_anArgValue, m_anArgValue.length);
                if (ahArg == null)
                    {
                    return R_REPEAT;
                    }

                ObjectHandle[] ahTarget = new ObjectHandle[] {hTarget};
                Frame.Continuation stepNext = frameCaller -> resolveArgs(frameCaller, ahTarget[0], ahArg);

                return new Utils.GetArguments(ahTarget, stepNext).doNext(frame);
                }

            return resolveArgs(frame, hTarget, null);
            }
        catch (ExceptionHandle.WrapperException e)
            {
            return frame.raiseException(e);
            }
        }

    protected int resolveArgs(Frame frame, ObjectHandle hTarget, ObjectHandle[] ahArg)
        {
        CallChain chain = getCallChain(frame, hTarget);

        ObjectHandle[] ahVar;
        if (ahArg == null)
            {
            try
                {
                ahVar = frame.getArguments(m_anArgValue, chain.getTop().getMaxVars());
                if (ahVar == null)
                    {
                    return R_REPEAT;
                    }
                }
            catch (ExceptionHandle.WrapperException e)
                {
                return frame.raiseException(e);
                }
            }
        else
            {
            ahVar = Utils.ensureSize(ahArg, chain.getTop().getMaxVars());
            }

        if (anyDeferred(ahVar))
            {
            Frame.Continuation stepNext = frameCaller ->
                complete(frameCaller, chain, hTarget, ahVar);
            return new Utils.GetArguments(ahVar, stepNext).doNext(frame);
            }
        return complete(frame, chain, hTarget, ahVar);
        }

    protected int complete(Frame frame, CallChain chain, ObjectHandle hTarget, ObjectHandle[] ahVar)
        {
        return chain.isNative()
             ? hTarget.getTemplate().invokeNativeN(frame, chain.getTop(), hTarget, ahVar, A_IGNORE)
             : hTarget.getTemplate().invoke1(frame, chain, hTarget, ahVar, A_IGNORE);
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
