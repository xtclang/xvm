package org.xvm.proto.op;

import org.xvm.proto.Frame;
import org.xvm.proto.Op;

/**
 * RETURN_0 ; (no return value)
 *
 * @author gg 2017.03.08
 */
public class Return_0 extends Op
    {
    public static final Return_0 INSTANCE = new Return_0();

    public Return_0()
        {
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        return R_RETURN;
        }
    }
