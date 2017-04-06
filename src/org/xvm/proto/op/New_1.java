package org.xvm.proto.op;

import org.xvm.proto.Frame;
import org.xvm.proto.ObjectHandle;
import org.xvm.proto.ObjectHandle.ExceptionHandle;
import org.xvm.proto.OpCallable;
import org.xvm.proto.TypeCompositionTemplate;
import org.xvm.proto.TypeCompositionTemplate.FunctionTemplate;
import org.xvm.proto.Utils;

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

        ObjectHandle hNew = template.createStruct(frame);

        // call the constructor with this:struct and arg
        ObjectHandle[] ahVar = new ObjectHandle[constructor.m_cVars];
        ahVar[0] = hNew;
        ahVar[1] = f_nArgValue >= 0 ? frame.f_ahVar[f_nArgValue] :
                Utils.resolveConst(frame, constructor.m_argTypeName[1], f_nArgValue);

        ExceptionHandle hException = callConstructor(frame, constructor, ahVar);

        if (hException == null)
            {
            frame.f_ahVar[f_nRetValue] = hNew;
            return iPC + 1;
            }
        else
            {
            frame.m_hException = hException;
            return RETURN_EXCEPTION;
            }
        }
    }
