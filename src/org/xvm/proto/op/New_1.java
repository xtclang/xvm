package org.xvm.proto.op;

import org.xvm.proto.Frame;
import org.xvm.proto.ObjectHandle;
import org.xvm.proto.ObjectHandle.ExceptionHandle;
import org.xvm.proto.OpCallable;
import org.xvm.proto.TypeCompositionTemplate;
import org.xvm.proto.TypeCompositionTemplate.FunctionTemplate;

/**
 * NEW_1 CONST-CONSTRUCT, rvalue-param, lvalue-return
 *
 * @author gg 2017.03.08
 */
public class New_1 extends OpCallable
    {
    private final int f_nConstructId;
    private final int f_nArgValue;
    private final int f_nRetValue;

    public New_1(int nConstructorId, int nArg, int nRet)
        {
        f_nConstructId = nConstructorId;
        f_nArgValue = nArg;
        f_nRetValue = nRet;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        FunctionTemplate constructor = getFunctionTemplate(frame, f_nConstructId);

        TypeCompositionTemplate template = constructor.getClazzTemplate();

        ObjectHandle hNew = template.createStruct();

        // call the constructor with this:struct and arg
        ExceptionHandle hException;

        try
            {
            ObjectHandle[] ahVar = new ObjectHandle[constructor.m_cVars];
            ahVar[0] = hNew;
            ahVar[1] = frame.getArgument(f_nArgValue);

            hException = callConstructor(frame, constructor, ahVar);

            if (hException == null)
                {
                hException = frame.assignValue(f_nRetValue, ahVar[0]); // not the same as hNew
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
