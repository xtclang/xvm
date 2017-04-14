package org.xvm.proto.op;

import org.xvm.proto.Frame;
import org.xvm.proto.ObjectHandle;
import org.xvm.proto.ObjectHandle.ExceptionHandle;
import org.xvm.proto.OpCallable;
import org.xvm.proto.TypeCompositionTemplate;
import org.xvm.proto.TypeCompositionTemplate.FunctionTemplate;
import org.xvm.proto.Utils;

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
        FunctionTemplate constructor = getFunctionTemplate(frame, f_nConstructId);

        TypeCompositionTemplate template = constructor.getClazzTemplate();

        ObjectHandle hNew = template.createStruct();

        // call the constructor with this:struct and args
        ObjectHandle[] ahVar = new ObjectHandle[constructor.m_cVars];
        ahVar[0] = hNew;
        for (int i = 0, c = f_anArgValue.length; i < c; i++)
            {
            int nArg = f_anArgValue[i];

            ahVar[i + 1] = nArg >= 0 ? frame.f_ahVar[nArg] : Utils.resolveConst(frame, nArg);
            }

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
