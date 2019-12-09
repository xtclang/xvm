package org.xvm.runtime.template.numbers;


import org.xvm.asm.ClassStructure;

import org.xvm.asm.Constant;
import org.xvm.asm.Op;

import org.xvm.asm.constants.UInt8Constant;

import org.xvm.runtime.ClassComposition;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle.JavaLong;
import org.xvm.runtime.TemplateRegistry;


/**
 * Native UInt8 (Byte) support.
 */
public class xUInt8
        extends xUnsignedConstrainedInt
    {
    public static xUInt8 INSTANCE;

    public xUInt8(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
        {
        super(templates, structure, 0, 255, 8, true);

        if (fInstance)
            {
            INSTANCE = this;

            // create unchecked template
            new xUncheckedUInt8(templates, structure, true);
            }
        }

    @Override
    public void initDeclared()
        {
        super.initDeclared();

        ClassComposition clz = getCanonicalClass();
        for (int i = 0; i < cache.length; ++i)
            {
            cache[i] = new JavaLong(clz, i);
            }
        }

    @Override
    protected xConstrainedInteger getComplimentaryTemplate()
        {
        return xInt8.INSTANCE;
        }

    @Override
    protected xUncheckedConstrainedInt getUncheckedTemplate()
        {
        return xUncheckedUInt8.INSTANCE;
        }

    @Override
    public int createConstHandle(Frame frame, Constant constant)
        {
        if (constant instanceof UInt8Constant)
            {
            frame.pushStack(
                makeHandle(((UInt8Constant) constant).getValue().longValue()));
            return Op.R_NEXT;
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
        return cache[(int) lValue];
        }

    private static final JavaLong[] cache = new JavaLong[256];
    }
