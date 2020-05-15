package org.xvm.asm.op;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.Argument;
import org.xvm.asm.Constant;
import org.xvm.asm.OpInvocable;

import org.xvm.asm.constants.MethodConstant;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.ExceptionHandle;

import org.xvm.runtime.template.collections.xTuple.TupleHandle;

import static org.xvm.util.Handy.readPackedInt;
import static org.xvm.util.Handy.writePackedLong;


/**
 * NVOK_TT rvalue-target, CONST-METHOD, rvalue-params-tuple, lvalue-return-tuple
 */
public class Invoke_TT
        extends OpInvocable
    {
    /**
     * Construct an NVOK_TT op based on the passed arguments.
     *
     * @param argTarget    the target Argument
     * @param constMethod  the method constant
     * @param argValue     the value Argument
     * @param argReturn    the Argument to move the result into
     */
    public Invoke_TT(Argument argTarget, MethodConstant constMethod, Argument argValue, Argument argReturn)
        {
        super(argTarget, constMethod);

        m_argValue = argValue;
        m_argReturn = argReturn;
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public Invoke_TT(DataInput in, Constant[] aconst)
            throws IOException
        {
        super(in, aconst);

        m_nArgTupleValue = readPackedInt(in);
        m_nRetValue = readPackedInt(in);
        }

    @Override
    public void write(DataOutput out, ConstantRegistry registry)
            throws IOException
        {
        super.write(out, registry);

        if (m_argValue != null)
            {
            m_nArgTupleValue = encodeArgument(m_argValue, registry);
            m_nRetValue = encodeArgument(m_argReturn, registry);
            }

        writePackedLong(out, m_nArgTupleValue);
        writePackedLong(out, m_nRetValue);
        }

    @Override
    public int getOpCode()
        {
        return OP_NVOK_TT;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        try
            {
            ObjectHandle hTarget = frame.getArgument(m_nTarget);
            ObjectHandle hArg    = frame.getArgument(m_nArgTupleValue);

            return isDeferred(hTarget)
                    ? hTarget.proceed(frame, frameCaller ->
                         resolveTuple(frameCaller, frameCaller.popStack(), hArg))
                    : resolveTuple(frame, hTarget, hArg);
            }
        catch (ExceptionHandle.WrapperException e)
            {
            return frame.raiseException(e);
            }
        }

    protected int resolveTuple(Frame frame, ObjectHandle hTarget, ObjectHandle hArg)
        {
        return isDeferred(hArg)
                ? hArg.proceed(frame, frameCaller ->
                     complete(frameCaller, hTarget,
                         ((TupleHandle) frameCaller.popStack()).m_ahValue))
                : complete(frame, hTarget,
                    ((TupleHandle) hArg).m_ahValue);
        }

    protected int complete(Frame frame, ObjectHandle hTarget, ObjectHandle[] ahArg)
        {
        checkReturnTupleRegister(frame, hTarget);

        return getCallChain(frame, hTarget).invokeT(frame, hTarget, ahArg, m_nRetValue);
        }

    @Override
    public void registerConstants(ConstantRegistry registry)
        {
        super.registerConstants(registry);

        m_argValue = registerArgument(m_argValue, registry);
        }

    private int m_nArgTupleValue;

    private Argument m_argValue;
    }
