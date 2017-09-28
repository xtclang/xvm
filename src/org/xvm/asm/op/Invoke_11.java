package org.xvm.asm.op;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.Constant;
import org.xvm.asm.OpInvocable;

import org.xvm.runtime.CallChain;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.ExceptionHandle;
import org.xvm.runtime.TypeComposition;

import static org.xvm.util.Handy.readPackedInt;
import static org.xvm.util.Handy.writePackedLong;


/**
 * INVOKE_11 rvalue-target, rvalue-method, rvalue-param, lvalue-return
 */
public class Invoke_11
        extends OpInvocable
    {
    /**
     * Construct an INVOKE_11 op.
     *
     * @param nTarget    r-value that specifies the object on which the method being invoked
     * @param nMethodId  r-value that specifies the method being invoked
     * @param nArg       the r-value location of the method argument
     * @param nRet       the l-value location for the result
     */
    public Invoke_11(int nTarget, int nMethodId, int nArg, int nRet)
        {
        f_nTargetValue  = nTarget;
        f_nMethodId     = nMethodId;
        f_nArgValue     = nArg;
        f_nRetValue     = nRet;
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public Invoke_11(DataInput in, Constant[] aconst)
            throws IOException
        {
        f_nTargetValue  = readPackedInt(in);
        f_nMethodId     = readPackedInt(in);
        f_nArgValue     = readPackedInt(in);
        f_nRetValue     = readPackedInt(in);
        }

    @Override
    public void write(DataOutput out, ConstantRegistry registry)
            throws IOException
        {
        out.writeByte(OP_INVOKE_11);
        writePackedLong(out, f_nTargetValue);
        writePackedLong(out, f_nMethodId);
        writePackedLong(out, f_nArgValue);
        writePackedLong(out, f_nRetValue);
        }

    @Override
    public int getOpCode()
        {
        return OP_INVOKE_11;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        try
            {
            ObjectHandle hTarget = frame.getArgument(f_nTargetValue);
            ObjectHandle hArg = frame.getArgument(f_nArgValue);

            if (hTarget == null || hArg == null)
                {
                return R_REPEAT;
                }

            TypeComposition clz = hTarget.f_clazz;
            CallChain chain = getCallChain(frame, clz, f_nMethodId);

            if (chain.isNative())
                {
                return clz.f_template.invokeNative1(frame, chain.getTop(), hTarget,
                        hArg, f_nRetValue);
                }

            ObjectHandle[] ahVar = new ObjectHandle[chain.getTop().getMaxVars()];
            ahVar[0] = hArg;
            return clz.f_template.invoke1(frame, chain, hTarget, ahVar, f_nRetValue);

            }
        catch (ExceptionHandle.WrapperException e)
            {
            return frame.raiseException(e);
            }
        }

    private final int f_nTargetValue;
    private final int f_nMethodId;
    private final int f_nArgValue;
    private final int f_nRetValue;
    }
