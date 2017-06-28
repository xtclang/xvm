package org.xvm.proto.op;

import org.xvm.asm.PropertyStructure;

import org.xvm.proto.Frame;
import org.xvm.proto.ObjectHandle;
import org.xvm.proto.ObjectHandle.ExceptionHandle;
import org.xvm.proto.OpInvocable;
import org.xvm.proto.ClassTemplate;

/**
 * P_GET rvalue-target, CONST_PROPERTY, lvalue-return
 *
 * @author gg 2017.03.08
 */
public class PGet extends OpInvocable
    {
    private final int f_nTarget;
    private final int f_nPropConstId;
    private final int f_nRetValue;

    public PGet(int nTarget, int nPropId, int nRet)
        {
        f_nTarget = nTarget;
        f_nPropConstId = nPropId;
        f_nRetValue = nRet;
        }

    @Override
    public int process(Frame frame, int iPC)
        {

        try
            {
            ObjectHandle hTarget = frame.getArgument(f_nTarget);
            if (hTarget == null)
                {
                return R_REPEAT;
                }

            ClassTemplate template = hTarget.f_clazz.f_template;

            PropertyStructure property = getPropertyStructure(frame, template, f_nPropConstId);

            return template.getPropertyValue(frame, hTarget, property, f_nRetValue);
            }
        catch (ExceptionHandle.WrapperException e)
            {
            frame.m_hException = e.getExceptionHandle();
            return R_EXCEPTION;
            }
        }
    }
