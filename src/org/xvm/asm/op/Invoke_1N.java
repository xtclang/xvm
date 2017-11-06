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
 * NVOK_1N rvalue-target, CONST-METHOD, rvalue-param, #returns:(lvalue)
 */
public class Invoke_1N
        extends OpInvocable
    {
    /**
     * Construct an NVOK_1N op.
     *
     * @param nTarget    r-value that specifies the object on which the method being invoked
     * @param nMethodId  r-value that specifies the method being invoked
     * @param nArg       the r-value location of the method argument
     * @param anRet      the l-value locations for the results
     */
    public Invoke_1N(int nTarget, int nMethodId, int nArg, int[] anRet)
        {
        super((Argument) null, null);

        m_nTarget = nTarget;
        m_nMethodId = nMethodId;
        m_nArgValue = nArg;
        m_anRetValue = anRet;
        }

    /**
     * Construct an NVOK_1N op based on the passed arguments.
     *
     * @param argTarget    the target Argument
     * @param constMethod  the method constant
     * @param argValue     the value Argument
     * @param aArgReturn   the array of Registers to move the results into
     */
    public Invoke_1N(Argument argTarget, MethodConstant constMethod, Argument argValue, Argument[] aArgReturn)
        {
        super(argTarget, constMethod);

        m_argValue = argValue;
        m_aArgReturn = aArgReturn;
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public Invoke_1N(DataInput in, Constant[] aconst)
            throws IOException
        {
        super(in, aconst);

        m_nArgValue = readPackedInt(in);
        m_anRetValue = readIntArray(in);
        }

    @Override
    public void write(DataOutput out, ConstantRegistry registry)
            throws IOException
        {
        super.write(out, registry);

        if (m_argValue != null)
            {
            m_nArgValue = encodeArgument(m_argValue, registry);
            m_anRetValue = encodeArguments(m_aArgReturn, registry);
            }

        writePackedLong(out, m_nArgValue);
        writeIntArray(out, m_anRetValue);
        }

    @Override
    public int getOpCode()
        {
        return OP_NVOK_1N;
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
            ObjectHandle hArg = frame.getArgument(m_nArgValue);

            if (hTarget == null || hArg == null)
                {
                return R_REPEAT;
                }

            if (isProperty(hTarget))
                {
                ObjectHandle[] ahTarget = new ObjectHandle[] {hTarget};
                Frame.Continuation stepNext = frameCaller ->
                    resolveArg(frameCaller, ahTarget[0], hArg);

                return new Utils.GetArgument(ahTarget, stepNext).doNext(frame);
                }

            return resolveArg(frame, hTarget, hArg);
            }
        catch (ExceptionHandle.WrapperException e)
            {
            return frame.raiseException(e);
            }
        }

    protected int resolveArg(Frame frame, ObjectHandle hTarget, ObjectHandle hArg)
        {
        if (isProperty(hArg))
            {
            ObjectHandle[] ahArg = new ObjectHandle[] {hArg};
            Frame.Continuation stepNext = frameCaller -> complete(frameCaller, hTarget, ahArg[0]);

            return new Utils.GetArgument(ahArg, stepNext).doNext(frame);
            }

        return complete(frame, hTarget, hArg);
        }

    protected int complete(Frame frame, ObjectHandle hTarget, ObjectHandle hArg)
        {
        TypeComposition clz = hTarget.f_clazz;
        CallChain chain = getCallChain(frame, hTarget.f_clazz);
        MethodStructure method = chain.getTop();

        for (int i = 0, c = m_anRetValue.length; i < c; i++)
            {
            if (frame.isNextRegister(m_anRetValue[i]))
                {
                if (i == 0)
                    {
                    frame.introduceReturnVar(m_nTarget, method.getIdentityConstant());
                    }
                else
                    {
                    throw new UnsupportedOperationException();
                    }
                }
            }

        ObjectHandle[] ahVar = new ObjectHandle[method.getMaxVars()];
        ahVar[0] = hArg;

        return chain.isNative()
            ? clz.f_template.invokeNativeNN(frame, method, hTarget, ahVar, m_anRetValue)
            : clz.f_template.invokeN(frame, chain, hTarget, ahVar, m_anRetValue);
        }

    private int m_nArgValue;

    private Argument m_argValue;
    }
