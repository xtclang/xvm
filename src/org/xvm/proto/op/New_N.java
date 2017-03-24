package org.xvm.proto.op;

import org.xvm.proto.*;
import org.xvm.proto.TypeCompositionTemplate.FunctionTemplate;

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
        FunctionTemplate constructor = getFunctionTemplate(frame, -f_nConstructId);

        TypeCompositionTemplate template = constructor.getClazzTemplate();

        ObjectHandle hNew = template.createStruct(frame);

        // call the constructor with this:struct and args
        ObjectHandle[] ahVars = new ObjectHandle[constructor.m_cVars];
        ahVars[0] = hNew;
        for (int i = 0, c = f_anArgValue.length; i < c; i++)
            {
            int nArg = f_anArgValue[i];

            ahVars[i + 1] = nArg >= 0 ? frame.f_ahVars[nArg] :
                    resolveConst(frame, constructor.m_argTypeName[i + 1], nArg);
            }

        ObjectHandle hException = callConstructor(frame, constructor, ahVars);

        if (hException == null)
            {
            frame.f_ahVars[f_nRetValue] = hNew;
            return iPC + 1;
            }
        else
            {
            frame.m_hException = hException;
            return RETURN_EXCEPTION;
            }
        }
    }
