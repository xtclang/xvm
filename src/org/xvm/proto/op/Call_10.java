package org.xvm.proto.op;

import org.xvm.proto.Frame;
import org.xvm.proto.ObjectHandle;
import org.xvm.proto.OpCallable;
import org.xvm.proto.TypeCompositionTemplate.FunctionTemplate;
import org.xvm.proto.Utils;

/**
 * CALL_01 rvalue-function, rvalue-param
 *
 * @author gg 2017.03.08
 */
public class Call_10 extends OpCallable
    {
    private final int f_nFunctionValue;
    private final int f_nArgValue;

    public Call_10(int nFunction, int nArg)
        {
        f_nFunctionValue = nFunction;
        f_nArgValue = nArg;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        FunctionTemplate function = getFunctionTemplate(frame, f_nFunctionValue);

        ObjectHandle[] ahVars = new ObjectHandle[function.m_cVars];

        ahVars[0] = f_nArgValue >= 0 ? frame.f_ahVars[f_nArgValue] :
                resolveConst(frame, function.m_argTypeName[0], f_nArgValue);


        Frame frameNew = new Frame(frame.f_context, frame, null, function, ahVars);

        ObjectHandle hException = frameNew.execute();

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
