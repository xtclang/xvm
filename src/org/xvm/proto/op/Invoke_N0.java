package org.xvm.proto.op;

import org.xvm.asm.MethodStructure;

import org.xvm.proto.ClassTemplate;
import org.xvm.proto.ConstantPoolAdapter;
import org.xvm.proto.Frame;
import org.xvm.proto.ObjectHandle;
import org.xvm.proto.ObjectHandle.ExceptionHandle;
import org.xvm.proto.OpInvocable;
import org.xvm.proto.template.xFunction;
import org.xvm.proto.template.xService.ServiceHandle;

/**
 * INVOKE_N0  rvalue-target, CONST-METHOD, #params:(rvalue)
 *
 * @author gg 2017.03.08
 */
public class Invoke_N0 extends OpInvocable
    {
    private final int f_nTargetValue;
    private final int f_nMethodId;
    private final int[] f_anArgValue;

    public Invoke_N0(int nTarget, int nMethodId, int[] anArg)
        {
        f_nTargetValue = nTarget;
        f_nMethodId = nMethodId;
        f_anArgValue = anArg;
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

            ClassTemplate template = hTarget.f_clazz.f_template;
            MethodStructure method = getMethodStructure(frame, template, f_nMethodId);

            if (ConstantPoolAdapter.isNative(method))
                {
                ObjectHandle[] ahArg = frame.getArguments(f_anArgValue, ConstantPoolAdapter.getVarCount(method), 0);
                if (ahArg == null)
                    {
                    return R_REPEAT;
                    }

                return template.invokeNative(frame, hTarget, method, ahArg, Frame.RET_UNUSED);
                }

            ObjectHandle[] ahVar = frame.getArguments(f_anArgValue, ConstantPoolAdapter.getVarCount(method), 1);
            if (ahVar == null)
                {
                return R_REPEAT;
                }

            if (template.isService() && frame.f_context != ((ServiceHandle) hTarget).m_context)
                {
                ahVar[0] = hTarget;
                return xFunction.makeAsyncHandle(method).call1(frame, ahVar, Frame.RET_UNUSED);
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
