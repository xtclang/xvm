package org.xvm.runtime.template.numbers;


import org.xvm.asm.ClassStructure;

import org.xvm.asm.Constant;

import org.xvm.asm.constants.ByteConstant;

import org.xvm.runtime.ClassComposition;
import org.xvm.runtime.Container;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle.JavaLong;


/**
 * Native UInt8 (Byte) support.
 */
public class xUInt8
        extends xUnsignedConstrainedInt
    {
    public static xUInt8 INSTANCE;

    public xUInt8(Container container, ClassStructure structure, boolean fInstance)
        {
        super(container, structure, 0, 255, 8, false);

        if (fInstance)
            {
            INSTANCE = this;
            }
        }

    @Override
    public void registerNativeTemplates()
        {
        // create unchecked template
        registerNativeTemplate(new xUncheckedUInt8(f_container, f_struct, true));
        }

    @Override
    public void initNative()
        {
        super.initNative();

        if (this == INSTANCE)
            {
            ClassComposition clz = getCanonicalClass();
            for (int i = 0; i < cache.length; ++i)
                {
                cache[i] = new JavaLong(clz, i);
                }
            }
        }

    @Override
    protected xConstrainedInteger getComplimentaryTemplate()
        {
        return xInt8.INSTANCE;
        }

    @Override
    public int createConstHandle(Frame frame, Constant constant)
        {
        if (constant instanceof ByteConstant constByte)
            {
            return frame.pushStack(makeHandle(constByte.getValue().longValue()));
            }

        return super.createConstHandle(frame, constant);
        }

    @Override
    public JavaLong makeJavaLong(long lValue)
        {
        return makeHandle(lValue & 0xFFL);
        }

    public static JavaLong makeHandle(long lValue)
        {
        assert lValue >= 0 & lValue <= 255;
        return INSTANCE.cache[(int) lValue];
        }

    private final JavaLong[] cache = new JavaLong[256];
    }