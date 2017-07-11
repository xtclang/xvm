package org.xvm.proto.op;

import org.xvm.asm.PropertyStructure;

import org.xvm.proto.Frame;
import org.xvm.proto.ObjectHandle;
import org.xvm.proto.ObjectHandle.ExceptionHandle;
import org.xvm.proto.OpProperty;
import org.xvm.proto.TypeComposition;

/**
 * POSTINC lvalue-target, lvalue-return ; T++ -> T
 *
 * @author gg 2017.03.08
 */
public class PostInc extends OpProperty
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
        try
            {
            if (f_nArgValue >= 0)
                {
                // operation on a register
                ObjectHandle hTarget = frame.getArgument(f_nArgValue);
                if (hTarget == null)
                    {
                    return R_REPEAT;
                    }

                return hTarget.f_clazz.f_template.
                        invokePostInc(frame, hTarget, null, f_nRetValue);
                }
            else
                {
                // operation on a local property
                ObjectHandle hTarget = frame.getThis();
                TypeComposition clazz = hTarget.f_clazz;

                PropertyStructure property = getPropertyStructure(frame, clazz, -f_nArgValue);

                return hTarget.f_clazz.f_template.
                        invokePostInc(frame, hTarget, property, f_nRetValue);
                }
            }
        catch (ExceptionHandle.WrapperException e)
            {
            frame.m_hException = e.getExceptionHandle();
            return R_EXCEPTION;
            }
        }
    }