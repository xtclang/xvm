package org.xvm.proto.op;

import org.xvm.proto.TypeCompositionTemplate.FunctionTemplate;
import org.xvm.proto.*;

/**
 * NEW_1 op-code.
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
        FunctionTemplate constructor = getFunctionTemplate(frame, -f_nConstructId);

        TypeCompositionTemplate template = constructor.getClazzTemplate();

        ObjectHandle hNew = template.createStruct(frame);

        // call the constructor with this:struct and arg
        ObjectHandle[] ahVars = new ObjectHandle[constructor.m_cVars];
        ahVars[0] = hNew;
        ahVars[1] = f_nArgValue >= 0 ? frame.f_ahVars[f_nArgValue] :
                resolveConst(frame, constructor.m_argTypeName[1], f_nArgValue);

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
