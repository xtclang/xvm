package org.xvm.runtime.template;


import org.xvm.asm.ClassStructure;
import org.xvm.asm.Constant;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;
import org.xvm.asm.PropertyStructure;

import org.xvm.asm.constants.StringConstant;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.JavaLong;
import org.xvm.runtime.TypeComposition;
import org.xvm.runtime.TemplateRegistry;

import org.xvm.runtime.template.xRef.RefHandle;

/**
 * TODO:
 */
public class xString
        extends xConst
    {
    public static xString INSTANCE;

    public xString(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
        {
        super(templates, structure, false);

        if (fInstance)
            {
            INSTANCE = this;
            }
        }

    @Override
    public void initDeclared()
        {
        markNativeGetter("size");
        markNativeMethod("indexOf", new String[]{"String", "Range<Int64>?"},
                new String[]{"Boolean", "Int64"});
        markNativeMethod("to", VOID, STRING);
        }

    @Override
    public ObjectHandle createConstHandle(Frame frame, Constant constant)
        {
        return constant instanceof StringConstant ? new StringHandle(ensureCanonicalClass(),
                ((StringConstant) constant).getValue()) : null;
        }

    @Override
    public int invokeAdd(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        StringHandle hThis = (StringHandle) hTarget;
        StringHandle hThat = (StringHandle) hArg;

        return frame.assignValue(iReturn, makeHandle(hThis.m_sValue + hThat.m_sValue));
        }

    @Override
    public int invokeNativeGet(Frame frame, PropertyStructure property, ObjectHandle hTarget, int iReturn)
        {
        StringHandle hThis = (StringHandle) hTarget;

        switch (property.getName())
            {
            case "size":
                return frame.assignValue(iReturn, xInt64.makeHandle(hThis.m_sValue.length()));
            }

        return super.invokeNativeGet(frame, property, hTarget, iReturn);
        }

    @Override
    public int invokeNativeN(Frame frame, MethodStructure method, ObjectHandle hTarget,
                             ObjectHandle[] ahArg, int iReturn)
        {
        StringHandle hThis = (StringHandle) hTarget;

        switch (ahArg.length)
            {
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

        return super.invokeNativeN(frame, method, hTarget, ahArg, iReturn);
        }

    @Override
    public int invokeNative1(Frame frame, MethodStructure method, ObjectHandle hTarget,
                             ObjectHandle hArg, int iReturn)
        {
        StringHandle hThis = (StringHandle) hTarget;

        switch (method.getName())
            {
            case "indexOf": // indexOf(String)
                if (hArg instanceof StringHandle)
                    {
                    int nOf = hThis.m_sValue.indexOf(((StringHandle) hArg).m_sValue);

                    return frame.assignValue(iReturn, xInt64.makeHandle(nOf));
                    }
            }

        return super.invokeNative1(frame, method, hTarget, hArg, iReturn);
        }

    @Override
    public int invokeNativeNN(Frame frame, MethodStructure method, ObjectHandle hTarget,
                              ObjectHandle[] ahArg, int[] aiReturn)
        {
        StringHandle hThis = (StringHandle) hTarget;

        switch (ahArg.length)
            {
            case 2:
                switch (method.getName())
                    {
                    case "indexOf": // (Boolean, Int) indexOf(String s, Range<Int>? range)
                        int cReturns = aiReturn.length;
                        if (cReturns == 0)
                            {
                            return Op.R_NEXT;
                            }

                        String s = ((StringHandle) ahArg[0]).getValue();
                        ObjectHandle hRange = ahArg[1];
                        if (hRange == xNullable.NULL)
                            {
                            int of = hThis.m_sValue.indexOf(s);
                            if (of < 0)
                                {
                                return frame.assignValue(aiReturn[0], xBoolean.FALSE);
                                }

                            return cReturns == 1
                                ? frame.assignValue(aiReturn[0], xBoolean.TRUE)
                                : frame.assignValues(aiReturn, xBoolean.TRUE, xInt64.makeHandle(of));
                            }
                        else
                            {
                            // TODO: parse the range
                            }
                    }
                break;
            }

        return super.invokeNativeNN(frame, method, hTarget, ahArg, aiReturn);
        }

    @Override
    public RefHandle createPropertyRef(ObjectHandle hTarget, String sPropName, boolean fRO)
        {
        if (sPropName.equals("size"))
            {
            if (fRO)
                {
                TypeComposition clzRef = xRef.INSTANCE.ensureParameterizedClass(hTarget.getType());
                return new RefHandle(clzRef, hTarget, sPropName);
                }
            throw new IllegalStateException("Read-only property : String.size");
            }
        throw new IllegalStateException("Unknown property : String." + sPropName);
        }


    // ----- comparison support -----

    @Override
    public int callEquals(Frame frame, TypeComposition clazz,
                          ObjectHandle hValue1, ObjectHandle hValue2, int iReturn)
        {
        StringHandle h1 = (StringHandle) hValue1;
        StringHandle h2 = (StringHandle) hValue2;

        return frame.assignValue(iReturn,
            xBoolean.makeHandle(h1.getValue().equals(h2.getValue())));
        }

    @Override
    public int callCompare(Frame frame, TypeComposition clazz,
                          ObjectHandle hValue1, ObjectHandle hValue2, int iReturn)
        {
        StringHandle h1 = (StringHandle) hValue1;
        StringHandle h2 = (StringHandle) hValue2;

        return frame.assignValue(iReturn,
            xOrdered.makeHandle(h1.getValue().compareTo(h2.getValue())));
        }

    @Override
    public int buildHashCode(Frame frame, ObjectHandle hTarget, int iReturn)
        {
        return frame.assignValue(iReturn,
            xInt64.makeHandle(((StringHandle) hTarget).getValue().hashCode()));
        }

    @Override
    public int buildStringValue(Frame frame, ObjectHandle hTarget, int iReturn)
        {
        return frame.assignValue(iReturn, hTarget);
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
        return new StringHandle(INSTANCE.ensureCanonicalClass(), sValue);
        }
    }
