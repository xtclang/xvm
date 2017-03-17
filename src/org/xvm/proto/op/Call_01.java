package org.xvm.proto.op;

import org.xvm.proto.*;
import org.xvm.proto.TypeCompositionTemplate.FunctionTemplate;

/**
 * CALL_01 op-code.
 *
 * @author gg 2017.03.08
 */
public class Call_01 extends OpCallable
    {
    private final int f_nRetValue;

    public Call_01(int nFunction, int nRet)
        {
        super(nFunction);

        f_nRetValue = nRet;
        }

    @Override
    public int process(Frame frame, int iPC, int[] aiRegister, int[] anScopeNextVar)
        {
        FunctionTemplate function = getFunctionTemplate(frame);

        ObjectHandle[] ahVars = new ObjectHandle[function.m_cVars];
        ObjectHandle[] ahRet = new ObjectHandle[1];

        ObjectHandle hException = new Frame(frame.f_context, frame, null, function, ahVars, ahRet).execute();

        if (hException == null)
            {
            frame.f_ahVars[f_nRetValue] = ahRet[0];
            return iPC + 1;
            }
        else
            {
            frame.m_hException = hException;
            return RETURN_EXCEPTION;
            }
        }
    }
