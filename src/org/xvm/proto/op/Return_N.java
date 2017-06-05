package org.xvm.proto.op;

import org.xvm.proto.Frame;
import org.xvm.proto.Op;

/**
 * RETURN_N #vals:(rvalue)
 *
 * @author gg 2017.03.08
 */
public class Return_N extends Op
    {
    private final int[] f_anArgValue;

    public Return_N(int[] anValue)
        {
        f_anArgValue = anValue;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        int[] aiRet = frame.f_aiReturn;
        int cReturns = aiRet.length;

        // it's possible that the caller doesn't care about all the return values
        for (int i = 0; i < cReturns; i++)
            {
            int iArg = f_anArgValue[i];

            frame.f_framePrev.forceValue(aiRet[i],
                iArg >= 0 ? frame.f_ahVar[iArg] :
                iArg < -Op.MAX_CONST_ID ?
                    frame.getPredefinedArgument(iArg) :
                    frame.f_context.f_heapGlobal.ensureConstHandle(-iArg));
            }
        return R_RETURN;
        }
    }
