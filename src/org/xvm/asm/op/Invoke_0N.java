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
import org.xvm.runtime.Utils;


/**
 * NVOK_0N rvalue-target, CONST-METHOD, #returns:(lvalue)
 */
public class Invoke_0N
        extends OpInvocable
    {
     /**
     * Construct an NVOK_0N op.
     *
     * @param nTarget    r-value that specifies the object on which the method being invoked
     * @param nMethodId  r-value that specifies the method being invoked
     * @param anRet      the l-value locations for the results
     *
     * @deprecated
     */
    public Invoke_0N(int nTarget, int nMethodId, int[] anRet)
        {
        super((Argument) null, null);

        m_nTarget = nTarget;
        m_nMethodId = nMethodId;
        m_anRetValue = anRet;
        }

    /**
     * Construct an NVOK_0N op based on the passed arguments.
     *
     * @param argTarget    the target Argument
     * @param constMethod  the method constant
     * @param aArgReturn   the array of Registers to move the results into
     */
    public Invoke_0N(Argument argTarget, MethodConstant constMethod, Argument[] aArgReturn)
        {
        super(argTarget, constMethod);

        m_aArgReturn = aArgReturn;
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public Invoke_0N(DataInput in, Constant[] aconst)
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
        return OP_NVOK_0N;
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
            if (hTarget == null)
                {
                return R_REPEAT;
                }

            if (isDeferred(hTarget))
                {
                ObjectHandle[] ahTarget = new ObjectHandle[] {hTarget};
                Frame.Continuation stepNext = frameCaller -> complete(frameCaller, ahTarget[0]);

                return new Utils.GetArguments(ahTarget, stepNext).doNext(frame);
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
        CallChain chain = getCallChain(frame, hTarget);
        MethodStructure method = chain.getTop();

        checkReturnRegisters(frame, method);

        if (chain.isNative())
            {
            return hTarget.getTemplate().invokeNativeNN(frame, method, hTarget,
                Utils.OBJECTS_NONE, m_anRetValue);
            }

        ObjectHandle[] ahVar = new ObjectHandle[method.getMaxVars()];

        return hTarget.getTemplate().invokeN(frame, chain, hTarget, ahVar, m_anRetValue);
        }
    }
