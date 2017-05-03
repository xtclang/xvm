package org.xvm.proto.op;

import org.xvm.proto.Frame;
import org.xvm.proto.ObjectHandle;
import org.xvm.proto.ObjectHandle.ExceptionHandle;
import org.xvm.proto.OpCallable;
import org.xvm.proto.TypeCompositionTemplate;
import org.xvm.proto.TypeCompositionTemplate.ConstructTemplate;
import org.xvm.proto.template.xService;

/**
 * NEW_N CONST-CONSTRUCT, #params:(rvalue), lvalue-return
 *
 * @author gg 2017.03.08
 */
public class New_N extends OpCallable
    {
    private final int f_nConstructId;
    private final int[] f_anArgValue;
    private final int f_nRetValue;

    public New_N(int nConstructorId, int[] anArg, int nRet)
        {
        f_nConstructId = nConstructorId;
        f_anArgValue = anArg;
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
            ObjectHandle[] ahVar = frame.getArguments(f_anArgValue, constructor.getVarCount(), 1);

            if (template.isService())
                {
                hException = ((xService) template).asyncConstruct(frame, constructor,
                        template.f_clazzCanonical, ahVar, f_nRetValue);
                }
            else
                {
                hException = template.construct(frame, constructor,
                        template.f_clazzCanonical, ahVar, f_nRetValue);
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
