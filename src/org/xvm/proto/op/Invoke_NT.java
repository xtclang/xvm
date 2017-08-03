package org.xvm.proto.op;

import org.xvm.asm.MethodStructure;

import org.xvm.proto.Frame;
import org.xvm.proto.ObjectHandle;
import org.xvm.proto.ObjectHandle.ExceptionHandle;
import org.xvm.proto.OpInvocable;
import org.xvm.proto.TypeComposition;

import org.xvm.proto.template.xFunction;
import org.xvm.proto.template.xService.ServiceHandle;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * INVOKE_NT rvalue-target, CONST-METHOD, #params:(rvalue), lvalue-return-tuple
 *
 * @author gg 2017.03.08
 */
public class Invoke_NT extends OpInvocable
    {
    private final int f_nTargetValue;
    private final int f_nMethodId;
    private final int[] f_anArgValue;
    private final int f_nTupleRetValue;

    public Invoke_NT(int nTarget, int nMethodId, int[] anArg, int nTupleRet)
        {
        f_nTargetValue = nTarget;
        f_nMethodId = nMethodId;
        f_anArgValue = anArg;
        f_nTupleRetValue = nTupleRet;
        }

    public Invoke_NT(DataInput in)
            throws IOException
        {
        f_nTargetValue = in.readInt();
        f_nMethodId = in.readInt();
        f_anArgValue = readIntArray(in);
        f_nTupleRetValue = in.readInt();
        }

    @Override
    public void write(DataOutput out)
            throws IOException
        {
        out.write(OP_INVOKE_NT);
        out.writeInt(f_nTargetValue);
        out.writeInt(f_nMethodId);
        writeIntArray(out, f_anArgValue);
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
                ObjectHandle[] ahArg = frame.getArguments(f_anArgValue, f_anArgValue.length);
                if (ahArg == null)
                    {
                    return R_REPEAT;
                    }
                return clz.f_template.invokeNativeN(frame, method, hTarget, ahArg, -f_nTupleRetValue - 1);
                }

            ObjectHandle[] ahVar = frame.getArguments(f_anArgValue, frame.f_adapter.getVarCount(method));
            if (ahVar == null)
                {
                return R_REPEAT;
                }

            if (clz.f_template.isService() && frame.f_context != ((ServiceHandle) hTarget).m_context)
                {
                return xFunction.makeAsyncHandle(method).call1(frame, hTarget, ahVar, -f_nTupleRetValue - 1);
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
