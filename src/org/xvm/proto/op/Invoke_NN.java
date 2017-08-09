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
 *INVOKE_NN rvalue-target, CONST-METHOD, #params:(rvalue), #returns:(lvalue)
 *
 * @author gg 2017.03.08
 */
public class Invoke_NN extends OpInvocable
    {
    private final int f_nTargetValue;
    private final int f_nMethodId;
    private final int[] f_anArgValue;
    private final int[] f_anRetValue;

    public Invoke_NN(int nTarget, int nMethodId, int[] anArg, int[] anRet)
        {
        f_nTargetValue = nTarget;
        f_nMethodId = nMethodId;
        f_anArgValue = anArg;
        f_anRetValue = anRet;
        }

    public Invoke_NN(DataInput in)
            throws IOException
        {
        f_nTargetValue = in.readInt();
        f_nMethodId = in.readInt();
        f_anArgValue = readIntArray(in);
        f_anRetValue = readIntArray(in);
        }

    @Override
    public void write(DataOutput out)
            throws IOException
        {
        out.write(OP_INVOKE_NN);
        out.writeInt(f_nTargetValue);
        out.writeInt(f_nMethodId);
        writeIntArray(out, f_anArgValue);
        writeIntArray(out, f_anRetValue);
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

            boolean fNative = frame.f_adapter.isNative(method);
            int cArgs = fNative ? f_anArgValue.length : frame.f_adapter.getVarCount(method);

            ObjectHandle[] ahVar = frame.getArguments(f_anArgValue, cArgs);

            if (ahVar == null)
                {
                return R_REPEAT;
                }

            if (fNative)
                {
                return clz.f_template.invokeNativeNN(frame, method, hTarget, ahVar, f_anRetValue);
                }

            if (clz.f_template.isService() && frame.f_context != ((ServiceHandle) hTarget).m_context)
                {
                return xFunction.makeAsyncHandle(method).callN(frame, hTarget, ahVar, f_anRetValue);
                }

            return frame.callN(method, hTarget, ahVar, f_anRetValue);
            }
        catch (ExceptionHandle.WrapperException e)
            {
            frame.m_hException = e.getExceptionHandle();
            return R_EXCEPTION;
            }
        }
    }
