package org.xvm.runtime.template;


import org.xvm.asm.ClassStructure;
import org.xvm.asm.MethodStructure;

import org.xvm.runtime.ClassComposition;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.JavaLong;
import org.xvm.runtime.TemplateRegistry;


/**
 * Native Int64 support.
 */
public class xInt64
        extends xConstrainedInteger
    {
    public static xInt64 INSTANCE;

    public xInt64(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
        {
        super(templates, structure, Long.MIN_VALUE, Long.MAX_VALUE, 64, false, true);

        if (fInstance)
            {
            INSTANCE = this;

            if (cache[0] == null)
                {
                ClassComposition clz = getCanonicalClass();
                for (int i = 0; i < cache.length; ++i)
                    {
                    cache[i] = new JavaLong(clz, i);
                    }
                }
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
        if (lValue == (lValue & 0x7F))
            {
            return cache[(int)lValue];
            }
        return INSTANCE.makeJavaLong(lValue);
        }

    private static final JavaLong[] cache = new JavaLong[128];
    }
