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
 * INVOKE_0T rvalue-target, CONST-METHOD, lvalue-return-tuple
 */
public class Invoke_0T
        extends OpInvocable
    {
    private final int f_nTargetValue;
    private final int f_nMethodId;
    private final int f_nTupleRetValue;

    public Invoke_0T(int nTarget, int nMethodId, int nRet)
        {
        f_nTargetValue = nTarget;
        f_nMethodId = nMethodId;
        f_nTupleRetValue = nRet;
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
        f_nTargetValue = readPackedInt(in);
        f_nMethodId = readPackedInt(in);
        f_nTupleRetValue = readPackedInt(in);
        }

    @Override
    public void write(DataOutput out)
            throws IOException
        {
        out.write(OP_INVOKE_0T);
        writePackedLong(out, f_nTargetValue);
        writePackedLong(out, f_nMethodId);
        writePackedLong(out, f_nTupleRetValue);
        }

    @Override
    public int getOpCode()
        {
        return OP_INVOKE_0T;
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
                return clz.f_template.invokeNativeN(frame, chain.getTop(), hTarget,
                        Utils.OBJECTS_NONE, -f_nTupleRetValue - 1);
                }

            ObjectHandle[] ahVar = new ObjectHandle[chain.getTop().getMaxVars()];

            return clz.f_template.invoke1(frame, chain, hTarget, ahVar, -f_nTupleRetValue - 1);
            }
        catch (ExceptionHandle.WrapperException e)
            {
            return frame.raiseException(e);
            }
        }
    }
