package org.xvm.proto.op;

import org.xvm.proto.Frame;
import org.xvm.proto.ObjectHandle;
import org.xvm.proto.OpCallable;
import org.xvm.proto.TypeCompositionTemplate.FunctionTemplate;
import org.xvm.proto.Utils;

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
        FunctionTemplate function = getFunctionTemplate(frame, f_nFunctionValue);

        ObjectHandle[] ahVars = new ObjectHandle[function.m_cVars];
        ObjectHandle[] ahRet = Utils.OBJECTS_NONE;

        ObjectHandle hException = new Frame(frame.f_context, frame, null, function, ahVars, ahRet).execute();

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
