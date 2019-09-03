package org.xvm.runtime.template;


import org.xvm.asm.ClassStructure;
import org.xvm.asm.Constant;
import org.xvm.asm.Op;

import org.xvm.asm.constants.CharConstant;

import org.xvm.runtime.ClassComposition;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.JavaLong;
import org.xvm.runtime.TemplateRegistry;


/**
 * Native Char implementation.
 */
public class xChar
        extends xConst
    {
    public static xChar INSTANCE;

    public xChar(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
        {
        super(templates, structure, false);

        if (fInstance)
            {
            INSTANCE = this;
            }
        }

    @Override
    public void initDeclared()
        {
        super.initDeclared();

        markNativeProperty("codepoint");

        ClassComposition clz = getCanonicalClass();
        for (int i = 0; i < cache.length; ++i)
            {
            cache[i] = new JavaLong(clz, i);
            }
        }

    @Override
    public boolean isGenericHandle()
        {
        return false;
        }

    @Override
    public int createConstHandle(Frame frame, Constant constant)
        {
        if (constant instanceof CharConstant)
            {
            frame.pushStack(new JavaLong(getCanonicalClass(),
                    (((CharConstant) constant).getValue())));
            return Op.R_NEXT;
            }

        return super.createConstHandle(frame, constant);
        }

    @Override
    public int invokeNativeGet(Frame frame, String sPropName, ObjectHandle hTarget, int iReturn)
        {
        switch (sPropName)
            {
            case "codepoint":
                return frame.assignValue(iReturn,
                    xUInt32.INSTANCE.makeJavaLong(((JavaLong) hTarget).getValue()));
            }

        return super.invokeNativeGet(frame, sPropName, hTarget, iReturn);
        }

    @Override
    public int invokePrev(Frame frame, ObjectHandle hTarget, int iReturn)
        {
        long l = ((JavaLong) hTarget).getValue();

        if (l == Character.MIN_VALUE)
            {
            return overflow(frame);
            }

        return frame.assignValue(iReturn, makeHandle(l - 1));
        }

    @Override
    public int invokeNext(Frame frame, ObjectHandle hTarget, int iReturn)
        {
        long l = ((JavaLong) hTarget).getValue();

        if (l == Character.MAX_VALUE)
            {
            return overflow(frame);
            }

        return frame.assignValue(iReturn, makeHandle(l + 1));
        }


    // ----- comparison support -----

    @Override
    public int callEquals(Frame frame, ClassComposition clazz,
                          ObjectHandle hValue1, ObjectHandle hValue2, int iReturn)
        {
        JavaLong h1 = (JavaLong) hValue1;
        JavaLong h2 = (JavaLong) hValue2;

        return frame.assignValue(iReturn,
            xBoolean.makeHandle(h1.getValue() == h2.getValue()));
        }

    @Override
    public int callCompare(Frame frame, ClassComposition clazz,
                           ObjectHandle hValue1, ObjectHandle hValue2, int iReturn)
        {
        JavaLong h1 = (JavaLong) hValue1;
        JavaLong h2 = (JavaLong) hValue2;

        return frame.assignValue(iReturn,
            xOrdered.makeHandle(Long.compare(h1.getValue(), h2.getValue())));
        }

    @Override
    public boolean compareIdentity(ObjectHandle hValue1, ObjectHandle hValue2)
        {
        return ((JavaLong) hValue1).getValue() == ((JavaLong) hValue2).getValue();
        }

    // ----- helpers -----

    protected int overflow(Frame frame)
        {
        return frame.raiseException(xException.outOfBounds(frame, "Char overflow"));
        }

    public static JavaLong makeHandle(long chValue)
        {
        assert chValue >= 0 & chValue <= 0x10FFFF;
        if (chValue < 128)
            {
            return cache[(int)chValue];
            }
        return new JavaLong(INSTANCE.getCanonicalClass(), chValue);
        }

    private static final JavaLong[] cache = new JavaLong[128];
    }
