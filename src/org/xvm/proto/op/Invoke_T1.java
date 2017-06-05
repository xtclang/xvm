package org.xvm.proto.op;

import org.xvm.proto.Frame;
import org.xvm.proto.ObjectHandle;
import org.xvm.proto.ObjectHandle.ExceptionHandle;
import org.xvm.proto.OpInvocable;
import org.xvm.proto.TypeCompositionTemplate;
import org.xvm.proto.TypeCompositionTemplate.MethodTemplate;

import org.xvm.proto.template.xException;
import org.xvm.proto.template.xFunction;
import org.xvm.proto.template.xService.ServiceHandle;
import org.xvm.proto.template.xTuple.TupleHandle;

/**
 * INVOKE_T1  rvalue-target, CONST-METHOD, rvalue-params-tuple, lvalue-return
 *
 * @author gg 2017.03.08
 */
public class Invoke_T1 extends OpInvocable
    {
    private final int f_nTargetValue;
    private final int f_nMethodId;
    private final int f_nArgTupleValue;
    private final int f_nRetValue;

    public Invoke_T1(int nTarget, int nMethodId, int nArg, int nRet)
        {
        f_nTargetValue = nTarget;
        f_nMethodId = nMethodId;
        f_nArgTupleValue = nArg;
        f_nRetValue = nRet;
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

            TypeCompositionTemplate template = hTarget.f_clazz.f_template;
            ObjectHandle[] ahArg = hArgTuple.m_ahValue;

            MethodTemplate method = getMethodTemplate(frame, template, f_nMethodId);

            if (method.isNative())
                {
                return template.invokeNative(frame, hTarget, method, ahArg, f_nRetValue);
                }

            if (ahArg.length != method.m_cArgs)
                {
                frame.m_hException = xException.makeHandle("Invalid tuple argument");
                }

            ObjectHandle[] ahVar = new ObjectHandle[method.m_cVars];
            System.arraycopy(ahArg, 0, ahVar, 1, ahArg.length);

            if (template.isService() && frame.f_context != ((ServiceHandle) hTarget).m_context)
                {
                ahVar[0] = hTarget;
                return xFunction.makeAsyncHandle(method).call1(frame, ahVar, f_nRetValue);
                }


            return frame.call1(method, hTarget, ahVar, f_nRetValue);
            }
        catch (ExceptionHandle.WrapperException e)
            {
            frame.m_hException = e.getExceptionHandle();
            return R_EXCEPTION;
            }
        }
    }
