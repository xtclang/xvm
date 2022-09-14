package org.xvm.runtime.template.text;


import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Constant;
import org.xvm.asm.MethodStructure;

import org.xvm.asm.constants.CharConstant;

import org.xvm.runtime.ClassComposition;

import org.xvm.runtime.Container;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.JavaLong;
import org.xvm.runtime.TypeComposition;

import org.xvm.runtime.template.xBoolean;
import org.xvm.runtime.template.xConst;
import org.xvm.runtime.template.xException;
import org.xvm.runtime.template.xOrdered;

import org.xvm.runtime.template.collections.xArray.ArrayHandle;
import org.xvm.runtime.template.collections.xByteArray;

import org.xvm.runtime.template.numbers.xUInt32;

import org.xvm.util.Handy;


/**
 * Native Char implementation.
 */
public class xChar
        extends xConst
    {
    public static xChar INSTANCE;

    public xChar(Container container, ClassStructure structure, boolean fInstance)
        {
        super(container, structure, false);

        if (fInstance)
            {
            INSTANCE = this;
            }
        }

    @Override
    public void initNative()
        {
        super.initNative();

        markNativeProperty("codepoint");

        getCanonicalType().invalidateTypeInfo();

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
    public boolean isGenericHandle()
        {
        return false;
        }

    @Override
    public int createConstHandle(Frame frame, Constant constant)
        {
        if (constant instanceof CharConstant constChar)
            {
            return frame.pushStack(new JavaLong(getCanonicalClass(), constChar.getValue()));
            }

        return super.createConstHandle(frame, constant);
        }

    @Override
    public int construct(Frame frame, MethodStructure constructor, TypeComposition clazz,
                         ObjectHandle hParent, ObjectHandle[] ahVar, int iReturn)
        {
        // there are three constructors take a JavaLong parameter (Byte, UInt32 and Int)
        // and one takes Byte[]
        ObjectHandle hArg = ahVar[0];
        if (hArg instanceof JavaLong hCodepoint)
            {
            return constructHandle(frame, hCodepoint.getValue(), iReturn);
            }

        if (hArg instanceof xString.StringHandle hText)
            {
            char[] ach = hText.getValue();
            if (ach.length != 1)
                {
                return frame.raiseException("illegal argument: String has length=" + ach.length);
                }

            return constructHandle(frame, ach[0], iReturn);
            }

        byte[] ab = xByteArray.getBytes((ArrayHandle) hArg);
        try
            {
            long lCodepoint =
                Handy.readUtf8Char(new DataInputStream(new ByteArrayInputStream(ab)));
            return constructHandle(frame, lCodepoint, iReturn);
            }
        catch (IOException e)
            {
            return frame.raiseException(xException.illegalUTF(frame, e.getMessage()));
            }
        }

    protected int constructHandle(Frame frame, long lCodepoint, int iReturn)
        {
        if (lCodepoint > 0x10FFFFL ||                       // unicode limit
            lCodepoint > 0xD7FFL && lCodepoint < 0xE000L)   // surrogate values are illegal
            {
            return frame.raiseException("illegal code-point: " + lCodepoint);
            }

        return frame.assignValue(iReturn, makeHandle(lCodepoint));
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


    // ----- comparison support --------------------------------------------------------------------

    @Override
    public int callEquals(Frame frame, TypeComposition clazz,
                          ObjectHandle hValue1, ObjectHandle hValue2, int iReturn)
        {
        JavaLong h1 = (JavaLong) hValue1;
        JavaLong h2 = (JavaLong) hValue2;

        return frame.assignValue(iReturn,
            xBoolean.makeHandle(h1.getValue() == h2.getValue()));
        }

    @Override
    public int callCompare(Frame frame, TypeComposition clazz,
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

    public static JavaLong makeHandle(long chValue)
        {
        assert chValue >= 0 & chValue <= 0x10FFFF;
        if (chValue < 128)
            {
            return INSTANCE.cache[(int)chValue];
            }
        return new JavaLong(INSTANCE.getCanonicalClass(), chValue);
        }

    private final JavaLong[] cache = new JavaLong[128];
    }