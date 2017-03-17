package org.xvm.proto.template;

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
        addMethodTemplate("indexOf", STRING = new String[]{"x:String", "x:Int"}, INT).markNative();
        }

    @Override
    public void assignConstValue(ObjectHandle handle, Object oValue)
        {
        StringHandle hThis = (StringHandle) handle;

        hThis.m_sValue = (String) oValue;
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

                    ahReturn[0] = xInt64.makeCanonicalHandle(nOf);
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
        protected String m_sValue;

        protected StringHandle(TypeComposition clazz)
            {
            super(clazz, clazz.ensurePublicType());
            }

        @Override
        public String toString()
            {
            return super.toString() + m_sValue;
            }
        }

    public static xString INSTANCE;
    public static StringHandle makeCanonicalHandle(String sValue)
        {
        StringHandle h = new StringHandle(INSTANCE.f_clazzCanonical);
        h.m_sValue = sValue;
        return h;
        }
    }
