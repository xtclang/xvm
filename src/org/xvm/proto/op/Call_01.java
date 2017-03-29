package org.xvm.proto.op;

import org.xvm.proto.*;
import org.xvm.proto.TypeCompositionTemplate.FunctionTemplate;
import org.xvm.proto.TypeCompositionTemplate.MethodTemplate;

/**
 * CALL_01 rvalue-function, lvalue-return  ; TODO: return value can be into the next available register
 *
 * @author gg 2017.03.08
 */
public class Call_01 extends OpCallable
    {
    private final int f_nFunctionValue;
    private final int f_nRetValue;

    public Call_01(int nFunction, int nRet)
        {
        f_nFunctionValue = nFunction;
        f_nRetValue = nRet;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        Frame frameNew;

        if (f_nFunctionValue == A_SUPER)
            {
            ObjectHandle hThis = frame.f_ahVars[0];
            MethodTemplate methodSuper = ((MethodTemplate) frame.f_function).getSuper();

            ObjectHandle[] ahVars = new ObjectHandle[methodSuper.m_cVars];

            ahVars[0] = hThis;

            frameNew = new Frame(frame.f_context, frame, hThis, methodSuper, ahVars);
            }
        else
            {
            FunctionTemplate function = getFunctionTemplate(frame, f_nFunctionValue);

            ObjectHandle[] ahVars = new ObjectHandle[function.m_cVars];

            frameNew = new Frame(frame.f_context, frame, null, function, ahVars);
            }

        ObjectHandle hException = frameNew.execute();

        if (hException == null)
            {
            frame.f_ahVars[f_nRetValue] = frameNew.f_ahReturns[0];
            return iPC + 1;
            }
        else
            {
            frame.m_hException = hException;
            return RETURN_EXCEPTION;
            }
        }
    }
