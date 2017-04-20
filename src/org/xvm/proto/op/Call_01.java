package org.xvm.proto.op;

import org.xvm.proto.Frame;
import org.xvm.proto.ObjectHandle;
import org.xvm.proto.ObjectHandle.ExceptionHandle;
import org.xvm.proto.OpCallable;
import org.xvm.proto.TypeCompositionTemplate.FunctionTemplate;
import org.xvm.proto.Utils;

import org.xvm.proto.template.xFunction.FunctionHandle;

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
        ExceptionHandle hException;

        if (f_nFunctionValue == A_SUPER)
            {
            hException = callSuper01(frame, frame.f_ahVar, f_nRetValue);
            }
        else if (f_nFunctionValue >= 0)
            {
            FunctionHandle function = (FunctionHandle) frame.f_ahVar[f_nFunctionValue];
            ObjectHandle[] ahReturn = new ObjectHandle[1];

            hException = function.call(frame, Utils.OBJECTS_NONE, ahReturn);
            frame.f_ahVar[f_nRetValue] = ahReturn[0];
            }
        else
            {
            FunctionTemplate function = getFunctionTemplate(frame, -f_nFunctionValue);

            ObjectHandle[] ahVar = new ObjectHandle[function.m_cVars];

            Frame frameNew = frame.f_context.createFrame(frame, function, null, ahVar);

            hException = frameNew.execute();
            frame.f_ahVar[f_nRetValue] = frameNew.f_ahReturn[0];
            }

        if (hException == null)
            {
            // TODO: match up the return type
            return iPC + 1;
            }
        else
            {
            frame.m_hException = hException;
            return RETURN_EXCEPTION;
            }
        }
    }
