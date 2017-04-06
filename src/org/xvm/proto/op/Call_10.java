package org.xvm.proto.op;

import org.xvm.proto.Frame;
import org.xvm.proto.ObjectHandle;
import org.xvm.proto.OpCallable;
import org.xvm.proto.ObjectHandle.ExceptionHandle;
import org.xvm.proto.TypeCompositionTemplate.FunctionTemplate;
import org.xvm.proto.Utils;

import org.xvm.proto.template.xFunction.FunctionHandle;

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
        ExceptionHandle hException;

        if (f_nFunctionValue == A_SUPER)
            {
            Frame frameNew = createSuperCall(frame, f_nArgValue);

            hException = frameNew.execute();
            }
        else if (f_nFunctionValue >= 0)
            {
            try
                {
                FunctionHandle function = frame.f_ahVar[f_nFunctionValue].as(FunctionHandle.class);

                hException = function.invoke(frame, frame.f_ahVar, new int[] {f_nArgValue}, Utils.OBJECTS_NONE);
                }
            catch (ExceptionHandle.WrapperException e)
                {
                hException = e.getExceptionHandle();
                }
            }
        else
            {
            FunctionTemplate function = getFunctionTemplate(frame, -f_nFunctionValue);

            ObjectHandle[] ahVar = new ObjectHandle[function.m_cVars];

            ahVar[0] = f_nArgValue >= 0 ? frame.f_ahVar[f_nArgValue] :
                    Utils.resolveConst(frame, function.m_argTypeName[0], f_nArgValue);

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
