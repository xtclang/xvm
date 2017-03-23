package org.xvm.proto.template;

import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.proto.Frame;
import org.xvm.proto.ObjectHandle;
import org.xvm.proto.TypeComposition;
import org.xvm.proto.TypeCompositionTemplate;
import org.xvm.proto.TypeSet;

/**
 * TODO:
 *
 * @author gg 2017.02.27
 */
public class xString
        extends TypeCompositionTemplate
    {
    public xString(TypeSet types)
        {
        super(types, "x:String", "x:Object", Shape.Const);

        INSTANCE = this;
        }

    @Override
    public void initDeclared()
        {
        addImplement("x:Sequence<x:Char>");

        //     Int length.get()

        addPropertyTemplate("length", "x:Int").makeReadOnly();

        addMethodTemplate("indexOf", STRING, INT).markNative();
        addMethodTemplate("indexOf", new String[]{"x:String", "x:Int"}, INT).markNative();
        }

    @Override
    public void assignConstValue(ObjectHandle handle, Constant constant)
        {
        StringHandle hThis = (StringHandle) handle;

        hThis.m_sValue = ((ConstantPool.CharStringConstant) constant).getValue();
        }

    @Override
    public ObjectHandle invokeNative01(Frame frame, ObjectHandle hTarget, MethodTemplate method, ObjectHandle[] ahReturn)
        {
        return null;
        }

    @Override
    public ObjectHandle invokeNative11(Frame frame, ObjectHandle hTarget, MethodTemplate method, ObjectHandle hArg, ObjectHandle[] ahReturn)
        {
        StringHandle hThis = (StringHandle) hTarget;
        switch (method.f_sName)
            {
            case "indexOf": // indexOf(String)
                if (hArg instanceof StringHandle)
                    {
                    int nOf = hThis.m_sValue.indexOf(((StringHandle) hArg).m_sValue);

                    ahReturn[0] = xInt64.makeHandle(nOf);
                    return null;
                    }
            }
        throw new IllegalStateException("Unknown method: " + method);
        }

    @Override
    public ObjectHandle createHandle(TypeComposition clazz)
        {
        return new StringHandle(clazz);
        }

    public static class StringHandle
            extends ObjectHandle
        {
        private String m_sValue = UNASSIGNED;

        protected StringHandle(TypeComposition clazz)
            {
            super(clazz);
            }
        protected StringHandle(TypeComposition clazz, String sValue)
            {
            super(clazz);

            m_sValue = sValue;
            }

        @Override
        public String toString()
            {
            return super.toString() + m_sValue;
            }

        private final static String UNASSIGNED = "\0UNASSIGNED\0";
        }

    public static xString INSTANCE;
    public static StringHandle makeHandle(String sValue)
        {
        return new StringHandle(INSTANCE.f_clazzCanonical, sValue);
        }
    }
