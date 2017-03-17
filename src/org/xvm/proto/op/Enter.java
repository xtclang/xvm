package org.xvm.proto.op;

import org.xvm.proto.Frame;
import org.xvm.proto.Op;

/**
 * ENTER op-code.
 *
 * @author gg 2017.03.08
 */
public class Enter extends Op
    {
    public Enter()
        {
        }

    @Override
    public int process(Frame frame, int iPC, int[] aiRegister, int[] anScopeNextVar)
        {
        int iScope = aiRegister[I_SCOPE];
        anScopeNextVar[iScope+1] = anScopeNextVar[iScope];
        aiRegister[I_SCOPE] = iScope+1;
        return iPC + 1;
        }
    }
