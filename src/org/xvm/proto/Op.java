package org.xvm.proto;


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

    // return values
    public static final int RETURN_NORMAL = -1;
    public static final int RETURN_EXCEPTION = -2;

    // ----- Op -----

    final int f_nGroup;

    protected Op(int nGroup)
        {
        f_nGroup = nGroup;
        }

    // returns a positive iPC or a negative RETURN_*
    public abstract int process(Frame frame, int iPC, int[] aiRegister, int[] anScopeNextVar);
    }
