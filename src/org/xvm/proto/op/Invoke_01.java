package org.xvm.proto.op;

import org.xvm.proto.Frame;
import org.xvm.proto.ObjectHandle;
import org.xvm.proto.ObjectHandle.ExceptionHandle;
import org.xvm.proto.OpInvocable;
import org.xvm.proto.TypeCompositionTemplate;
import org.xvm.proto.TypeCompositionTemplate.MethodTemplate;
import org.xvm.proto.template.xFunction;

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

    @Override
    public int process(Frame frame, int iPC)
        {
        ObjectHandle hTarget = frame.f_ahVar[f_nTargetValue];

        TypeCompositionTemplate template = hTarget.f_clazz.f_template;

        MethodTemplate method = getMethodTemplate(frame, template, -f_nMethodId);

        ObjectHandle[] ahReturn;
        ExceptionHandle hException;

        if (method.isNative())
            {
            ahReturn = new ObjectHandle[1];
            hException = template.invokeNative01(frame, hTarget, method, ahReturn);
            }
        else if (template.isService())
            {
            hException = xFunction.makeAsyncHandle(method).
                    call(frame, new ObjectHandle[]{hTarget}, ahReturn = new ObjectHandle[1]);
            // TODO: match up
            }
        else
            {
            ObjectHandle[] ahVar = new ObjectHandle[method.m_cVars];
            Frame frameNew = frame.f_context.createFrame(frame, method, hTarget, ahVar);

            ahReturn = frameNew.f_ahReturn;
            hException = frameNew.execute();
            }

        if (hException == null)
            {
            frame.f_ahVar[f_nRetValue] = ahReturn[0];
            return iPC + 1;
            }
        else
            {
            frame.m_hException = hException;
            return RETURN_EXCEPTION;
            }
        }
    }
