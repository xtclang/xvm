package org.xvm.proto.template;

import org.xvm.asm.Constant;
import org.xvm.asm.constants.IntConstant;
import org.xvm.proto.*;
import org.xvm.proto.ObjectHandle.ExceptionHandle;
import org.xvm.proto.ObjectHandle.JavaLong;

/**
 * TODO:
 *
 * @author gg 2017.02.27
 */
public class xInt64
        extends TypeCompositionTemplate
    {
    public static xInt64 INSTANCE;

    public xInt64(TypeSet types)
        {
        super(types, "x:Int64", "x:Object", Shape.Const);

        addImplement("x:IntNumber");

        INSTANCE = this;
        }

    @Override
    public void initDeclared()
        {
        f_types.addTemplate(new xIntArray(f_types));
        }

    @Override
    public ObjectHandle createHandle(TypeComposition clazz)
        {
        return new JavaLong(clazz);
        }

    @Override
    public ObjectHandle createConstHandle(Constant constant, ObjectHeap heap)
        {
        return constant instanceof IntConstant ? new JavaLong(f_clazzCanonical,
            (((IntConstant) constant).getValue().getLong())) : null;
        }

    @Override
    public int createArrayStruct(Frame frame, TypeComposition clazz,
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
                            PropertyTemplate property, int iReturn)
        {
        assert property == null;

        JavaLong hThis = (JavaLong) hTarget;

        // TODO: check overflow
        ObjectHandle hResult = makeHandle(hThis.getValue() + 1);

        return frame.assignValue(iReturn, hResult);
        }

    @Override
    public int invokePostInc(Frame frame, ObjectHandle hTarget,
                             PropertyTemplate property, int iReturn)
        {
        return invokePreInc(frame, hTarget, property, iReturn);
        }

    @Override
    public int invokeNative(Frame frame, ObjectHandle hTarget,
                            MethodTemplate method, ObjectHandle[] ahArg, int iReturn)
        {
        JavaLong hThis = (JavaLong) hTarget;

        switch (ahArg.length)
            {
            case 0:
                if (method.f_sName.equals("to"))
                    {
                    // how to differentiate; check the method's return type?
                    return frame.assignValue(iReturn,
                            xString.makeHandle(String.valueOf(hThis.getValue())));
                    }
            }
        return super.invokeNative(frame, hTarget, method, ahArg, iReturn);
        }
    }
