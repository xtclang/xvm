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
        int iRet = frame.f_iReturn;
        if (iRet >= 0)
            {
            throw new IllegalStateException(); // assertion
            }

        switch (iRet)
            {
            case Frame.R_LOCAL:
                throw new IllegalStateException(); // assertion

            case Frame.R_UNUSED:
                break;

            case Frame.R_MULTI:
                int[] aiRet = frame.f_aiReturn;

                // it's possible that the caller doesn't care about some of the return values
                for (int i = 0, c = aiRet.length; i < c; i++)
                    {
                    frame.returnValue(aiRet[i], f_anArgValue[i]);
                    }
                break;

            default:
                // the caller needs a tuple
                frame.returnTuple(-iRet - 1, f_anArgValue);
                break;
            }
        return R_RETURN;
        }
    }
