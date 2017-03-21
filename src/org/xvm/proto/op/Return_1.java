package org.xvm.proto.op;

import org.xvm.proto.Frame;
import org.xvm.proto.ObjectHandle;
import org.xvm.proto.Op;
import org.xvm.proto.TypeName;

/**
 * RETURN_1 op-code.
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
    public int process(Frame frame, int iPC, int[] aiRegister, int[] anScopeNextVar)
        {
        frame.f_ahReturns[0] = resolveArgument(frame, frame.f_function.m_retTypeName[0], f_nValue);

        return RETURN_NORMAL;
        }
    }
