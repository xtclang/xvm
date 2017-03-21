package org.xvm.proto.op;

import org.xvm.proto.TypeCompositionTemplate.FunctionTemplate;
import org.xvm.proto.*;
import org.xvm.proto.template.xFunction;

/**
 * NEW_1 op-code.
 *
 * @author gg 2017.03.08
 */
public class New_1 extends OpCallable
    {
    protected final int f_nConstructId;
    private final int f_nArgValue;
    private final int f_nRetValue;

    public New_1(int nConstructorId, int nArg, int nRet)
        {
        f_nConstructId = nConstructorId;
        f_nArgValue = nArg;
        f_nRetValue = nRet;
        }

    @Override
    public int process(Frame frame, int iPC, int[] aiRegister, int[] anScopeNextVar)
        {
        FunctionTemplate constructor = getFunctionTemplate(frame, -f_nConstructId);

        TypeCompositionTemplate template = constructor.getClazzTemplate();

        ObjectHandle hNew = template.createStruct();

        // call the constructor with this:struct and arg
        ObjectHandle[] ahVars = new ObjectHandle[constructor.m_cVars];
        ahVars[0] = hNew;
        ahVars[1] = resolveArgument(frame, constructor.m_argTypeName[1], f_nArgValue);

        int cReturns = constructor.m_cReturns;

        ObjectHandle[] ahRet = cReturns == 0 ? Utils.OBJECTS_NONE : new ObjectHandle[cReturns];

        ObjectHandle hException = new Frame(frame.f_context, frame, null, constructor, ahVars, ahRet).execute();

        if (hException == null)
            {
            template.toPublic(hNew);

            if (cReturns > 0)
                {
                xFunction.FunctionHandle hFinally = (xFunction.FunctionHandle) ahRet[0];

                // get the "finally" method from the handle; TODO: how to do it right?
                FunctionTemplate methFinally = (FunctionTemplate) hFinally.m_invoke;

                ahVars[0] = hNew;

                // call the finally method
                hException = new Frame(frame.f_context, frame, null, methFinally, ahVars, Utils.OBJECTS_NONE).execute();
                }

            if (hException == null)
                {
                frame.f_ahVars[f_nRetValue] = hNew;
                return iPC + 1;
                }
            }

        frame.m_hException = hException;
        return RETURN_EXCEPTION;
        }
    }
