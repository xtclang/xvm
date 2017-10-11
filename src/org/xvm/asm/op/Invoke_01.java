package org.xvm.asm.op;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.Constant;
import org.xvm.asm.OpInvocable;

import org.xvm.asm.constants.MethodConstant;

import org.xvm.runtime.CallChain;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.ExceptionHandle;
import org.xvm.runtime.TypeComposition;
import org.xvm.runtime.Utils;

import static org.xvm.util.Handy.readPackedInt;
import static org.xvm.util.Handy.writePackedLong;


/**
 * NVOK_01 rvalue-target, rvalue-method, lvalue-return
 */
public class Invoke_01
        extends OpInvocable
    {
    /**
     * Construct an NVOK_01 op.
     *
     * @param nTarget    r-value that specifies the object on which the method being invoked
     * @param nMethodId  r-value that specifies the method being invoked
     * @param nRet       the l-value location for the result
     *
     * @deprecated
     */
    public Invoke_01(int nTarget, int nMethodId, int nRet)
        {
        m_nTarget   = nTarget;
        m_nMethodId = nMethodId;
        m_nRetValue = nRet;
        }

    /**
     * Construct an NVOKE_01 op for the specified Tuple type and arguments.
     *
     * @param argTarget    the target argument
     * @param constMethod  the method constant
     * @param argRet       the return value argument
     */
    public Invoke_01(Argument argTarget, MethodConstant constMethod, Argument argRet)
        {
        m_argTarget   = argTarget;
        m_constMethod = constMethod;
        m_argRet      = argRet;
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public Invoke_01(DataInput in, Constant[] aconst)
            throws IOException
        {
        m_nTarget   = readPackedInt(in);
        m_nMethodId = readPackedInt(in);
        m_nRetValue = readPackedInt(in);
        }

    @Override
    public void write(DataOutput out, ConstantRegistry registry)
            throws IOException
        {
        if (m_argTarget != null)
            {
            m_nTarget   = encodeArgument(m_argTarget  , registry);
            m_nMethodId = encodeArgument(m_constMethod, registry);
            m_nRetValue = encodeArgument(m_argRet     , registry);
            }

        out.writeByte(OP_NVOK_01);
        writePackedLong(out, m_nTarget);
        writePackedLong(out, m_nMethodId);
        writePackedLong(out, m_nRetValue);
        }

    @Override
    public int getOpCode()
        {
        return OP_NVOK_01;
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

            TypeComposition clz = hTarget.f_clazz;
            CallChain chain = getCallChain(frame, clz, m_nMethodId);

            if (chain.isNative())
                {
                return clz.f_template.invokeNativeN(frame, chain.getTop(), hTarget,
                        Utils.OBJECTS_NONE, m_nRetValue);
                }

            ObjectHandle[] ahVar = new ObjectHandle[chain.getTop().getMaxVars()];

            return clz.f_template.invoke1(frame, chain, hTarget, ahVar, m_nRetValue);
            }
        catch (ExceptionHandle.WrapperException e)
            {
            return frame.raiseException(e);
            }
        }

    @Override
    public void registerConstants(ConstantRegistry registry)
        {
        registerArgument(m_argTarget, registry);
        }

    private Argument       m_argTarget;
    private MethodConstant m_constMethod;
    private Argument       m_argRet;

    private int m_nTarget;
    private int m_nMethodId;
    private int m_nRetValue;
    }
