package org.xvm.proto.op;

import org.xvm.proto.Frame;
import org.xvm.proto.ObjectHandle;
import org.xvm.proto.Op;

import java.util.Date;

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

        String sPrefix = new Date().toString() + " " + frame.f_context.toString() + ": ";
        if (nValue >= 0)
            {
            try
                {
                ObjectHandle handle = frame.f_ahVar[nValue].as(ObjectHandle.class);

                System.out.println(sPrefix + handle);
                }
            catch (ObjectHandle.ExceptionHandle.WrapperException e)
                {
                System.out.println(sPrefix  + " exception " + e.getExceptionHandle());
                }
            }
        else
            {
            System.out.println(sPrefix +
                    frame.f_context.f_constantPool.getConstantValue(-nValue).getValueString());
            }

        return iPC + 1;
        }
    }
