package org.xvm.proto.template;

import org.xvm.asm.Constant;
import org.xvm.asm.constants.IntConstant;
import org.xvm.proto.Frame;
import org.xvm.proto.ObjectHandle;
import org.xvm.proto.ObjectHandle.ExceptionHandle;
import org.xvm.proto.ObjectHandle.JavaLong;
import org.xvm.proto.TypeComposition;
import org.xvm.proto.TypeCompositionTemplate;
import org.xvm.proto.TypeSet;

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
        }

    @Override
    public ObjectHandle createHandle(TypeComposition clazz)
        {
        return new JavaLong(clazz);
        }

    @Override
    public ObjectHandle createConstHandle(Constant constant)
        {
        return constant instanceof IntConstant ? new JavaLong(f_clazzCanonical,
            (((IntConstant) constant).getValue().getLong())) : null;
        }

    @Override
    public ExceptionHandle invokeAdd(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        JavaLong hThis = (JavaLong) hTarget;
        JavaLong hThat = (JavaLong) hArg;

        // TODO: check overflow
        ObjectHandle hResult = makeHandle(hThis.getValue() + hThat.getValue());

        return frame.assignValue(iReturn, hResult);
        }

    @Override
    public ExceptionHandle invokeInc(Frame frame, ObjectHandle hTarget, int iReturn)
        {
        JavaLong hThis = (JavaLong) hTarget;

        // TODO: check overflow
        ObjectHandle hResult = makeHandle(hThis.getValue() + 1);

        return frame.assignValue(iReturn, hResult);
        }

    @Override
    public ExceptionHandle invokeNeg(Frame frame, ObjectHandle hTarget, int iReturn)
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
    }
