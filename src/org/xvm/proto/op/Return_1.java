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

        if (iRet >= 0 || iRet == Frame.RET_LOCAL)
            {
            return frame.returnValue(iRet, f_nArgValue);
            }

        switch (iRet)
            {
            case Frame.RET_UNUSED:
                return R_RETURN;

            case Frame.RET_MULTI:
                throw new IllegalStateException();

            default:
                return frame.returnTuple(-iRet - 1, new int[] {f_nArgValue});
            }
        }
    }
