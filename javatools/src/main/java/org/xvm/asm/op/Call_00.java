package org.xvm.asm.op;


import java.io.DataInput;
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
 * CALL_00 rvalue-function.
 */
public class Call_00
        extends OpCallable
    {
    /**
     * Construct a CALL_00 op based on the passed arguments.
     *
     * @param argFunction the function Argument
     */
    public Call_00(Argument argFunction)
        {
        super(argFunction);
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public Call_00(DataInput in, Constant[] aconst)
            throws IOException
        {
        super(in, aconst);
        }

    @Override
    public int getOpCode()
        {
        return OP_CALL_00;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        if (m_nFunctionId == A_SUPER)
            {
            CallChain chain = frame.m_chain;
            if (chain == null)
                {
                throw new IllegalStateException();
                }

            return chain.callSuper01(frame, A_IGNORE);
            }

        if (m_nFunctionId <= CONSTANT_OFFSET)
            {
            MethodStructure function = getMethodStructure(frame);
            if (function == null)
                {
                return R_EXCEPTION;
                }

            if (function.isNative())
                {
                return getNativeTemplate(frame, function).
                    invokeNativeN(frame, function, null, Utils.OBJECTS_NONE, A_IGNORE);
                }

            ObjectHandle[] ahVar = new ObjectHandle[function.getMaxVars()];
            return frame.call1(function, null, ahVar, A_IGNORE);
            }

        try
            {
            ObjectHandle hFunction = frame.getArgument(m_nFunctionId);

            return isDeferred(hFunction)
                    ? hFunction.proceed(frame, CALL)
                    : ((FunctionHandle) hFunction).call1(frame, null, Utils.OBJECTS_NONE, A_IGNORE);
            }
        catch (ExceptionHandle.WrapperException e)
            {
            return frame.raiseException(e);
            }
        }

    private static final Frame.Continuation CALL =
        frameCaller -> ((FunctionHandle) frameCaller.popStack()).
            call1(frameCaller, null, Utils.OBJECTS_NONE, A_IGNORE);
    }
