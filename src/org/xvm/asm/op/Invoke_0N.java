package org.xvm.asm.op;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.Constant;
import org.xvm.asm.OpInvocable;
import org.xvm.asm.Register;

import org.xvm.asm.constants.MethodConstant;

import org.xvm.runtime.CallChain;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.ExceptionHandle;
import org.xvm.runtime.TypeComposition;
import org.xvm.runtime.Utils;

import static org.xvm.util.Handy.readPackedInt;


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
     */
    public Invoke_0N(int nTarget, int nMethodId, int[] anRet)
        {
        super(nTarget, nMethodId);

        m_anRetValue = anRet;
        }

    /**
     * Construct an NVOK_0N op based on the passed arguments.
     *
     * @param argTarget    the target Argument
     * @param constMethod  the method constant
     * @param aRegReturn   the array of Registers to move the results into
     */
    public Invoke_0N(Argument argTarget, MethodConstant constMethod, Register[] aRegReturn)
        {
        super(argTarget, constMethod);

        m_aRegReturn = aRegReturn;
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
        super(readPackedInt(in), readPackedInt(in));

        m_anRetValue = readIntArray(in);
        }

    @Override
    public void write(DataOutput out, ConstantRegistry registry)
            throws IOException
        {
        if (m_aRegReturn != null)
            {
            m_anRetValue = encodeArguments(m_aRegReturn, registry);
            }

        writeIntArray(out, m_anRetValue);
        }

    @Override
    public int getOpCode()
        {
        return OP_NVOK_0N;
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
                Frame.Continuation stepLast = frameCaller -> complete(frameCaller, ahTarget[0]);

                return new Utils.GetArgument(ahTarget, stepLast).doNext(frame);
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

        if (chain.isNative())
            {
            return clz.f_template.invokeNativeNN(frame, chain.getTop(), hTarget,
                    Utils.OBJECTS_NONE, m_anRetValue);
            }

        ObjectHandle[] ahVar = new ObjectHandle[chain.getTop().getMaxVars()];

        return clz.f_template.invokeN(frame, chain, hTarget, ahVar, m_anRetValue);
        }

    private int[] m_anRetValue;

    private Register[] m_aRegReturn;
    }
