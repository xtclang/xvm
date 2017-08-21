package org.xvm.proto.template;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Constant;
import org.xvm.asm.PropertyStructure;

import org.xvm.asm.constants.Int64Constant;

import org.xvm.proto.Frame;
import org.xvm.proto.ObjectHandle;
import org.xvm.proto.ObjectHandle.JavaLong;
import org.xvm.proto.ObjectHeap;
import org.xvm.proto.Op;
import org.xvm.proto.TypeComposition;
import org.xvm.proto.TypeSet;

import org.xvm.proto.template.collections.xIntArray;

/**
 * TODO:
 *
 * @author gg 2017.02.27
 */
public class xInt64
        extends Const
    {
    public static xInt64 INSTANCE;

    public xInt64(TypeSet types, ClassStructure structure, boolean fInstance)
        {
        super(types, structure, false);

        if (fInstance)
            {
            INSTANCE = this;

            // TODO: cache some often used numbers
            }
        }

    @Override
    public void initDeclared()
        {
        markNativeMethod("to", VOID, STRING);
        }

    @Override
    public ObjectHandle createConstHandle(Constant constant, ObjectHeap heap)
        {
        return constant instanceof Int64Constant ? new JavaLong(f_clazzCanonical,
                (((Int64Constant) constant).getValue().getLong())) : null;
        }

    @Override
    public int createArrayStruct(Frame frame, TypeComposition clzArray,
                                 long cCapacity, int iReturn)
        {
        if (cCapacity < 0 || cCapacity > Integer.MAX_VALUE)
            {
            frame.m_hException = xException.makeHandle("Invalid array size: " + cCapacity);
            return Op.R_EXCEPTION;
            }

        return frame.assignValue(iReturn, xIntArray.makeIntArrayInstance(cCapacity));
        }

    @Override
    public int invokeAdd(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        JavaLong hThis = (JavaLong) hTarget;
        JavaLong hThat = (JavaLong) hArg;

        // TODO: check overflow
        ObjectHandle hResult = makeHandle(hThis.getValue() + hThat.getValue());

        return frame.assignValue(iReturn, hResult);
        }

    @Override
    public int invokeNeg(Frame frame, ObjectHandle hTarget, int iReturn)
        {
        JavaLong hThis = (JavaLong) hTarget;

        // TODO: check overflow
        ObjectHandle hResult = makeHandle(-hThis.getValue());
        return frame.assignValue(iReturn, hResult);
        }

    @Override
    public int invokePreInc(Frame frame, ObjectHandle hTarget,
                            PropertyStructure property, int iReturn)
        {
        assert property == null;

        JavaLong hThis = (JavaLong) hTarget;

        // TODO: check overflow
        ObjectHandle hResult = makeHandle(hThis.getValue() + 1);

        return frame.assignValue(iReturn, hResult);
        }

    @Override
    public int invokePostInc(Frame frame, ObjectHandle hTarget,
                             PropertyStructure property, int iReturn)
        {
        return invokePreInc(frame, hTarget, property, iReturn);
        }

    @Override
    public int invokeNativeGet(Frame frame, ObjectHandle hTarget, PropertyStructure property, int iReturn)
        {
        JavaLong hThis = (JavaLong) hTarget;

        switch (property.getName())
            {
            case "hash":
                return frame.assignValue(iReturn, hThis);
            }

        return super.invokeNativeGet(frame, hTarget, property, iReturn);
        }

    @Override
    public int buildHashCode(Frame frame, ObjectHandle hTarget, int iReturn)
        {
        return frame.assignValue(iReturn, hTarget);
        }

    // ----- comparison support -----

    @Override
    public int callEquals(Frame frame, TypeComposition clazz,
                          ObjectHandle hValue1, ObjectHandle hValue2, int iReturn)
        {
        JavaLong h1 = (JavaLong) hValue1;
        JavaLong h2 = (JavaLong) hValue2;

        return frame.assignValue(iReturn, xBoolean.makeHandle(h1.getValue() == h2.getValue()));
        }

    @Override
    public int callCompare(Frame frame, TypeComposition clazz,
                           ObjectHandle hValue1, ObjectHandle hValue2, int iReturn)
        {
        JavaLong h1 = (JavaLong) hValue1;
        JavaLong h2 = (JavaLong) hValue2;

        return frame.assignValue(iReturn, xOrdered.makeHandle(h1.getValue() - h2.getValue()));
        }

    // ----- Object methods -----

    @Override
    public int buildStringValue(Frame frame, ObjectHandle hTarget, int iReturn)
        {
        JavaLong hThis = (JavaLong) hTarget;
        return frame.assignValue(iReturn, xString.makeHandle(String.valueOf(hThis.getValue())));
        }

    public static JavaLong makeHandle(long lValue)
        {
        return new JavaLong(INSTANCE.f_clazzCanonical, lValue);
        }
    }
