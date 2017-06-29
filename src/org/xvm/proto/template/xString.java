package org.xvm.proto.template;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Constant;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.constants.CharStringConstant;

import org.xvm.proto.Frame;
import org.xvm.proto.ObjectHandle;
import org.xvm.proto.ObjectHandle.JavaLong;
import org.xvm.proto.ObjectHeap;
import org.xvm.proto.TypeComposition;
import org.xvm.proto.ClassTemplate;
import org.xvm.proto.TypeSet;

/**
 * TODO:
 *
 * @author gg 2017.02.27
 */
public class xString
        extends ClassTemplate
        implements ComparisonSupport
    {
    public static xString INSTANCE;

    public xString(TypeSet types, ClassStructure structure, boolean fInstance)
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
        //     Int length.get()

        markNativeGetter("length");
        markNativeMethod("indexOf", STRING);
        markNativeMethod("indexOf", new String[]{"x:String", "x:Int"});
        }

    @Override
    public ObjectHandle createConstHandle(Constant constant, ObjectHeap heap)
        {
        return constant instanceof CharStringConstant ? new StringHandle(f_clazzCanonical,
                ((CharStringConstant) constant).getValue()) : null;
        }

    @Override
    public boolean callEquals(ObjectHandle hValue1, ObjectHandle hValue2)
        {
        StringHandle h1 = (StringHandle) hValue1;
        StringHandle h2 = (StringHandle) hValue2;

        return h1.getValue().equals(h2.getValue());
        }

    @Override
    public int invokeNative(Frame frame, ObjectHandle hTarget, MethodStructure method,
                            ObjectHandle[] ahArg, int iReturn)
        {
        StringHandle hThis = (StringHandle) hTarget;

        switch (ahArg.length)
            {
            case 0:
                switch (method.getName())
                    {
                    case "length$get": // length.get()
                        ObjectHandle hResult = xInt64.makeHandle(hThis.m_sValue.length());
                        return frame.assignValue(iReturn, hResult);
                    }
                break;

            case 2:
                switch (method.getName())
                    {
                    case "indexOf": // indexOf(String s, Int n)
                        String s = ((StringHandle) ahArg[0]).getValue();
                        int n = (int) ((JavaLong) ahArg[1]).getValue();

                        ObjectHandle hResult = xInt64.makeHandle(hThis.m_sValue.indexOf(s, n));
                        return frame.assignValue(iReturn, hResult);
                    }
                break;

            }

        return super.invokeNative(frame, hTarget, method, ahArg, iReturn);
        }

    @Override
    public int invokeNative(Frame frame, ObjectHandle hTarget, MethodStructure method,
                            ObjectHandle hArg, int iReturn)
        {
        StringHandle hThis = (StringHandle) hTarget;

        switch (method.getName())
            {
            case "indexOf": // indexOf(String)
                if (hArg instanceof StringHandle)
                    {
                    int nOf = hThis.m_sValue.indexOf(((StringHandle) hArg).m_sValue);

                    ObjectHandle hResult = xInt64.makeHandle(nOf);
                    return frame.assignValue(iReturn, hResult);
                    }
            }

        return super.invokeNative(frame, hTarget, method, hArg, iReturn);
        }

    @Override
    public int invokeAdd(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        StringHandle hThis = (StringHandle) hTarget;
        StringHandle hThat = (StringHandle) hArg;

        return frame.assignValue(iReturn, makeHandle(hThis.m_sValue + hThat.m_sValue));
        }

    // ----- ComparisonSupport -----

    @Override
    public int compare(ObjectHandle hValue1, ObjectHandle hValue2)
        {
        StringHandle h1 = (StringHandle) hValue1;
        StringHandle h2 = (StringHandle) hValue2;

        return h1.getValue().compareTo(h2.getValue());
        }

    public static class StringHandle
            extends ObjectHandle
        {
        private String m_sValue = UNASSIGNED;

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
