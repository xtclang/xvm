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

import org.xvm.runtime.template.xException;

import org.xvm.runtime.template.collections.xTuple.TupleHandle;

import static org.xvm.util.Handy.readPackedInt;
import static org.xvm.util.Handy.writePackedLong;


/**
 * NVOK_TN rvalue-target, CONST-METHOD, rvalue-params-tuple, #returns:(lvalue)
 */
public class Invoke_TN
        extends OpInvocable
    {
    /**
     * Construct an NVOK_TN op.
     *
     * @param nTarget    r-value that specifies the object on which the method being invoked
     * @param nMethodId  r-value that specifies the method being invoked
     * @param nArg       the r-value location of the tuple of method arguments
     * @param anRet      the l-value locations for the results
     *
     * @deprecated
     */
    public Invoke_TN(int nTarget, int nMethodId, int nArg, int [] anRet)
        {
        super((Argument) null, null);

        m_nTarget = nTarget;
        m_nMethodId = nMethodId;
        m_nArgTupleValue = nArg;
        m_anRetValue = anRet;
        }

    /**
     * Construct an NVOK_TN op based on the passed arguments.
     *
     * @param argTarget    the target Argument
     * @param constMethod  the method constant
     * @param argValue     the value Argument
     * @param aArgReturn   the array of Registers to move the results into
     */
    public Invoke_TN(Argument argTarget, MethodConstant constMethod, Argument argValue, Argument[] aArgReturn)
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
    public Invoke_TN(DataInput in, Constant[] aconst)
            throws IOException
        {
        super(in, aconst);

        m_nArgTupleValue = readPackedInt(in);
        m_anRetValue = readIntArray(in);
        }

    @Override
    public void write(DataOutput out, ConstantRegistry registry)
            throws IOException
        {
        super.write(out, registry);

        if (m_argValue != null)
            {
            m_nArgTupleValue = encodeArgument(m_argValue, registry);
            m_anRetValue = encodeArguments(m_aArgReturn, registry);
            }

        writePackedLong(out, m_nArgTupleValue);
        writeIntArray(out, m_anRetValue);
        }

    @Override
    public int getOpCode()
        {
        return OP_NVOK_TN;
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
            ObjectHandle hArg = frame.getArgument(m_nArgTupleValue);

            if (hTarget == null || hArg == null)
                {
                return R_REPEAT;
                }

            if (isProperty(hTarget))
                {
                ObjectHandle[] ahTarget = new ObjectHandle[] {hTarget};
                Frame.Continuation stepNext = frameCaller ->
                    resolveTuple(frameCaller, ahTarget[0], hArg);

                return new Utils.GetArgument(ahTarget, stepNext).doNext(frame);
                }

            return resolveTuple(frame, hTarget, hArg);
            }
        catch (ExceptionHandle.WrapperException e)
            {
            return frame.raiseException(e);
            }
        }

    protected int resolveTuple(Frame frame, ObjectHandle hTarget, ObjectHandle hArg)
        {
        // Tuple values cannot be local properties
        if (isProperty(hArg))
            {
            ObjectHandle[] ahArg = new ObjectHandle[] {hArg};
            Frame.Continuation stepNext = frameCaller ->
                complete(frameCaller, hTarget, ((TupleHandle) ahArg[0]).m_ahValue);

            return new Utils.GetArgument(ahArg, stepNext).doNext(frame);
            }

        return complete(frame, hTarget, ((TupleHandle) hArg).m_ahValue);
        }

    protected int complete(Frame frame, ObjectHandle hTarget, ObjectHandle[] ahArg)
        {
        TypeComposition clz = hTarget.f_clazz;
        CallChain chain = getCallChain(frame, clz);
        MethodStructure method = chain.getTop();

        if (ahArg.length != method.getParamCount())
            {
            return frame.raiseException(xException.makeHandle("Invalid tuple argument"));
            }

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

        return chain.isNative()
            ? clz.f_template.invokeNativeNN(frame, method, hTarget, ahArg, m_anRetValue)
            : clz.f_template.invokeN(frame, chain, hTarget,
                Utils.ensureSize(ahArg, method.getMaxVars()), m_anRetValue);
        }

    private int m_nArgTupleValue;

    private Argument m_argValue;
    }
