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
        if (f_nFunctionValue == A_SUPER)
            {
            // in-lined version of "callSuperN"
            MethodTemplate methodSuper = ((MethodTemplate) frame.f_function).getSuper();

            ObjectHandle[] ahVar = new ObjectHandle[methodSuper.m_cVars];

            return frame.call1(methodSuper, frame.getThis(), ahVar, Frame.RET_UNUSED);
            }

        if (f_nFunctionValue < 0)
            {
            FunctionTemplate function = getFunctionTemplate(frame, -f_nFunctionValue);

            ObjectHandle[] ahVar = new ObjectHandle[function.m_cVars];

            return frame.call1(function, null, ahVar, Frame.RET_UNUSED);
            }

        try
            {
            FunctionHandle hFunction = (FunctionHandle) frame.getArgument(f_nFunctionValue);
            if (hFunction == null)
                {
                return R_REPEAT;
                }

            return hFunction.call1(frame, Utils.OBJECTS_NONE, Frame.RET_UNUSED);
            }
        catch (ExceptionHandle.WrapperException e)
            {
            frame.m_hException = e.getExceptionHandle();
            return R_EXCEPTION;
            }
        }
    }
