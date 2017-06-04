package org.xvm.proto.op;

import org.xvm.proto.Frame;
import org.xvm.proto.ObjectHandle;
import org.xvm.proto.ObjectHandle.ExceptionHandle;
import org.xvm.proto.Op;

import org.xvm.proto.template.xTuple.TupleHandle;

/**
 * RETURN_T rvalue-tuple  ; return (a tuple of return values)
 *
 * @author gg 2017.03.08
 */
public class Return_T extends Op
    {
    private final int f_nArgValue;

    public Return_T(int nValue)
        {
        f_nArgValue = nValue;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        int[] aiRet = frame.f_aiReturn;
        int cReturns = aiRet.length;

        // it's possible that the caller doesn't care about the return value
        if (cReturns > 0)
            {
            try
                {
                TupleHandle hTuple = (TupleHandle) frame.getArgument(f_nArgValue);
                ObjectHandle[] ahValue = hTuple.m_ahValue;

                for (int i = 0; i < cReturns; i++)
                    {
                    frame.f_framePrev.forceValue(aiRet[i], ahValue[i]);
                    }
                }
            catch (ExceptionHandle.WrapperException e)
                {
                frame.m_hException = e.getExceptionHandle();
                return R_EXCEPTION;
                }
            }
        return R_RETURN;
        }
    }
