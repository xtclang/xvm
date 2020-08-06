package org.xvm.asm.op;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.Argument;
import org.xvm.asm.Constant;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.OpCallable;

import org.xvm.runtime.CallChain;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.ExceptionHandle;
import org.xvm.runtime.Utils;

import org.xvm.runtime.template._native.reflect.xRTFunction.FunctionHandle;


/**
 * CALL_NN rvalue-function, #params:(rvalue) #returns:(lvalue)
 */
public class Call_NN
        extends OpCallable
    {
    /**
     * Construct a CALL_NN op based on the passed arguments.
     *
     * @param argFunction  the function Argument
     * @param aArgValue    the array of value Arguments
     * @param aArgReturn   the return Register
     */
    public Call_NN(Argument argFunction, Argument[] aArgValue, Argument[] aArgReturn)
        {
        super(argFunction);

        m_aArgValue = aArgValue;
        m_aArgReturn = aArgReturn;
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public Call_NN(DataInput in, Constant[] aconst)
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
        return OP_CALL_NN;
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
            if (m_nFunctionId == A_SUPER)
                {
                CallChain chain = frame.m_chain;
                if (chain == null)
                    {
                    throw new IllegalStateException();
                    }

                MethodStructure methodSuper = chain.getSuper(frame);

                checkReturnRegisters(frame, methodSuper);

                ObjectHandle[] ahVar = frame.getArguments(m_anArgValue, methodSuper.getMaxVars());

                if (anyDeferred(ahVar))
                    {
                    Frame.Continuation stepNext = frameCaller ->
                        chain.callSuperNN(frame, ahVar, m_anRetValue);

                    return new Utils.GetArguments(ahVar, stepNext).doNext(frame);
                    }

                return chain.callSuperNN(frame, ahVar, m_anRetValue);
                }

            if (m_nFunctionId <= CONSTANT_OFFSET)
                {
                MethodStructure function = getMethodStructure(frame);
                if (function == null)
                    {
                    return R_EXCEPTION;
                    }

                ObjectHandle[] ahVar = frame.getArguments(m_anArgValue, function.getMaxVars());

                checkReturnRegisters(frame, function);

                if (anyDeferred(ahVar))
                    {
                    Frame.Continuation stepNext = frameCaller ->
                        frame.callN(function, null, ahVar, m_anRetValue);

                    return new Utils.GetArguments(ahVar, stepNext).doNext(frame);
                    }

                if (function.isNative())
                    {
                    return getNativeTemplate(frame, function).
                        invokeNativeNN(frame, function, null, ahVar, m_anRetValue);
                    }

                return frame.callN(function, null, ahVar, m_anRetValue);
                }

            ObjectHandle hFunction = frame.getArgument(m_nFunctionId);

            return isDeferred(hFunction)
                    ? hFunction.proceed(frame, frameCaller ->
                        complete(frameCaller, (FunctionHandle) frameCaller.popStack()))
                    : complete(frame, (FunctionHandle) hFunction);
            }
        catch (ExceptionHandle.WrapperException e)
            {
            return frame.raiseException(e);
            }
        }

    protected int complete(Frame frame, FunctionHandle hFunction)
        {
        try
            {
            ObjectHandle[] ahVar = frame.getArguments(m_anArgValue, hFunction.getVarCount());
            if (anyDeferred(ahVar))
                {
                Frame.Continuation stepNext = frameCaller ->
                    hFunction.callN(frameCaller, null, ahVar, m_anRetValue);

                return new Utils.GetArguments(ahVar, stepNext).doNext(frame);
                }

            return hFunction.callN(frame, null, ahVar, m_anRetValue);
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
        registerArguments(m_aArgReturn, registry);
        }

    @Override
    protected String getParamsString()
        {
        return getParamsString(m_anArgValue, m_aArgValue);
        }

    private int[] m_anArgValue;

    private Argument[] m_aArgValue;
    }