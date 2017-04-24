package org.xvm.proto.template;

import org.xvm.asm.Constant;
import org.xvm.asm.constants.CharStringConstant;

import org.xvm.proto.Frame;
import org.xvm.proto.ObjectHandle;
import org.xvm.proto.ObjectHandle.ExceptionHandle;
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
    public static xString INSTANCE;

    public xString(TypeSet types)
        {
        super(types, "x:String", "x:Object", Shape.Const);

        addImplement("x:collections.Sequence<x:Char>");

        INSTANCE = this;
        }

    @Override
    public void initDeclared()
        {
        //     Int length.get()

        PropertyTemplate pt;

        pt = ensurePropertyTemplate("length", "x:Int");
        pt.makeReadOnly();
        pt.addGet().markNative();

        ensureMethodTemplate("indexOf", STRING, INT).markNative();
        ensureMethodTemplate("indexOf", new String[]{"x:String", "x:Int"}, INT).markNative();
        }

    @Override
    public ObjectHandle createConstHandle(Constant constant)
        {
        return constant instanceof CharStringConstant ? new StringHandle(f_clazzCanonical,
                ((CharStringConstant) constant).getValue()) : null;
        }

    @Override
    public ExceptionHandle invokeNative(Frame frame, ObjectHandle hTarget, MethodTemplate method,
                                        ObjectHandle[] ahArg, int iReturn)
        {
        StringHandle hThis = (StringHandle) hTarget;

        switch (method.f_sName)
            {
            case "length$get": // length.get()
                assert ahArg.length == 0;

                ObjectHandle hResult = xInt64.makeHandle(hThis.m_sValue.length());
                return frame.assignValue(iReturn, hResult);
            }

        return super.invokeNative(frame, hTarget, method, ahArg, iReturn);
        }

    @Override
    public ExceptionHandle invokeNative(Frame frame, ObjectHandle hTarget, MethodTemplate method,
                                        ObjectHandle hArg, int iReturn)
        {
        StringHandle hThis = (StringHandle) hTarget;

        switch (method.f_sName)
            {
            case "indexOf": // indexOf(String)
                if (hArg instanceof StringHandle)
                    {
                    if (iReturn >= 0)
                        {
                        int nOf = hThis.m_sValue.indexOf(((StringHandle) hArg).m_sValue);

                        ObjectHandle hResult = xInt64.makeHandle(nOf);
                        return frame.assignValue(iReturn, hResult);
                        }
                    else
                        {
                        return null;
                        }
                    }
            }

        return super.invokeNative(frame, hTarget, method, hArg, iReturn);
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

        public String getValue()
            {
            return m_sValue;
            }

        @Override
        public String toString()
            {
            return super.toString() + m_sValue;
            }

        private final static String UNASSIGNED = "\0UNASSIGNED\0";
        }

    public static StringHandle makeHandle(String sValue)
        {
        return new StringHandle(INSTANCE.f_clazzCanonical, sValue);
        }
    }
