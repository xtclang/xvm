package org.xvm.proto.op;

import org.xvm.proto.Frame;
import org.xvm.proto.ObjectHandle;
import org.xvm.proto.OpCallable;
import org.xvm.proto.TypeCompositionTemplate.FunctionTemplate;
import org.xvm.proto.TypeCompositionTemplate.MethodTemplate;

/**
 * CALL_N0 rvalue-function, #params:(rvalue)
 *
 * @author gg 2017.03.08
 */
public class Call_N0 extends OpCallable
    {
    private final int f_nFunctionValue;
    private final int[] f_anArgValue;

    public Call_N0(int nFunction, int[] anArg)
        {
        f_nFunctionValue = nFunction;
        f_anArgValue = anArg;
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
            for (int i = 0, c = f_anArgValue.length; i < c; i++)
                {
                int nArg = f_anArgValue[i];

                ahVars[i + 1] = nArg >= 0 ? frame.f_ahVars[nArg] :
                        resolveConst(frame, methodSuper.m_argTypeName[i + 1], nArg);
                }

            frameNew = new Frame(frame.f_context, frame, hThis, methodSuper, ahVars);
            }
        else
            {
            FunctionTemplate function = getFunctionTemplate(frame, f_nFunctionValue);

            ObjectHandle[] ahVars = new ObjectHandle[function.m_cVars];

            for (int i = 0, c = f_anArgValue.length; i < c; i++)
                {
                int nArg = f_anArgValue[i];

                ahVars[i] = nArg >= 0 ? frame.f_ahVars[nArg] :
                        resolveConst(frame, function.m_argTypeName[i], nArg);
                }

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