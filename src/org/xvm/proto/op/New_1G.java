package org.xvm.proto.op;

import org.xvm.proto.Frame;
import org.xvm.proto.ObjectHandle;
import org.xvm.proto.ObjectHandle.ExceptionHandle;
import org.xvm.proto.OpCallable;
import org.xvm.proto.TypeComposition;
import org.xvm.proto.TypeCompositionTemplate;
import org.xvm.proto.TypeCompositionTemplate.ConstructTemplate;

import org.xvm.proto.template.xClass.ClassHandle;
import org.xvm.proto.template.xService;

/**
 * NEW_1G CONST-CONSTRUCT, rvalue-type, rvalue-param, lvalue-return
 *
 * @author gg 2017.03.08
 */
public class New_1G extends OpCallable
    {
    private final int f_nConstructId;
    private final int f_nTypeValue;
    private final int f_nArgValue;
    private final int f_nRetValue;

    public New_1G(int nConstructorId, int nType, int nArg, int nRet)
        {
        f_nConstructId = nConstructorId;
        f_nTypeValue = nType;
        f_nArgValue = nArg;
        f_nRetValue = nRet;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        ConstructTemplate constructor = (ConstructTemplate) getFunctionTemplate(frame, f_nConstructId);
        TypeCompositionTemplate template = constructor.getClazzTemplate();

        ExceptionHandle hException;
        try
            {
            TypeComposition clzTarget;
            if (f_nTypeValue >= 0)
                {
                clzTarget = ((ClassHandle) frame.getArgument(f_nTypeValue)).f_clazz;
                }
            else
                {
                clzTarget = frame.f_context.f_types.ensureConstComposition(-f_nTypeValue);
                }

            ObjectHandle[] ahVar = new ObjectHandle[constructor.getVarCount()];
            ahVar[0] = frame.getArgument(f_nArgValue);

            if (template.isService())
                {
                hException = ((xService) template).asyncConstruct(
                        frame, constructor, clzTarget, ahVar, f_nRetValue);
                }
            else
                {
                hException = template.construct(
                        frame, constructor, clzTarget, ahVar, f_nRetValue);
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
