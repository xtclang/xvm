package org.xvm.proto.op;

import org.xvm.proto.Frame;
import org.xvm.proto.Op;
import org.xvm.proto.Utils;

/**
 * RETURN_1 rvalue
 *
 * @author gg 2017.03.08
 */
public class Return_1 extends Op
    {
    private final int f_nValue;

    public Return_1(int nValue)
        {
        f_nValue = nValue;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        frame.f_ahReturn[0] = f_nValue >= 0 ? frame.f_ahVar[f_nValue] :
                Utils.resolveConst(frame, f_nValue);

        return RETURN_NORMAL;
        }
    }
