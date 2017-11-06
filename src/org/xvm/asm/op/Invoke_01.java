package org.xvm.asm.op;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.Constant;
import org.xvm.asm.MethodStructure;
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
        super((Argument) null, null);

        m_nTarget = nTarget;
        m_nMethodId = nMethodId;
        m_nRetValue = nRet;
        }

    /**
     * Construct an NVOKE_01 op based on the passed arguments.
     *
     * @param argTarget    the target argument
     * @param constMethod  the method constant
     * @param argReturn    the return value register
     */
    public Invoke_01(Argument argTarget, MethodConstant constMethod, Argument argReturn)
        {
        super(argTarget, constMethod);

        m_argReturn = argReturn;
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

            if (isProperty(hTarget))
                {
                ObjectHandle[] ahTarget = new ObjectHandle[] {hTarget};
                Frame.Continuation stepNext = frameCaller -> complete(frameCaller, ahTarget[0]);

                return new Utils.GetArgument(ahTarget, stepNext).doNext(frame);
                }

            return complete(frame, hTarget);
            }
        catch (ExceptionHandle.WrapperException e)
            {
            return frame.raiseException(e);
            }
        }

    protected int complete(Frame frame, ObjectHandle hTarget)
        {
        TypeComposition clz = hTarget.f_clazz;
        CallChain chain = getCallChain(frame, clz);
        MethodStructure method = chain.getTop();

        checkReturnRegister(frame, method);

        if (chain.isNative())
            {
            return clz.f_template.invokeNativeN(frame, method, hTarget,
                    Utils.OBJECTS_NONE, m_nRetValue);
            }

        ObjectHandle[] ahVar = new ObjectHandle[method.getMaxVars()];

        return clz.f_template.invoke1(frame, chain, hTarget, ahVar, m_nRetValue);
        }
    }
