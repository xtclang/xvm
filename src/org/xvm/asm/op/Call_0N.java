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
 * CALL_0N rvalue-function,  #returns:(lvalue)
 */
public class Call_0N
        extends OpCallable
    {
    /**
     * Construct a CALL_0N op based on the passed arguments.
     *
     * @param argFunction  the function Argument
     * @param aArgReturn    the return Registers
     */
    public Call_0N(Argument argFunction, Argument[] aArgReturn)
        {
        super(argFunction);

        m_aArgReturn = aArgReturn;
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public Call_0N(DataInput in, Constant[] aconst)
            throws IOException
        {
        super(in, aconst);

        m_anRetValue = readIntArray(in);
        }

    @Override
    public void write(DataOutput out, ConstantRegistry registry)
            throws IOException
        {
        super.write(out, registry);

        if (m_aArgReturn != null)
            {
            m_anRetValue = encodeArguments(m_aArgReturn, registry);
            }

        writeIntArray(out, m_anRetValue);
        }

    @Override
    public int getOpCode()
        {
        return OP_CALL_0N;
        }

    @Override
    protected boolean isMultiReturn()
        {
        return true;
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

            checkReturnRegisters(frame, chain.getSuper(frame));

            return chain.callSuperNN(frame, Utils.OBJECTS_NONE, m_anRetValue);
            }

        if (m_nFunctionId <= CONSTANT_OFFSET)
            {
            MethodStructure function = getMethodStructure(frame);
            if (function == null)
                {
                return R_EXCEPTION;
                }

            checkReturnRegisters(frame, function);

            if (function.isNative())
                {
                return getNativeTemplate(frame, function).
                    invokeNativeNN(frame, function, null, Utils.OBJECTS_NONE, m_anRetValue);
                }

            ObjectHandle[] ahVar = new ObjectHandle[function.getMaxVars()];
            return frame.callN(function, null, ahVar, m_anRetValue);
            }

        try
            {
            FunctionHandle hFunction = (FunctionHandle) frame.getArgument(m_nFunctionId);
            if (hFunction == null)
                {
                return R_REPEAT;
                }

            checkReturnRegisters(frame, hFunction.getMethod());

            return hFunction.callN(frame, null, Utils.OBJECTS_NONE, m_anRetValue);
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

        registerArguments(m_aArgReturn, registry);
        }
    }
