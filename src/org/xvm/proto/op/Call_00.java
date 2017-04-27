package org.xvm.proto.op;

import org.xvm.proto.Frame;
import org.xvm.proto.ObjectHandle;
import org.xvm.proto.ObjectHandle.ExceptionHandle;
import org.xvm.proto.OpCallable;
import org.xvm.proto.TypeCompositionTemplate.FunctionTemplate;
import org.xvm.proto.TypeCompositionTemplate.MethodTemplate;
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
            // in-lined version of "createSuperCall"
            MethodTemplate methodSuper = ((MethodTemplate) frame.f_function).getSuper();

            ObjectHandle[] ahVar = new ObjectHandle[methodSuper.m_cVars];
            ObjectHandle hThis = frame.getThis();

            hException = frame.call1(methodSuper, hThis, ahVar, -1);
            }
        else if (f_nFunctionValue >= 0)
            {
            try
                {
                FunctionHandle hFunction = (FunctionHandle) frame.getArgument(f_nFunctionValue);

                hException = hFunction.call1(frame, Utils.OBJECTS_NONE, -1);
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

            hException = frame.call1(function, null, ahVar, -1);
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
