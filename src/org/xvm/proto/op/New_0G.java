package org.xvm.proto.op;

import org.xvm.proto.Frame;
import org.xvm.proto.ObjectHandle;
import org.xvm.proto.ObjectHandle.ExceptionHandle;
import org.xvm.proto.OpCallable;
import org.xvm.proto.TypeComposition;
import org.xvm.proto.TypeCompositionTemplate;
import org.xvm.proto.TypeCompositionTemplate.ConstructTemplate;

import org.xvm.proto.template.xClass.ClassHandle;

/**
 * NEW_1G CONST-CONSTRUCT, rvalue-type, rvalue-param, lvalue-return
 *
 * @author gg 2017.03.08
 */
public class New_0G extends OpCallable
    {
    private final int f_nConstructId;
    private final int f_nTypeValue;
    private final int f_nRetValue;

    public New_0G(int nConstructorId, int nType, int nRet)
        {
        f_nConstructId = nConstructorId;
        f_nTypeValue = nType;
        f_nRetValue = nRet;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        ConstructTemplate constructor = (ConstructTemplate) getFunctionTemplate(frame, f_nConstructId);
        TypeCompositionTemplate template = constructor.getClazzTemplate();

        try
            {
            TypeComposition clzTarget;
            if (f_nTypeValue >= 0)
                {
                ClassHandle hClass = (ClassHandle) frame.getArgument(f_nTypeValue);
                if (hClass == null)
                    {
                    return R_REPEAT;
                    }
                clzTarget = hClass.f_clazz;
                }
            else
                {
                clzTarget = frame.f_context.f_types.ensureConstComposition(-f_nTypeValue);
                }

            ObjectHandle[] ahVar = new ObjectHandle[constructor.getVarCount()];

            return template.construct(frame, constructor, clzTarget, ahVar, f_nRetValue);
            }
        catch (ExceptionHandle.WrapperException e)
            {
            frame.m_hException = e.getExceptionHandle();
            return R_EXCEPTION;
            }
        }
    }