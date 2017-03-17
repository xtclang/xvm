package org.xvm.proto.op;

import org.xvm.proto.Frame;
import org.xvm.proto.ObjectHandle;
import org.xvm.proto.Op;

/**
 * Debugging only.
 *
 * @author gg 2017.03.08
 */
public class X_Print extends Op
    {
    private final int f_nValue;

    public X_Print(int nValue)
        {
        f_nValue = nValue;
        }

    @Override
    public int process(Frame frame, int iPC, int[] aiRegister, int[] anScopeNextVar)
        {
        int nValue = f_nValue;

        ObjectHandle handle = frame.f_ahVars[nValue];

        System.out.println("################");
        System.out.println(handle);

        return RETURN_NORMAL;
        }
    }
