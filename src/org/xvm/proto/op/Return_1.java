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
    private final int f_nValue;

    public Return_1(int nValue)
        {
        f_nValue = nValue;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        frame.f_ahReturns[0] = f_nValue >= 0 ? frame.f_ahVars[f_nValue] :
                resolveConstReturn(frame, 0, f_nValue);

        return RETURN_NORMAL;
        }
    }
