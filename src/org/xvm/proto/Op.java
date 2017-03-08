package org.xvm.proto;

import org.xvm.proto.op.Enter;

/**
 * TODO:
 *
 * @author gg 2017.02.21
 */
public abstract class Op
    {
    public static final int GROUP_1 = 1;
    public static final int GROUP_2 = 2;

    // execution-registers; [0] = iScope
    public static final int I_SCOPE = 0;

    // ----- Op -----

    final int f_nGroup;

    protected Op(int nGroup)
        {
        f_nGroup = nGroup;
        }

    public abstract int process(Frame frame, int iPC, int[] aiRegister, int[] anScopeNextVar);
    }
