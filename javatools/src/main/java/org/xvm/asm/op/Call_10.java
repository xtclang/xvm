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
 * CALL_10 rvalue-function, rvalue-param
 */
public class Call_10
        extends OpCallable
    {
    /**
     * Construct a CALL_10 op based on the passed arguments.
     *
     * @param argFunction  the function Argument
     * @param argValue     the value Argument
     */
    public Call_10(Argument argFunction, Argument argValue)
        {
        super(argFunction);

        m_argValue = argValue;
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public Call_10(DataInput in, Constant[] aconst)
            throws IOException
        {
        super(in, aconst);

        m_nArgValue = readPackedInt(in);
        }

    @Override
    public void write(DataOutput out, ConstantRegistry registry)
            throws IOException
        {
        super.write(out, registry);

        if (m_argValue != null)
            {
            m_nArgValue = encodeArgument(m_argValue, registry);
            }

        writePackedLong(out, m_nArgValue);
        }

    @Override
    public int getOpCode()
        {
        return OP_CALL_10;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        try
            {
            ObjectHandle hArg = frame.getArgument(m_nArgValue);

            if (m_nFunctionId == A_SUPER)
                {
                CallChain chain = frame.m_chain;
                if (chain == null)
                    {
                    throw new IllegalStateException();
                    }

                return isDeferred(hArg)
                        ? hArg.proceed(frame, frameCaller ->
                            chain.callSuper10(frameCaller, frameCaller.popStack()))
                        : chain.callSuper10(frame, hArg);
                }

            if (m_nFunctionId <= CONSTANT_OFFSET)
                {
                MethodStructure function = getMethodStructure(frame);
                if (function == null)
                    {
                    return R_EXCEPTION;
                    }

                return isDeferred(hArg)
                        ? hArg.proceed(frame, frameCaller ->
                            complete(frameCaller, frameCaller.popStack(), function))
                        : complete(frame, hArg, function);
                }

            ObjectHandle hFunction = frame.getArgument(m_nFunctionId);

            if (isDeferred(hArg) || isDeferred(hFunction))
                {
                ObjectHandle[] ahArg = new ObjectHandle[] {hArg, hFunction};
                Frame.Continuation stepNext = frameCaller ->
                    complete(frameCaller, ahArg[0], (FunctionHandle) ahArg[1]);

                return new Utils.GetArguments(ahArg, stepNext).doNext(frame);
                }

            return complete(frame, hArg, (FunctionHandle) hFunction);
            }
        catch (ExceptionHandle.WrapperException e)
            {
            return frame.raiseException(e);
            }
        }

    protected int complete(Frame frame, ObjectHandle hArg, MethodStructure function)
        {
        if (function.isNative())
            {
            return getNativeTemplate(frame, function).
                invokeNative1(frame, function, null, hArg, A_IGNORE);
            }

        ObjectHandle[] ahVar = new ObjectHandle[function.getMaxVars()];
        ahVar[0] = hArg;
        return frame.call1(function, null, ahVar, A_IGNORE);
        }

    protected int complete(Frame frame, ObjectHandle hArg, FunctionHandle hFunction)
        {
        ObjectHandle[] ahVar = new ObjectHandle[hFunction.getVarCount()];
        ahVar[0] = hArg;

        return hFunction.call1(frame, null, ahVar, A_IGNORE);
        }

    @Override
    public void registerConstants(ConstantRegistry registry)
        {
        super.registerConstants(registry);

        m_argValue = registerArgument(m_argValue, registry);
        }

    @Override
    protected String getParamsString()
        {
        return Argument.toIdString(m_argValue, m_nArgValue);
        }

    private int m_nArgValue;

    private Argument m_argValue;
    }
