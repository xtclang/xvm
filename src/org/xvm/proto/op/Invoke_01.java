package org.xvm.proto.op;

import org.xvm.proto.CallChain;
import org.xvm.proto.Frame;
import org.xvm.proto.ObjectHandle;
import org.xvm.proto.ObjectHandle.ExceptionHandle;
import org.xvm.proto.OpInvocable;
import org.xvm.proto.TypeComposition;
import org.xvm.proto.Utils;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * INVOKE_01 rvalue-target, rvalue-method, lvalue-return
 *
 * @author gg 2017.03.08
 */
public class Invoke_01 extends OpInvocable
    {
    private final int f_nTargetValue;
    private final int f_nMethodId;
    private final int f_nRetValue;

    public Invoke_01(int nTarget, int nMethodId, int nRet)
        {
        f_nTargetValue = nTarget;
        f_nMethodId = nMethodId;
        f_nRetValue = nRet;
        }

    public Invoke_01(DataInput in)
            throws IOException
        {
        f_nTargetValue = in.readInt();
        f_nMethodId = in.readInt();
        f_nRetValue = in.readInt();
        }

    @Override
    public void write(DataOutput out)
            throws IOException
        {
        out.write(OP_INVOKE_01);
        out.writeInt(f_nTargetValue);
        out.writeInt(f_nMethodId);
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

            if (chain.isNative())
                {
                return clz.f_template.invokeNativeN(frame, chain.getTop(), hTarget,
                        Utils.OBJECTS_NONE, f_nRetValue);
                }

            ObjectHandle[] ahVar = new ObjectHandle[frame.f_adapter.getVarCount(chain.getTop())];

            return clz.f_template.invoke1(frame, chain, hTarget, ahVar, f_nRetValue);
            }
        catch (ExceptionHandle.WrapperException e)
            {
            return frame.raiseException(e);
            }
        }
    }
