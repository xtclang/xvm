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
 * INVOKE_T1  rvalue-target, CONST-METHOD, rvalue-params-tuple, lvalue-return
 */
public class Invoke_T1
        extends OpInvocable
    {
    /**
     * Construct an INVOKE_T1 op.
     *
     * @param nTarget    r-value that specifies the object on which the method being invoked
     * @param nMethodId  r-value that specifies the method being invoked
     * @param nArg       the r-value location of the tuple of method arguments
     * @param nRet       the l-value location for the result
     */
    public Invoke_T1(int nTarget, int nMethodId, int nArg, int nRet)
        {
        f_nTargetValue   = nTarget;
        f_nMethodId      = nMethodId;
        f_nArgTupleValue = nArg;
        f_nRetValue      = nRet;
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public Invoke_T1(DataInput in, Constant[] aconst)
            throws IOException
        {
        f_nTargetValue   = readPackedInt(in);
        f_nMethodId      = readPackedInt(in);
        f_nArgTupleValue = readPackedInt(in);
        f_nRetValue      = readPackedInt(in);
        }

    @Override
    public void write(DataOutput out)
            throws IOException
        {
        out.writeByte(OP_INVOKE_T1);
        writePackedLong(out, f_nTargetValue);
        writePackedLong(out, f_nMethodId);
        writePackedLong(out, f_nArgTupleValue);
        writePackedLong(out, f_nRetValue);
        }

    @Override
    public int getOpCode()
        {
        return OP_INVOKE_T1;
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
                return clz.f_template.invokeNativeN(frame, method, hTarget, ahArg, f_nRetValue);
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

            return clz.f_template.invoke1(frame, chain, hTarget, ahVar, f_nRetValue);
            }
        catch (ExceptionHandle.WrapperException e)
            {
            return frame.raiseException(e);
            }
        }

    private final int f_nTargetValue;
    private final int f_nMethodId;
    private final int f_nArgTupleValue;
    private final int f_nRetValue;
    }
