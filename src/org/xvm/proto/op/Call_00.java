package org.xvm.proto.op;

import org.xvm.proto.Frame;
import org.xvm.proto.ObjectHandle;
import org.xvm.proto.ObjectHandle.ExceptionHandle;
import org.xvm.proto.OpCallable;
import org.xvm.proto.TypeCompositionTemplate.FunctionTemplate;
import org.xvm.proto.Utils;

import org.xvm.proto.template.xFunction.FunctionHandle;

/**
 * CALL_00 rvalue-function.
 *
 * @author gg 2017.03.08
 */
public class Call_00 extends OpCallable
    {
    private final int f_nFunctionValue;

    public Call_00(int nFunction)
        {
        f_nFunctionValue = nFunction;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        ExceptionHandle hException;

        if (f_nFunctionValue == A_SUPER)
            {
            Frame frameNew = createSuperCall(frame, Utils.ARGS_NONE);

            hException = frameNew.execute();
            }
        else if (f_nFunctionValue >= 0)
            {
            try
                {
                FunctionHandle hFunction = frame.f_ahVar[f_nFunctionValue].as(FunctionHandle.class);

                hException = hFunction.invoke(frame, Utils.OBJECTS_NONE, Utils.OBJECTS_NONE);
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
