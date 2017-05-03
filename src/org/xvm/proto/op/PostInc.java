package org.xvm.proto.op;

import org.xvm.proto.Frame;
import org.xvm.proto.ObjectHandle;
import org.xvm.proto.ObjectHandle.ExceptionHandle;
import org.xvm.proto.OpInvocable;
import org.xvm.proto.TypeCompositionTemplate;
import org.xvm.proto.TypeCompositionTemplate.PropertyTemplate;

/**
 * POSTINC lvalue-target, lvalue-return ; T++ -> T
 *
 * @author gg 2017.03.08
 */
public class PostInc extends OpInvocable
    {
    private final int f_nArgValue;
    private final int f_nRetValue;

    public PostInc(int nArg, int nRet)
        {
        f_nArgValue = nArg;
        f_nRetValue = nRet;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        ExceptionHandle hException;
        try
            {
            if (f_nArgValue >= 0)
                {
                // operation on a register
                ObjectHandle hTarget = frame.getArgument(f_nArgValue);

                hException = hTarget.f_clazz.f_template.
                        invokePostInc(frame, hTarget, null, f_nRetValue);
                }
            else
                {
                // operation on a local property
                ObjectHandle hTarget = frame.getThis();
                TypeCompositionTemplate template = hTarget.f_clazz.f_template;

                PropertyTemplate property = getPropertyTemplate(frame, template, -f_nArgValue);

                hException = hTarget.f_clazz.f_template.
                        invokePostInc(frame, hTarget, property, f_nRetValue);
                }
            }
        catch (ExceptionHandle.WrapperException e)
            {
            hException = e.getExceptionHandle();
            }

        if (hException == null)
            {
            return iPC + 1;
            }
        else
            {
            frame.m_hException = hException;
            return RETURN_EXCEPTION;
            }
        }
    }