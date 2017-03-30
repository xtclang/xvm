package org.xvm.proto.op;

import org.xvm.proto.Frame;
import org.xvm.proto.ObjectHandle;
import org.xvm.proto.OpCallable;
import org.xvm.proto.TypeCompositionTemplate.FunctionTemplate;
import org.xvm.proto.TypeCompositionTemplate.MethodTemplate;

/**
 * CALL_10 rvalue-function, rvalue-param
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
        Frame frameNew;

        if (f_nFunctionValue == A_SUPER)
            {
            MethodTemplate methodSuper = ((MethodTemplate) frame.f_function).getSuper();

            ObjectHandle[] ahVars = new ObjectHandle[methodSuper.m_cVars];

            ObjectHandle hThis = frame.f_ahVars[0];
            ahVars[0] = hThis;
            ahVars[1] = f_nArgValue >= 0 ? frame.f_ahVars[f_nArgValue] :
                    resolveConst(frame, methodSuper.m_argTypeName[1], f_nArgValue);

            frameNew = new Frame(frame.f_context, frame, hThis, methodSuper, ahVars);
            }
        else
            {
            FunctionTemplate function = getFunctionTemplate(frame, f_nFunctionValue);

            ObjectHandle[] ahVars = new ObjectHandle[function.m_cVars];

            ahVars[0] = f_nArgValue >= 0 ? frame.f_ahVars[f_nArgValue] :
                    resolveConst(frame, function.m_argTypeName[0], f_nArgValue);

            frameNew = new Frame(frame.f_context, frame, null, function, ahVars);
            }

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
