package org.xvm.runtime.template.numbers;


import org.xvm.asm.ClassStructure;
import org.xvm.asm.MethodStructure;

import org.xvm.runtime.Container;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.JavaLong;


/**
 * Native Int64 support.
 */
public class xInt64
        extends xConstrainedInteger
    {
    public static xInt64 INSTANCE;

    public xInt64(Container container, ClassStructure structure, boolean fInstance)
        {
        super(container, structure, Long.MIN_VALUE, Long.MAX_VALUE, 64, false, false);

        if (fInstance)
            {
            INSTANCE = this;
            }
        }

    @Override
    protected xConstrainedInteger getComplimentaryTemplate()
        {
        return xUInt64.INSTANCE;
        }

    @Override
    public int invokeNativeN(Frame frame, MethodStructure method, ObjectHandle hTarget,
            ObjectHandle[] ahArg, int iReturn)
        {
        switch (method.getName())
            {
            case "rotateLeft":
                {
                long lValue = ((JavaLong) hTarget).getValue();
                long cBits  = ((JavaLong) ahArg[0]).getValue();
                lValue = Long.rotateLeft(lValue, (int) (cBits & 0x3F));
                return frame.assignValue(iReturn, makeJavaLong(lValue));
                }

            case "rotateRight":
                {
                long lValue = ((JavaLong) hTarget).getValue();
                long cBits  = ((JavaLong) ahArg[0]).getValue();
                lValue = Long.rotateRight(lValue, (int) (cBits & 0x3F));
                return frame.assignValue(iReturn, makeJavaLong(lValue));
                }

            case "reverseBits":
                {
                long lValue = ((JavaLong) hTarget).getValue();
                return frame.assignValue(iReturn, makeJavaLong(Long.reverse(lValue)));
                }

            case "reverseBytes":
                {
                long lValue = ((JavaLong) hTarget).getValue();
                return frame.assignValue(iReturn, makeJavaLong(Long.reverseBytes(lValue)));
                }
            }

        return super.invokeNativeN(frame, method, hTarget, ahArg, iReturn);
        }

    public static JavaLong makeHandle(long lValue)
        {
        return INSTANCE.makeJavaLong(lValue);
        }
    }