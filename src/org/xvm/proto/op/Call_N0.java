package org.xvm.proto.op;

import org.xvm.proto.Frame;
import org.xvm.proto.ObjectHandle;
import org.xvm.proto.ObjectHandle.ExceptionHandle;
import org.xvm.proto.OpCallable;
import org.xvm.proto.TypeCompositionTemplate.FunctionTemplate;
import org.xvm.proto.Utils;

import org.xvm.proto.template.xFunction.FunctionHandle;

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
        ExceptionHandle hException;

        if (f_nFunctionValue == A_SUPER)
            {
            Frame frameNew = createSuperCall(frame, f_anArgValue);

            hException = frameNew.execute();
            }
        else if (f_nFunctionValue >= 0)
            {
            FunctionHandle function = frame.f_ahVar[f_nFunctionValue].as(FunctionHandle.class);

            hException = function.invoke(frame, frame.f_ahVar, f_anArgValue, Utils.OBJECTS_NONE);
            }
        else
            {
            FunctionTemplate function = getFunctionTemplate(frame, -f_nFunctionValue);

            ObjectHandle[] ahVar = Utils.resolveArguments(frame, function, frame.f_ahVar, f_anArgValue);

            hException = frame.f_context.createFrame(frame, function, null, ahVar).execute();
            }

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