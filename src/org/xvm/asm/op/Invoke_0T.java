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
import static org.xvm.util.Handy.writePackedLong;


/**
 * NVOK_0T rvalue-target, CONST-METHOD, lvalue-return-tuple
 */
public class Invoke_0T
        extends OpInvocable
    {
    /**
     * Construct an NVOK_0T op.
     *
     * @param nTarget    r-value that specifies the object on which the method being invoked
     * @param nMethodId  r-value that specifies the method being invoked
     * @param nRet       the l-value location for the tuple result
     */
    public Invoke_0T(int nTarget, int nMethodId, int nRet)
        {
        super(nTarget, nMethodId);

        m_nTupleRetValue = nRet;
        }

    /**
     * Construct an NVOK_0T op based on the passed arguments.
     *
     * @param argTarget    the target Argument
     * @param constMethod  the method constant
     * @param regReturn    the Register to move the result into
     */
    public Invoke_0T(Argument argTarget, MethodConstant constMethod, Argument[] aArgValue, Register regReturn)
        {
        super(argTarget, constMethod);

        m_regReturn = regReturn;
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public Invoke_0T(DataInput in, Constant[] aconst)
            throws IOException
        {
        super(readPackedInt(in), readPackedInt(in));

        m_nTupleRetValue = readPackedInt(in);
        }

    @Override
    public void write(DataOutput out, ConstantRegistry registry)
            throws IOException
        {
        super.write(out, registry);

        if (m_regReturn != null)
            {
            m_nTupleRetValue = encodeArgument(m_regReturn, registry);
            }

        writePackedLong(out, m_nTupleRetValue);
        }

    @Override
    public int getOpCode()
        {
        return OP_NVOK_0T;
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

                return new Utils.GetTarget(ahTarget, stepLast).doNext(frame);
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
            return clz.f_template.invokeNativeN(frame, chain.getTop(), hTarget,
                    Utils.OBJECTS_NONE, -m_nTupleRetValue - 1);
            }

        ObjectHandle[] ahVar = new ObjectHandle[chain.getTop().getMaxVars()];

        return clz.f_template.invoke1(frame, chain, hTarget, ahVar, -m_nTupleRetValue - 1);
        }

    private int m_nTupleRetValue;

    private Register m_regReturn;
    }
