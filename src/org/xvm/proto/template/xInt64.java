package org.xvm.proto.template;

import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.proto.ObjectHandle;
import org.xvm.proto.ObjectHandle.JavaLong;
import org.xvm.proto.TypeComposition;
import org.xvm.proto.TypeCompositionTemplate;
import org.xvm.proto.TypeSet;

import org.xvm.util.PackedInteger;

/**
 * TODO:
 *
 * @author gg 2017.02.27
 */
public class xInt64
        extends TypeCompositionTemplate
    {
    public xInt64(TypeSet types)
        {
        super(types, "x:Int64", "x:Object", Shape.Const);

        INSTANCE = this;
        }

    @Override
    public void initDeclared()
        {
        addImplement("x:IntNumber");
        }

    @Override
    public ObjectHandle createHandle(TypeComposition clazz)
        {
        return new JavaLong(clazz);
        }

    @Override
    public void assignConstValue(ObjectHandle handle, Constant constant)
        {
        JavaLong hThis = (JavaLong) handle;

        hThis.assign(((ConstantPool.IntConstant) constant).getValue().getLong());
        }

    public static xInt64 INSTANCE;
    public static JavaLong makeHandle(long lValue)
        {
        return new JavaLong(INSTANCE.f_clazzCanonical, lValue);
        }
    }
