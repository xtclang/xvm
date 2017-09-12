package org.xvm.proto.op;

import org.xvm.asm.MethodStructure;

import org.xvm.proto.Adapter;
import org.xvm.proto.CallChain;
import org.xvm.proto.Frame;
import org.xvm.proto.ObjectHandle;
import org.xvm.proto.ObjectHandle.ExceptionHandle;
import org.xvm.proto.OpInvocable;
import org.xvm.proto.TypeComposition;

import org.xvm.proto.template.collections.xTuple.TupleHandle;
import org.xvm.proto.template.xException;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * INVOKE_TN rvalue-target, CONST-METHOD, rvalue-params-tuple, #returns:(lvalue)
 *
 * @author gg 2017.03.08
 */
public class Invoke_TN extends OpInvocable
    {
    private final int f_nTargetValue;
    private final int f_nMethodId;
    private final int f_nArgTupleValue;
    private final int[] f_anRetValue;

    public Invoke_TN(int nTarget, int nMethodId, int nArg, int [] anRet)
        {
        f_nTargetValue = nTarget;
        f_nMethodId = nMethodId;
        f_nArgTupleValue = nArg;
        f_anRetValue = anRet;
        }

    public Invoke_TN(DataInput in)
            throws IOException
        {
        f_nTargetValue = in.readInt();
        f_nMethodId = in.readInt();
        f_nArgTupleValue = in.readInt();
        f_anRetValue = readIntArray(in);
        }

    @Override
    public void write(DataOutput out)
            throws IOException
        {
        out.write(OP_INVOKE_TN);
        out.writeInt(f_nTargetValue);
        out.writeInt(f_nMethodId);
        out.writeInt(f_nArgTupleValue);
        writeIntArray(out, f_anRetValue);
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
            int cVars = frame.f_adapter.getVarCount(method);

            if (cArgs != Adapter.getArgCount(method))
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
    }
