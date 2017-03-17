package org.xvm.proto.op;

import org.xvm.proto.Frame;
import org.xvm.proto.Op;

/**
 * RETURN_1 op-code.
 *
 * @author gg 2017.03.08
 */
public class Return_1 extends Op
    {
    private final int f_nRetValue;

    public Return_1(int nRetValue)
        {
        f_nRetValue = nRetValue;
        }

    @Override
    public int process(Frame frame, int iPC, int[] aiRegister, int[] anScopeNextVar)
        {
        int nRetValue = f_nRetValue;

        frame.f_ahReturns[0] = nRetValue > 0 ?
                frame.f_ahVars[nRetValue] :
                frame.f_context.f_heap.ensureConstHandle(frame.f_anRetTypeId[0], -nRetValue);

        return RETURN_NORMAL;
        }
    }
