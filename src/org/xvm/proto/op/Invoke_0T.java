package org.xvm.proto.op;

import org.xvm.asm.MethodStructure;

import org.xvm.proto.Frame;
import org.xvm.proto.ObjectHandle;
import org.xvm.proto.ObjectHandle.ExceptionHandle;
import org.xvm.proto.OpInvocable;
import org.xvm.proto.TypeComposition;
import org.xvm.proto.Utils;

import org.xvm.proto.template.Function;
import org.xvm.proto.template.Service.ServiceHandle;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * INVOKE_0T rvalue-target, CONST-METHOD, lvalue-return-tuple
 *
 * @author gg 2017.03.08
 */
public class Invoke_0T extends OpInvocable
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

    public Invoke_0T(DataInput in)
            throws IOException
        {
        f_nTargetValue = in.readInt();
        f_nMethodId = in.readInt();
        f_nTupleRetValue = in.readInt();
        }

    @Override
    public void write(DataOutput out)
            throws IOException
        {
        out.write(OP_INVOKE_0T);
        out.writeInt(f_nTargetValue);
        out.writeInt(f_nMethodId);
        out.writeInt(f_nTupleRetValue);
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
            MethodStructure method = getMethodStructure(frame, clz, f_nMethodId);

            if (frame.f_adapter.isNative(method))
                {
                return clz.f_template.invokeNativeN(frame, method, hTarget,
                        Utils.OBJECTS_NONE, -f_nTupleRetValue - 1);
                }

            ObjectHandle[] ahVar = new ObjectHandle[frame.f_adapter.getVarCount(method)];

            if (clz.f_template.isService() && frame.f_context != ((ServiceHandle) hTarget).m_context)
                {
                return Function.makeAsyncHandle(method).
                        call1(frame, hTarget, ahVar, -f_nTupleRetValue - 1);
                }

            return frame.call1(method, hTarget, ahVar, -f_nTupleRetValue - 1);
            }
        catch (ExceptionHandle.WrapperException e)
            {
            frame.m_hException = e.getExceptionHandle();
            return R_EXCEPTION;
            }
        }
    }
