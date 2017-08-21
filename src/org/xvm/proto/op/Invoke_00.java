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
 * INVOKE_00 rvalue-target, rvalue-method
 *
 * @author gg 2017.03.08
 */
public class Invoke_00 extends OpInvocable
    {
    private final int f_nTargetValue;
    private final int f_nMethodId;

    public Invoke_00(int nTarget, int nMethodId)
        {
        f_nTargetValue = nTarget;
        f_nMethodId = nMethodId;
        }

    public Invoke_00(DataInput in)
            throws IOException
        {
        f_nTargetValue = in.readInt();
        f_nMethodId = in.readInt();
        }

    @Override
    public void write(DataOutput out)
            throws IOException
        {
        out.write(OP_INVOKE_00);
        out.writeInt(f_nTargetValue);
        out.writeInt(f_nMethodId);
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

            TypeComposition clazz = hTarget.f_clazz;

            MethodStructure method = getMethodStructure(frame, clazz, f_nMethodId);

            if (frame.f_adapter.isNative(method))
                {
                return clazz.f_template.invokeNativeN(frame, method, hTarget,
                        Utils.OBJECTS_NONE, Frame.RET_UNUSED);
                }

            ObjectHandle[] ahVar = new ObjectHandle[frame.f_adapter.getVarCount(method)];

            if (clazz.f_template.isService() &&
                    frame.f_context != ((ServiceHandle) hTarget).m_context)
                {
                return Function.makeAsyncHandle(method).
                        call1(frame, hTarget, ahVar, Frame.RET_UNUSED);
                }

            return frame.call1(method, hTarget, ahVar, Frame.RET_UNUSED);
            }
        catch (ExceptionHandle.WrapperException e)
            {
            frame.m_hException = e.getExceptionHandle();
            return R_EXCEPTION;
            }
        }
    }
