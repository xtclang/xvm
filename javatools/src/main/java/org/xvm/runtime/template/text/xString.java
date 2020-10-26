package org.xvm.runtime.template.text;


import java.util.Arrays;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;

import org.xvm.asm.constants.StringConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.ClassComposition;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.ArrayHandle;
import org.xvm.runtime.ObjectHandle.JavaLong;
import org.xvm.runtime.TypeComposition;
import org.xvm.runtime.TemplateRegistry;
import org.xvm.runtime.Utils;

import org.xvm.runtime.template.IndexSupport;
import org.xvm.runtime.template.xBoolean;
import org.xvm.runtime.template.xConst;
import org.xvm.runtime.template.xException;
import org.xvm.runtime.template.xOrdered;

import org.xvm.runtime.template.collections.xArray;
import org.xvm.runtime.template.collections.xCharArray;
import org.xvm.runtime.template.collections.xCharArray.CharArrayHandle;

import org.xvm.runtime.template.numbers.xInt64;


/**
 * Native String implementation.
 */
public class xString
        extends xConst
        implements IndexSupport
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
    public void initNative()
        {
        ConstantPool   pool      = pool();
        TypeConstant   typeArg   = pool.ensureClassTypeConstant(
                pool.ensureEcstasyClassConstant("Appender"), null,
                pool.typeChar());

        EMPTY_STRING     = new StringHandle(getCanonicalClass(), new char[0]);
        EMPTY_ARRAY      = makeHandle(new char[] {'[', ']'});
        ZERO             = makeHandle(new char[] {'0'});
        ONE              = makeHandle(new char[] {'1'});
        METHOD_APPEND_TO = getStructure().findMethod("appendTo", 1, typeArg);

        markNativeProperty("size");
        markNativeProperty("chars");

        markNativeMethod("construct", new String[]{"collections.Array<text.Char>"}, VOID);
        markNativeMethod("indexOf", new String[]{"text.Char", "numbers.Int64"},
                                    new String[]{"Boolean", "numbers.Int64"});
        markNativeMethod("substring", INT, STRING);

        getCanonicalType().invalidateTypeInfo();
        }

    @Override
    public boolean isGenericHandle()
        {
        return false;
        }

    @Override
    public int createConstHandle(Frame frame, Constant constant)
        {
        if (constant instanceof StringConstant)
            {
            return frame.pushStack(new StringHandle(getCanonicalClass(),
                    ((StringConstant) constant).getValue().toCharArray()));
            }

        return super.createConstHandle(frame, constant);
        }

    @Override
    public int construct(Frame frame, MethodStructure constructor, ClassComposition clazz,
                         ObjectHandle hParent, ObjectHandle[] ahVar, int iReturn)
        {
        CharArrayHandle hCharArray = (CharArrayHandle) ahVar[0];
        hCharArray.makeImmutable();

        return frame.assignValue(iReturn, makeHandle(hCharArray.m_achValue));
        }

    @Override
    public int invokeAdd(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        StringHandle hThis = (StringHandle) hTarget;

        switch (Utils.callToString(frame, hArg))
            {
            case Op.R_NEXT:
                return frame.assignValue(iReturn, concat(hThis, (StringHandle) frame.popStack()));

            case Op.R_CALL:
                frame.m_frameNext.addContinuation(frameCaller ->
                    frameCaller.assignValue(iReturn, concat(hThis, (StringHandle) frame.popStack())));
                return Op.R_CALL;

            case Op.R_EXCEPTION:
                return Op.R_EXCEPTION;

            default:
                throw new IllegalStateException();
            }
        }

    @Override
    public int invokeNativeGet(Frame frame, String sPropName, ObjectHandle hTarget, int iReturn)
        {
        StringHandle hThis = (StringHandle) hTarget;

        switch (sPropName)
            {
            case "size":
                return frame.assignValue(iReturn, xInt64.makeHandle(hThis.m_achValue.length));

            case "chars":
                return frame.assignValue(iReturn,
                        xCharArray.makeHandle(hThis.m_achValue, xArray.Mutability.Constant));
            }

        return super.invokeNativeGet(frame, sPropName, hTarget, iReturn);
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
            case "substring": // (Int starAt)
                {
                int    ofStart = (int) ((JavaLong) hArg).getValue();
                char[] ach     = hThis.m_achValue;
                int    cch     = ach.length;

                if (ofStart <= 0)
                    {
                    return frame.assignValue(iReturn, hThis);
                    }

                if (ofStart >= cch)
                    {
                    return frame.assignValue(iReturn, EMPTY_STRING);
                    }

                int    cchNew = cch - ofStart;
                char[] achNew = new char[cchNew];
                System.arraycopy(ach, ofStart, achNew, 0, cchNew);
                return frame.assignValue(iReturn, makeHandle(achNew));
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
                    case "indexOf": // (Boolean, Int) indexOf(Char value, Int startAt)
                        {
                        ObjectHandle hValue = ahArg[0];
                        ObjectHandle hStart = ahArg[1];

                        char chValue = (char) ((JavaLong) hValue).getValue();
                        int  ofStart = hStart == ObjectHandle.DEFAULT
                                ? 0
                                : (int) ((JavaLong) hStart).getValue();

                        int  ofResult = indexOf(hThis.m_achValue, chValue, ofStart);
                        return ofResult < 0
                                ? frame.assignValue(aiReturn[0], xBoolean.FALSE)
                                : frame.assignValues(aiReturn, xBoolean.TRUE, xInt64.makeHandle(ofResult));
                        }
                    }
                break;
            }

        return super.invokeNativeNN(frame, method, hTarget, ahArg, aiReturn);
        }

    // ----- IndexSupport -----

    @Override
    public int extractArrayValue(Frame frame, ObjectHandle hTarget, long lIndex, int iReturn)
        {
        char[] ach = ((StringHandle) hTarget).getValue();
        try
            {
            return frame.assignValue(iReturn, xChar.makeHandle(ach[(int) lIndex]));
            }
        catch (ArrayIndexOutOfBoundsException e)
            {
            return frame.raiseException(xException.outOfBounds(frame, lIndex, ach.length));
            }
        }

    @Override
    public int assignArrayValue(Frame frame, ObjectHandle hTarget, long lIndex, ObjectHandle hValue)
        {
        return frame.raiseException(xException.immutableObject(frame));
        }

    @Override
    public TypeConstant getElementType(Frame frame, ObjectHandle hTarget, long lIndex)
        {
        return pool().typeChar();
        }

    @Override
    public long size(ObjectHandle hTarget)
        {
        return ((StringHandle) hTarget).getValue().length;
        }


    // ----- comparison support --------------------------------------------------------------------

    @Override
    public int callEquals(Frame frame, ClassComposition clazz,
                          ObjectHandle hValue1, ObjectHandle hValue2, int iReturn)
        {
        StringHandle h1 = (StringHandle) hValue1;
        StringHandle h2 = (StringHandle) hValue2;

        return frame.assignValue(iReturn, xBoolean.makeHandle(compare(h1, h2) == 0));
        }

    @Override
    public int callCompare(Frame frame, ClassComposition clazz,
                           ObjectHandle hValue1, ObjectHandle hValue2, int iReturn)
        {
        StringHandle h1 = (StringHandle) hValue1;
        StringHandle h2 = (StringHandle) hValue2;

        return frame.assignValue(iReturn, xOrdered.makeHandle(compare(h1, h2)));
        }

    @Override
    public int buildHashCode(Frame frame, ClassComposition clazz, ObjectHandle hTarget, int iReturn)
        {
        return frame.assignValue(iReturn, ((StringHandle) hTarget).getHashCode());
        }


    // ----- helpers -------------------------------------------------------------------------------

    protected StringHandle concat(StringHandle h1, StringHandle h2)
        {
        char[] ach1 = h1.m_achValue;
        char[] ach2 = h2.m_achValue;

        int c1 = ach1.length;
        int c2 = ach2.length;

        if (c1 == 0)
            {
            return h2;
            }
        if (c2 == 0)
            {
            return h1;
            }

        char[] ach = new char[c1 + c2];
        System.arraycopy(ach1, 0, ach, 0, c1);
        System.arraycopy(ach2, 0, ach, c1, c2);
        return makeHandle(ach);
        }

    protected int indexOf(char[] achSource, char chTarget, int ofStart)
        {
        int cchSource = achSource.length;

        if (ofStart < 0)
            {
            ofStart = 0;
            }
        else if (ofStart >= cchSource)
            {
            return -1;
            }

        if (chTarget < Character.MIN_SUPPLEMENTARY_CODE_POINT)
            {
            for (int of = ofStart; of < cchSource; of++)
                {
                if (achSource[of] == chTarget)
                    {
                    return of;
                    }
                }
            return -1;
            }
        else
            {
            // TODO: see String.java indexOfSupplementary()
            return -1;
            }
        }

    protected int indexOf(char[] achSource, char[] achTarget, int ofStart)
        {
        int cchSource = achSource.length;
        int cchTarget = achTarget.length;

        if (ofStart >= cchSource - cchTarget)
            {
            return -1;
            }

        if (ofStart < 0)
            {
            ofStart = 0;
            }

        if (cchTarget <= 1)
            {
            if (cchTarget == 0)
                {
                return ofStart;
                }
            return indexOf(achSource, achTarget[0], ofStart);
            }

        char chFirst = achTarget[0];
        int  ofMax   = cchSource - cchTarget;

        for (int of = ofStart; of <= ofMax; of++)
            {
            // indexOf(chFirst, ofStart)
            if (achSource[of] != chFirst)
                {
                while (++of <= ofMax && achSource[of] != chFirst)
                    {
                    }
                }

            // first character matches; compare the rest
            if (of <= ofMax)
                {
                int ofFirst = of + 1;
                int ofLast  = ofFirst + cchTarget - 1;

                for (int ofTarget = 1;
                     ofFirst < ofLast && achSource[ofFirst] == achTarget[ofTarget];
                     ofFirst++, ofTarget++)
                    {
                    }

                if (ofFirst == ofLast)
                    {
                    return of;
                    }
                }
            }
        return -1;
        }

    protected int compare(StringHandle h1, StringHandle h2)
        {
        char[] ach1 = h1.m_achValue;
        char[] ash2 = h2.m_achValue;
        int    c1   = ach1.length;
        int    c2   = ash2.length;
        int    c    = Math.min(c1, c2);

        for (int i = 0; i < c; i++)
            {
            char ch1 = ach1[i];
            char ch2 = ash2[i];
            if (ch1 != ch2)
                {
                return ch1 - ch2;
                }
            }
        return c1 - c2;
        }

    /**
     * Call String.appendTo(Appender<Char> appender)
     *
     * @param frame      the current frame
     * @param hString    the string to append
     * @param hAppender  the appender handle
     * @param iReturn    the register to place the result into
     *
     * @return one of the {@link Op#R_CALL}, {@link Op#R_EXCEPTION} values
     */
    public static int callAppendTo(Frame frame, StringHandle hString,
                                   ObjectHandle hAppender, int iReturn)
        {
        ObjectHandle[] ahArg = new ObjectHandle[METHOD_APPEND_TO.getMaxVars()];
        ahArg[0] = hAppender;

        return frame.call1(METHOD_APPEND_TO, hString, ahArg, iReturn);
        }


    // ----- handle --------------------------------------------------------------------------------

    public static class StringHandle
            extends ObjectHandle
        {
        private char[] m_achValue;
        private transient JavaLong m_hash; // cached hash value
        private transient String m_sValue; // cached String value

        protected StringHandle(TypeComposition clazz, char[] achValue)
            {
            super(clazz);

            m_achValue = achValue;
            }

        public char[] getValue()
            {
            return m_achValue;
            }

        public String getStringValue()
            {
            String sValue = m_sValue;
            return sValue == null
                    ? (m_sValue = new String(m_achValue))
                    : sValue;
            }

        public JavaLong getHashCode()
            {
            JavaLong hHash = m_hash;
            return hHash == null
                    ? (m_hash = xInt64.makeHandle(Arrays.hashCode(m_achValue)))
                    : hHash;
            }

        @Override
        public int hashCode()
            {
            return (int) getHashCode().getValue();
            }

        @Override
        public boolean equals(Object obj)
            {
            if (obj instanceof StringHandle)
                {
                return Arrays.equals(m_achValue, ((StringHandle) obj).m_achValue);
                }
            return false;
            }

        @Override
        public String toString()
            {
            return super.toString() + getStringValue();
            }
        }

    public static StringHandle makeHandle(String sValue)
        {
        return makeHandle(sValue.toCharArray());
        }

    public static StringHandle makeHandle(char[] achValue)
        {
        return achValue.length == 0
            ? EMPTY_STRING
            : new StringHandle(INSTANCE.getCanonicalClass(), achValue);
        }


    // ----- Composition and handle caching --------------------------------------------------------

    /**
     * @return the ClassComposition for an Array of String
     */
    public static ClassComposition ensureArrayComposition()
        {
        ClassComposition clz = ARRAY_CLZCOMP;
        if (clz == null)
            {
            ConstantPool pool = INSTANCE.pool();
            TypeConstant typeStringArray = pool.ensureParameterizedTypeConstant(
                    pool.typeArray(), pool.typeString());
            ARRAY_CLZCOMP = clz = INSTANCE.f_templates.resolveClass(typeStringArray);
            assert clz != null;
            }
        return clz;
        }

    /**
     * @return the handle for an empty Array of String
     */
    public static ArrayHandle ensureEmptyArray()
        {
        if (ARRAY_EMPTY == null)
            {
            ARRAY_EMPTY = xArray.makeStringArrayHandle(new StringHandle[0]);
            }
        return ARRAY_EMPTY;
        }


    // ----- data members --------------------------------------------------------------------------

    public static StringHandle EMPTY_STRING;
    public static StringHandle EMPTY_ARRAY;
    public static StringHandle ZERO;
    public static StringHandle ONE;

    private static ClassComposition ARRAY_CLZCOMP;
    private static ArrayHandle      ARRAY_EMPTY;

    protected static MethodStructure METHOD_APPEND_TO;
    }
