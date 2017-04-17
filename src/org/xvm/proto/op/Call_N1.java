package org.xvm.proto.op;

import org.xvm.proto.Frame;
import org.xvm.proto.ObjectHandle;
import org.xvm.proto.ObjectHandle.ExceptionHandle;
import org.xvm.proto.OpCallable;
import org.xvm.proto.TypeCompositionTemplate.FunctionTemplate;
import org.xvm.proto.Utils;

import org.xvm.proto.template.xFunction.FunctionHandle;

/**
 * CALL_N1 rvalue-function, #params:(rvalue) lvalue-return
 *
 * @author gg 2017.03.08
 */
public class Call_N1 extends OpCallable
    {
    private final int f_nFunctionValue;
    private final int[] f_anArgValue;
    private final int f_nRetValue;

    public Call_N1(int nFunction, int[] anArg, int nRet)
        {
        f_nFunctionValue = nFunction;
        f_anArgValue = anArg;
        f_nRetValue = nRet;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        ExceptionHandle hException;
        ObjectHandle[] ahReturn;

        if (f_nFunctionValue == A_SUPER)
            {
            Frame frameNew = createSuperCall(frame, f_anArgValue);

            ahReturn   = frameNew.f_ahReturn;
            hException = frameNew.execute();
            }
        else if (f_nFunctionValue >= 0)
            {
            FunctionHandle function = (FunctionHandle) frame.f_ahVar[f_nFunctionValue];

            hException = function.call(frame, frame.f_ahVar,
                    f_anArgValue, ahReturn = new ObjectHandle[1]);
            }
        else
            {
            FunctionTemplate function = getFunctionTemplate(frame, f_nFunctionValue);

            ObjectHandle[] ahVar = Utils.resolveArguments(frame, function, frame.f_ahVar, f_anArgValue);

            Frame frameNew = frame.f_context.createFrame(frame, function, null, ahVar);

            ahReturn   = frameNew.f_ahReturn;
            hException = frameNew.execute();
            }

        if (hException == null)
            {
            // TODO: match up
            frame.f_ahVar[f_nRetValue] = ahReturn[0];
            return iPC + 1;
            }
        else
            {
            frame.m_hException = hException;
            return RETURN_EXCEPTION;
            }
        }
    }