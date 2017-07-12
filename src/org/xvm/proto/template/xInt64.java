package org.xvm.proto.template;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Constant;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.PropertyStructure;
import org.xvm.asm.constants.IntConstant;
import org.xvm.proto.*;
import org.xvm.proto.ObjectHandle.JavaLong;

/**
 * TODO:
 *
 * @author gg 2017.02.27
 */
public class xInt64
        extends ClassTemplate
        implements ComparisonSupport
    {
    public static xInt64 INSTANCE;

    public xInt64(TypeSet types, ClassStructure structure, boolean fInstance)
        {
        super(types, structure);

        if (fInstance)
            {
            INSTANCE = this;
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
        return constant instanceof IntConstant ? new JavaLong(f_clazzCanonical,
            (((IntConstant) constant).getValue().getLong())) : null;
        }

    @Override
    public boolean callEquals(ObjectHandle hValue1, ObjectHandle hValue2)
        {
        JavaLong h1 = (JavaLong) hValue1;
        JavaLong h2 = (JavaLong) hValue2;

        return h1.getValue() == h2.getValue();
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

    public static JavaLong makeHandle(long lValue)
        {
        return new JavaLong(INSTANCE.f_clazzCanonical, lValue);
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
    public int invokeNative(Frame frame, MethodStructure method, ObjectHandle hTarget,
                            ObjectHandle[] ahArg, int iReturn)
        {
        JavaLong hThis = (JavaLong) hTarget;

        switch (ahArg.length)
            {
            case 0:
                if (method.getName().equals("to"))
                    {
                    // how to differentiate; check the method's return type?
                    return frame.assignValue(iReturn,
                            xString.makeHandle(String.valueOf(hThis.getValue())));
                    }
            }
        return super.invokeNative(frame, method, hTarget, ahArg, iReturn);
        }

    // ----- ComparisonSupport -----

    @Override
    public int compare(ObjectHandle hValue1, ObjectHandle hValue2)
        {
        JavaLong h1 = (JavaLong) hValue1;
        JavaLong h2 = (JavaLong) hValue2;

        return (int) (h1.getValue() - h2.getValue());
        }
    }
