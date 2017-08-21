package org.xvm.proto.op;

import org.xvm.asm.MethodStructure;

import org.xvm.proto.Frame;
import org.xvm.proto.ObjectHandle;
import org.xvm.proto.ObjectHandle.ExceptionHandle;
import org.xvm.proto.OpInvocable;
import org.xvm.proto.TypeComposition;

import org.xvm.proto.template.Function;
import org.xvm.proto.template.Service.ServiceHandle;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * INVOKE_1T rvalue-target, CONST-METHOD, rvalue-param, lvalue-return-tuple
 *
 * @author gg 2017.03.08
 */
public class Invoke_1T extends OpInvocable
    {
    private final int f_nTargetValue;
    private final int f_nMethodId;
    private final int f_nArgValue;
    private final int f_nTupleRetValue;

    public Invoke_1T(int nTarget, int nMethodId, int nArg, int nRet)
        {
        f_nTargetValue = nTarget;
        f_nMethodId = nMethodId;
        f_nArgValue = nArg;
        f_nTupleRetValue = nRet;
        }

    public Invoke_1T(DataInput in)
            throws IOException
        {
        f_nTargetValue = in.readInt();
        f_nMethodId = in.readInt();
        f_nArgValue = in.readInt();
        f_nTupleRetValue = in.readInt();
        }

    @Override
    public void write(DataOutput out)
            throws IOException
        {
        out.write(OP_INVOKE_1T);
        out.writeInt(f_nTargetValue);
        out.writeInt(f_nMethodId);
        out.writeInt(f_nArgValue);
        out.writeInt(f_nTupleRetValue);
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        try
            {
            ObjectHandle hTarget = frame.getArgument(f_nTargetValue);
            ObjectHandle hArg = frame.getArgument(f_nArgValue);

            if (hTarget == null || hArg == null)
                {
                return R_REPEAT;
                }

            TypeComposition clz = hTarget.f_clazz;
            MethodStructure method = getMethodStructure(frame, clz, f_nMethodId);

            if (frame.f_adapter.isNative(method))
                {
                return clz.f_template.invokeNative1(frame, method, hTarget, hArg, -f_nTupleRetValue - 1);
                }

            ObjectHandle[] ahVar = new ObjectHandle[frame.f_adapter.getVarCount(method)];
            ahVar[0] = hArg;

            if (clz.f_template.isService() && frame.f_context != ((ServiceHandle) hTarget).m_context)
                {
                return Function.makeAsyncHandle(method).call1(frame, hTarget, ahVar, -f_nTupleRetValue - 1);
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
