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
    public int process(Frame frame, int iPC)
        {
        int nValue = f_nValue;

        if (nValue >= 0)
            {
            ObjectHandle handle = frame.f_ahVar[nValue];

            System.out.println(handle);
            }
        else
            {
            System.out.println(frame.f_context.f_constantPool.getConstantValue(-nValue).getValueString());
            }

        return iPC + 1;
        }
    }
