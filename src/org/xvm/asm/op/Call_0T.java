package org.xvm.asm.op;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.Argument;
import org.xvm.asm.Constant;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.OpCallable;

import org.xvm.runtime.CallChain;
import org.xvm.runtime.ClassTemplate;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.ExceptionHandle;
import org.xvm.runtime.Utils;

import org.xvm.runtime.template.xFunction.FunctionHandle;

import static org.xvm.util.Handy.readPackedInt;
import static org.xvm.util.Handy.writePackedLong;


/**
 * CALL_0T rvalue-function, lvalue-return-tuple
 */
public class Call_0T
        extends OpCallable
    {
    /**
     * Construct a CALL_0T op based on the passed arguments.
     *
     * @param argFunction  the function Argument
     * @param argReturn    the return Argument
     */
    public Call_0T(Argument argFunction, Argument argReturn)
        {
        super(argFunction);

        m_argReturn = argReturn;
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public Call_0T(DataInput in, Constant[] aconst)
            throws IOException
        {
        super(in, aconst);

        m_nRetValue = readPackedInt(in);
        }

    @Override
    public void write(DataOutput out, ConstantRegistry registry)
            throws IOException
        {
        super.write(out, registry);

        if (m_argReturn != null)
            {
            m_nRetValue = encodeArgument(m_argReturn, registry);
            }

        writePackedLong(out, m_nRetValue);
        }

    @Override
    public int getOpCode()
        {
        return OP_CALL_0T;
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

            checkReturnTupleRegister(frame, chain.getSuper(frame));

            switch (chain.callSuper01(frame, A_STACK))
                {
                case R_NEXT:
                    return frame.assignTuple(m_nRetValue,
                        new ObjectHandle[] {frame.popStack()});

                case R_CALL:
                    frame.m_frameNext.addContinuation(frameCaller ->
                        frameCaller.assignTuple(m_nRetValue,
                            new ObjectHandle[] {frame.popStack()}));
                    return R_CALL;

                case R_EXCEPTION:
                    return R_EXCEPTION;

                default:
                    throw new IllegalStateException();
                }
            }

        if (m_nFunctionId < 0)
            {
            MethodStructure function = getMethodStructure(frame);

            checkReturnTupleRegister(frame, function);

            if (function.isNative())
                {
                ClassTemplate clz = frame.f_context.f_heapGlobal.f_templates.getTemplate(
                        function.getContainingClass().getIdentityConstant());
                return clz.invokeNativeT(frame, function, null, Utils.OBJECTS_NONE, m_nRetValue);
                }

            ObjectHandle[] ahVar = new ObjectHandle[function.getMaxVars()];
            return frame.callT(function, null, ahVar, m_nRetValue);
            }

        try
            {
            FunctionHandle hFunction = (FunctionHandle) frame.getArgument(m_nFunctionId);
            if (hFunction == null)
                {
                return R_REPEAT;
                }

            checkReturnTupleRegister(frame, hFunction.getMethod());

            return hFunction.callT(frame, null, Utils.OBJECTS_NONE, m_nRetValue);
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

        m_argReturn = registerArgument(m_argReturn, registry);
        }
    }
