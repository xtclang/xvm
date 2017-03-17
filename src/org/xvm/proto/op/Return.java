package org.xvm.proto.op;

import org.xvm.proto.Frame;
import org.xvm.proto.Op;

/**
 * RETURN op-code.
 *
 * @author gg 2017.03.08
 */
public class Return extends Op
    {
    public Return()
        {
        }

    @Override
    public int process(Frame frame, int iPC, int[] aiRegister, int[] anScopeNextVar)
        {
        return RETURN_NORMAL;
        }
    }
