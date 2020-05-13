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

import static org.xvm.util.Handy.readPackedInt;
import static org.xvm.util.Handy.writePackedLong;


/**
 * CALL_NT rvalue-function, #params:(rvalue) lvalue-return-tuple
 */
public class Call_NT
        extends OpCallable
    {
    /**
     * Construct a CALL_NT op based on the passed arguments.
     *
     * @param argFunction  the function Argument
     * @param aArgValue    the array of value Arguments
     * @param argReturn    the return Argument
     */
    public Call_NT(Argument argFunction, Argument[] aArgValue, Argument argReturn)
        {
        super(argFunction);

        m_aArgValue = aArgValue;
        m_argReturn = argReturn;
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public Call_NT(DataInput in, Constant[] aconst)
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
            m_nRetValue = encodeArgument(m_argReturn, registry);
            }

        writeIntArray(out, m_anArgValue);
        writePackedLong(out, m_nRetValue);
        }

    @Override
    public int getOpCode()
        {
        return OP_CALL_NT;
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

                checkReturnTupleRegister(frame, chain.getSuper(frame));

                ObjectHandle[] ahVar = frame.getArguments(m_anArgValue, chain.getSuper(frame).getMaxVars());

                if (anyDeferred(ahVar))
                    {
                    Frame.Continuation stepNext = frameCaller ->
                        chain.callSuperN1(frame, ahVar, m_nRetValue, true);

                    return new Utils.GetArguments(ahVar, stepNext).doNext(frame);
                    }

                return chain.callSuperN1(frame, ahVar, m_nRetValue, true);
                }

            if (m_nFunctionId <= CONSTANT_OFFSET)
                {
                MethodStructure function = getMethodStructure(frame);
                if (function == null)
                    {
                    return R_EXCEPTION;
                    }

                ObjectHandle[] ahVar = frame.getArguments(m_anArgValue, function.getMaxVars());

                checkReturnTupleRegister(frame, function);

                if (anyDeferred(ahVar))
                    {
                    Frame.Continuation stepNext = frameCaller ->
                        frame.callT(function, null, ahVar, m_nRetValue);

                    return new Utils.GetArguments(ahVar, stepNext).doNext(frame);
                    }

                if (function.isNative())
                    {
                    return getNativeTemplate(frame, function).
                        invokeNativeT(frame, function, null, ahVar, m_nRetValue);
                    }

                return frame.callT(function, null, ahVar, m_nRetValue);
                }

            ObjectHandle hFunction = frame.getArgument(m_nFunctionId);

            if (isDeferred(hFunction))
                {
                ObjectHandle[] ahFn = new ObjectHandle[] {hFunction};
                Frame.Continuation stepNext = frameCaller ->
                    complete(frameCaller, (FunctionHandle) ahFn[0]);

                return new Utils.GetArguments(ahFn, stepNext).doNext(frame);
                }
            return complete(frame, (FunctionHandle) hFunction);
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
                    hFunction.callT(frameCaller, null, ahVar, m_nRetValue);

                return new Utils.GetArguments(ahVar, stepNext).doNext(frame);
                }

            return hFunction.callT(frame, null, ahVar, m_nRetValue);
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
        m_argReturn = registerArgument(m_argReturn, registry);
        }

    @Override
    protected String getParamsString()
        {
        return getParamsString(m_anArgValue, m_aArgValue);
        }

    private int[] m_anArgValue;

    private Argument[] m_aArgValue;
    }