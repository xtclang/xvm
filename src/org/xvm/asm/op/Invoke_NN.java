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

import static org.xvm.util.Handy.checkElementsNonNull;


/**
 *NVOK_NN rvalue-target, CONST-METHOD, #params:(rvalue), #returns:(lvalue)
 */
public class Invoke_NN
        extends OpInvocable
    {
    /**
     * Construct an NVOK_NN op based on the passed arguments.
     *
     * @param argTarget    the target Argument
     * @param constMethod  the method constant
     * @param aArgValue    the array of Argument values
     * @param aArgReturn   the Register array to move the results into
     */
    public Invoke_NN(Argument argTarget, MethodConstant constMethod, Argument[] aArgValue, Argument[] aArgReturn)
        {
        super(argTarget, constMethod);

        checkElementsNonNull(aArgValue);
        checkElementsNonNull(aArgReturn);

        m_aArgValue  = aArgValue;
        m_aArgReturn = aArgReturn;
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public Invoke_NN(DataInput in, Constant[] aconst)
            throws IOException
        {
        super(in, aconst);

        m_anArgValue = readIntArray(in);
        m_anRetValue = readIntArray(in);
        }

    @Override
    public void write(DataOutput out, ConstantRegistry registry)
            throws IOException
        {
        super.write(out, registry);

        if (m_aArgValue != null)
            {
            m_anArgValue = encodeArguments(m_aArgValue, registry);
            m_anRetValue = encodeArguments(m_aArgReturn, registry);
            }

        writeIntArray(out, m_anArgValue);
        writeIntArray(out, m_anRetValue);
        }

    @Override
    public int getOpCode()
        {
        return OP_NVOK_NN;
        }

    @Override
    protected boolean isMultiReturn()
        {
        return true;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        try
            {
            ObjectHandle hTarget = frame.getArgument(m_nTarget);

            return isDeferred(hTarget)
                    ? hTarget.proceed(frame, frameCaller ->
                         resolveArgs(frameCaller, frameCaller.popStack()))
                    : resolveArgs(frame, hTarget);
            }
        catch (ExceptionHandle.WrapperException e)
            {
            return frame.raiseException(e);
            }
        }

    protected int resolveArgs(Frame frame, ObjectHandle hTarget)
        {
        checkReturnRegisters(frame, hTarget);

        CallChain chain = getCallChain(frame, hTarget);

        try
            {
            ObjectHandle[] ahArg = frame.getArguments(m_anArgValue, chain.getMaxVars());

            if (anyDeferred(ahArg))
                {
                Frame.Continuation stepNext = frameCaller ->
                    chain.invoke(frameCaller, hTarget, ahArg, m_anRetValue);
                return new Utils.GetArguments(ahArg, stepNext).doNext(frame);
                }
            return chain.invoke(frame, hTarget, ahArg, m_anRetValue);
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
