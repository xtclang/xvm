package org.xvm.proto.op;

import org.xvm.proto.Frame;
import org.xvm.proto.ObjectHandle;
import org.xvm.proto.ObjectHandle.ExceptionHandle;
import org.xvm.proto.OpInvocable;

/**
 * INC lvalue-target
 *
 * @author gg 2017.03.08
 */
public class Inc extends OpInvocable
    {
    private final int f_nArgValue;

    public Inc(int nArg)
        {
        f_nArgValue = nArg;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        ExceptionHandle hException;
        try
            {
            ObjectHandle hTarget = frame.getArgument(f_nArgValue);

            hException = hTarget.f_clazz.f_template.
                    invokeInc(frame, hTarget, frame.f_ahVar, f_nArgValue);
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