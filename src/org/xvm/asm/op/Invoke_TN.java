package org.xvm.asm.op;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.Constant;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.OpInvocable;

import org.xvm.runtime.CallChain;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.ExceptionHandle;
import org.xvm.runtime.TypeComposition;

import org.xvm.runtime.template.xException;

import org.xvm.runtime.template.collections.xTuple.TupleHandle;

import static org.xvm.util.Handy.readPackedInt;
import static org.xvm.util.Handy.writePackedLong;


/**
 * INVOKE_TN rvalue-target, CONST-METHOD, rvalue-params-tuple, #returns:(lvalue)
 */
public class Invoke_TN
        extends OpInvocable
    {
    /**
     * Construct an INVOKE_TN op.
     *
     * @param nTarget    r-value that specifies the object on which the method being invoked
     * @param nMethodId  r-value that specifies the method being invoked
     * @param nArg       the r-value location of the tuple of method arguments
     * @param anRet      the l-value locations for the results
     */
    public Invoke_TN(int nTarget, int nMethodId, int nArg, int [] anRet)
        {
        f_nTargetValue   = nTarget;
        f_nMethodId      = nMethodId;
        f_nArgTupleValue = nArg;
        f_anRetValue     = anRet;
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
        f_nTargetValue   = readPackedInt(in);
        f_nMethodId      = readPackedInt(in);
        f_nArgTupleValue = readPackedInt(in);
        f_anRetValue     = readIntArray(in);
        }

    @Override
    public void write(DataOutput out, ConstantRegistry registry)
            throws IOException
        {
        out.writeByte(OP_INVOKE_TN);
        writePackedLong(out, f_nTargetValue);
        writePackedLong(out, f_nMethodId);
        writePackedLong(out, f_nArgTupleValue);
        writeIntArray(out, f_anRetValue);
        }

    @Override
    public int getOpCode()
        {
        return OP_INVOKE_TN;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        try
            {
            ObjectHandle hTarget = frame.getArgument(f_nTargetValue);
            TupleHandle hArgTuple = (TupleHandle) frame.getArgument(f_nArgTupleValue);

            if (hTarget == null || hArgTuple == null)
                {
                return R_REPEAT;
                }

            TypeComposition clz = hTarget.f_clazz;
            ObjectHandle[] ahArg = hArgTuple.m_ahValue;

            CallChain chain = getCallChain(frame, clz, f_nMethodId);
            MethodStructure method = chain.getTop();

            if (chain.isNative())
                {
                return clz.f_template.invokeNativeNN(frame, method, hTarget, ahArg, f_anRetValue);
                }

            int cArgs = ahArg.length;
            int cVars = method.getMaxVars();

            if (cArgs != method.getParamCount())
                {
                return frame.raiseException(xException.makeHandle("Invalid tuple argument"));
                }

            ObjectHandle[] ahVar;
            if (cVars > cArgs)
                {
                ahVar = new ObjectHandle[cVars];
                System.arraycopy(ahArg, 0, ahVar, 0, cArgs);
                }
            else
                {
                ahVar = ahArg;
                }

            return clz.f_template.invokeN(frame, chain, hTarget, ahVar, f_anRetValue);
            }
        catch (ExceptionHandle.WrapperException e)
            {
            return frame.raiseException(e);
            }
        }

    private final int   f_nTargetValue;
    private final int   f_nMethodId;
    private final int   f_nArgTupleValue;
    private final int[] f_anRetValue;
    }
