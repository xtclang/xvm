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
import org.xvm.runtime.Utils;

import static org.xvm.util.Handy.readPackedInt;
import static org.xvm.util.Handy.writePackedLong;


/**
 * INVOKE_0N rvalue-target, CONST-METHOD, #returns:(lvalue)
 */
public class Invoke_0N
        extends OpInvocable
    {
     /**
     * Construct an INVOKE_0N op.
     *
     * @param nTarget    r-value that specifies the object on which the method being invoked
     * @param nMethodId  r-value that specifies the method being invoked
     * @param anRet      the l-value locations for the results
     */
    public Invoke_0N(int nTarget, int nMethodId, int[] anRet)
        {
        f_nTargetValue = nTarget;
        f_nMethodId    = nMethodId;
        f_anRetValue   = anRet;
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
        f_nTargetValue = readPackedInt(in);
        f_nMethodId    = readPackedInt(in);
        f_anRetValue   = readIntArray(in);
        }

    @Override
    public void write(DataOutput out, ConstantRegistry registry)
            throws IOException
        {
        out.writeByte(OP_INVOKE_0N);
        writePackedLong(out, f_nTargetValue);
        writePackedLong(out, f_nMethodId);
        writeIntArray(out, f_anRetValue);
        }

    @Override
    public int getOpCode()
        {
        return OP_INVOKE_0N;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        try
            {
            ObjectHandle hTarget = frame.getArgument(f_nTargetValue);
            if (hTarget == null)
                {
                return R_REPEAT;
                }

            TypeComposition clz = hTarget.f_clazz;
            CallChain chain = getCallChain(frame, clz, f_nMethodId);

            if (chain.isNative())
                {
                return clz.f_template.invokeNativeNN(frame, chain.getTop(), hTarget,
                        Utils.OBJECTS_NONE, f_anRetValue);
                }

            ObjectHandle[] ahVar = new ObjectHandle[chain.getTop().getMaxVars()];

            return clz.f_template.invokeN(frame, chain, hTarget, ahVar, f_anRetValue);
            }
        catch (ExceptionHandle.WrapperException e)
            {
            return frame.raiseException(e);
            }
        }

    private final int   f_nTargetValue;
    private final int   f_nMethodId;
    private final int[] f_anRetValue;
    }
