package org.xvm.proto.op;

import org.xvm.proto.CallChain;
import org.xvm.proto.Frame;
import org.xvm.proto.ObjectHandle;
import org.xvm.proto.ObjectHandle.ExceptionHandle;
import org.xvm.proto.OpInvocable;
import org.xvm.proto.TypeComposition;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * INVOKE_N1  rvalue-target, CONST-METHOD, #params:(rvalue), lvalue-return
 *
 * @author gg 2017.03.08
 */
public class Invoke_N1 extends OpInvocable
    {
    private final int f_nTargetValue;
    private final int f_nMethodId;
    private final int[] f_anArgValue;
    private final int f_nRetValue;

    public Invoke_N1(int nTarget, int nMethodId, int[] anArg, int nRet)
        {
        f_nTargetValue = nTarget;
        f_nMethodId = nMethodId;
        f_anArgValue = anArg;
        f_nRetValue = nRet;
        }

    public Invoke_N1(DataInput in)
            throws IOException
        {
        f_nTargetValue = in.readInt();
        f_nMethodId = in.readInt();
        f_anArgValue = readIntArray(in);
        f_nRetValue = in.readInt();
        }

    @Override
    public void write(DataOutput out)
            throws IOException
        {
        out.write(OP_INVOKE_N1);
        out.writeInt(f_nTargetValue);
        out.writeInt(f_nMethodId);
        writeIntArray(out, f_anArgValue);
        out.writeInt(f_nRetValue);
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
                    frame.f_adapter.getVarCount(chain.getTop()));
            if (ahVar == null)
                {
                return R_REPEAT;
                }

            if (chain.isNative())
                {
                return clz.f_template.invokeNativeN(frame, chain.getTop(), hTarget,
                        ahVar, f_nRetValue);
                }

            return clz.f_template.invoke1(frame, chain, hTarget, ahVar, f_nRetValue);
            }
        catch (ExceptionHandle.WrapperException e)
            {
            return frame.raiseException(e);
            }
        }
    }
