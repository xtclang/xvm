package org.xvm.proto.op;

import org.xvm.proto.Frame;
import org.xvm.proto.Op;

/**
 * RETURN_1 rvalue
 *
 * @author gg 2017.03.08
 */
public class Return_1 extends Op
    {
    private final int f_nArgValue;

    public Return_1(int nValue)
        {
        f_nArgValue = nValue;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        int iRet = frame.f_iReturn;

        if (iRet >= 0)
            {
            frame.returnValue(iRet, f_nArgValue);
            }
        else
            {
            switch (iRet)
                {
                case Frame.R_LOCAL:
                    frame.returnValue(iRet, f_nArgValue);
                    break;

                case Frame.R_UNUSED:
                    break;

                case Frame.R_MULTI:
                    throw new IllegalStateException();

                default:
                    frame.returnTuple(-iRet - 1, new int[] {f_nArgValue});
                    break;
                }
            }
        return R_RETURN;
        }
    }
