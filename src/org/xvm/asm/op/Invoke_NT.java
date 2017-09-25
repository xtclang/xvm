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
 * INVOKE_NT rvalue-target, CONST-METHOD, #params:(rvalue), lvalue-return-tuple
 */
public class Invoke_NT
        extends OpInvocable
    {
    /**
     * Construct an INVOKE_NT op.
     *
     * @param nTarget    r-value that specifies the object on which the method being invoked
     * @param nMethodId  r-value that specifies the method being invoked
     * @param anArg      the r-value locations of the method arguments
     * @param nTupleRet  the l-value location for the tuple result
     */
    public Invoke_NT(int nTarget, int nMethodId, int[] anArg, int nTupleRet)
        {
        f_nTargetValue   = nTarget;
        f_nMethodId      = nMethodId;
        f_anArgValue     = anArg;
        f_nTupleRetValue = nTupleRet;
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public Invoke_NT(DataInput in, Constant[] aconst)
            throws IOException
        {
        f_nTargetValue   = readPackedInt(in);
        f_nMethodId      = readPackedInt(in);
        f_anArgValue     = readIntArray(in);
        f_nTupleRetValue = readPackedInt(in);
        }

    @Override
    public void write(DataOutput out)
            throws IOException
        {
        out.writeByte(OP_INVOKE_NT);
        writePackedLong(out, f_nTargetValue);
        writePackedLong(out, f_nMethodId);
        writeIntArray(out, f_anArgValue);
        writePackedLong(out, f_nTupleRetValue);
        }

    @Override
    public int getOpCode()
        {
        return OP_INVOKE_NT;
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

            ObjectHandle[] ahVar = frame.getArguments(f_anArgValue,
                    chain.getTop().getMaxVars());
            if (ahVar == null)
                {
                return R_REPEAT;
                }

            if (chain.isNative())
                {
                return clz.f_template.invokeNativeN(frame, chain.getTop(), hTarget, ahVar, -f_nTupleRetValue - 1);
                }

            return clz.f_template.invoke1(frame, chain, hTarget, ahVar, -f_nTupleRetValue - 1);
            }
        catch (ExceptionHandle.WrapperException e)
            {
            return frame.raiseException(e);
            }
        }

    private final int   f_nTargetValue;
    private final int   f_nMethodId;
    private final int[] f_anArgValue;
    private final int   f_nTupleRetValue;
    }
