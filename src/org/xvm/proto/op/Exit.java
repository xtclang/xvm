package org.xvm.proto.op;

import org.xvm.proto.Frame;
import org.xvm.proto.Op;

/**
 * EXIT op-code.
 *
 * @author gg 2017.03.08
 */
public class Exit extends Op
    {
    public Exit()
        {
        }

    @Override
    public int process(Frame frame, int iPC, int[] aiRegister, int[] anScopeNextVar)
        {
        int iScope = aiRegister[I_SCOPE];
        aiRegister[I_SCOPE] = --iScope;
        return iPC + 1;
        }
    }
