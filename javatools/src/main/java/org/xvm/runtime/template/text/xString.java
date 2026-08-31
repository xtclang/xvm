package org.xvm.runtime.template.text;


import java.util.Arrays;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;

import org.xvm.asm.constants.StringConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.ClassTemplate;
import org.xvm.runtime.Container;
import org.xvm.runtime.Frame;
import org.xvm.runtime.NativeTemplates;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.OperatorBinding;
import org.xvm.runtime.ObjectHandle.JavaLong;
import org.xvm.runtime.TypeComposition;
import org.xvm.runtime.Utils;

import org.xvm.runtime.template.IndexSupport;
import org.xvm.runtime.template.xBoolean;
import org.xvm.runtime.template.xConst;
import org.xvm.runtime.template.xException;
import org.xvm.runtime.template.xOrdered;

import org.xvm.runtime.template.collections.xArray;
import org.xvm.runtime.template.collections.xArray.ArrayHandle;
import org.xvm.runtime.template.collections.xArray.Mutability;

import org.xvm.runtime.template.numbers.xInt64;

import org.xvm.runtime.template._native.collections.arrays.xRTCharDelegate;
import org.xvm.runtime.template._native.collections.arrays.xRTCharDelegate.CharArrayHandle;
import org.xvm.runtime.template._native.collections.arrays.xRTDelegate.DelegateHandle;
import org.xvm.runtime.template._native.collections.arrays.xRTSlicingDelegate.SliceHandle;

import org.xvm.util.Handy;
import org.xvm.util.Lazy;
import org.xvm.util.FrozenCharArray;


/**
 * Native String implementation.
 */
public class xString
        extends xConst
        implements IndexSupport {
    public xString(Container container, ClassStructure structure) {
        super(container, structure);
    }

    @Override
    public void initNative() {
        bindOp(OperatorBinding.Op.ADD, StringHandle.class, ObjectHandle.class, this::opAdd);

        markNativeProperty("size");
        markNativeProperty("chars");

        markNativeMethod("construct", new String[]{"collections.Array<text.Char>"}, VOID);
        markNativeMethod("construct", STRING, VOID);
        markNativeMethod("indexOf", new String[]{"text.Char", "numbers.Int64"},
                                    new String[]{"Boolean", "numbers.Int64"});
        markNativeMethod("indexOf", new String[]{"text.String", "numbers.Int64"},
                                    new String[]{"Boolean", "numbers.Int64"});
        markNativeMethod("hashCode",  null, INT);
        markNativeMethod("equals",    null, BOOLEAN);
        markNativeMethod("compare",   null, null);

        invalidateTypeInfo();
    }

    @Override
    public boolean isGenericHandle() {
        return false;
    }

    @Override
    public int createConstHandle(Frame frame, Constant constant) {
        if (constant instanceof StringConstant hString) {
            return frame.pushStack(makeHandle(frame, hString.getValue().toCharArray()));
        }

        return super.createConstHandle(frame, constant);
    }

    @Override
    public int construct(Frame frame, MethodStructure constructor, TypeComposition clazz,
                         ObjectHandle hParent, ObjectHandle[] ahVar, int iReturn) {
        if (constructor.getIdentityConstant().getRawParams().get(0).equals(frame.poolContext().typeString())) {
            return frame.assignValue(iReturn, ahVar[0]);
        }

        return frame.assignValue(iReturn,
                makeHandle(frame, getChars((ArrayHandle) ahVar[0])));
    }


    @Override
    public int invokeNativeGet(Frame frame, String sPropName, ObjectHandle hTarget, int iReturn) {
        StringHandle hThis = (StringHandle) hTarget;

        switch (sPropName) {
        case "size":
            return frame.assignValue(iReturn, xInt64.makeHandle(frame, hThis.m_achValue.size()));

        case "chars":
            return frame.assignValue(iReturn,
                    xArray.makeCharArrayHandle(frame.container(), hThis.m_achValue.copy(),
                            Mutability.Constant));
        }

        return super.invokeNativeGet(frame, sPropName, hTarget, iReturn);
    }

    @Override
    public int invokeNativeNN(Frame frame, MethodStructure method, ObjectHandle hTarget,
                              ObjectHandle[] ahArg, int[] aiReturn) {
        switch (ahArg.length) {
        case 2:
            switch (method.getName()) {
            case "indexOf": {
                StringHandle hThis  = (StringHandle) hTarget;
                ObjectHandle hValue = ahArg[0];
                ObjectHandle hStart = ahArg[1];

                int  ofStart = hStart == ObjectHandle.DEFAULT
                        ? 0
                        : (int) ((JavaLong) hStart).getValue();

                int ofResult;
                if (hValue instanceof JavaLong hChar) {
                    // (Boolean, Int) indexOf(Char value, Int startAt)
                    char chValue = (char) hChar.getValue();

                    ofResult = indexOf(hThis.m_achValue.unsafeArray(), chValue, ofStart);
                } else {
                    // (Boolean, Int) indexOf(String value, Int startAt)
                    String sValue = ((StringHandle) hValue).getStringValue();

                    ofResult = hThis.getStringValue().indexOf(sValue, ofStart);
                }
                return ofResult < 0
                        ? frame.assignValue(aiReturn[0], xBoolean.falseHandle(frame))
                        : frame.assignValues(aiReturn, xBoolean.trueHandle(frame), xInt64.makeHandle(frame, ofResult));
            }
            }
        }

        return super.invokeNativeNN(frame, method, hTarget, ahArg, aiReturn);
    }

    private int opAdd(Frame frame, StringHandle hTarget, ObjectHandle hArg, int iReturn) {
        StringHandle hThis = hTarget;

        switch (Utils.callToString(frame, hArg)) {
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

    // ----- IndexSupport --------------------------------------------------------------------------

    @Override
    public int extractArrayValue(Frame frame, ObjectHandle hTarget, long lIndex, int iReturn) {
        FrozenCharArray ach = ((StringHandle) hTarget).getValue();
        int             nIx = (int) lIndex;

        return nIx < 0 || nIx >= ach.size()
                ? frame.raiseException(xException.outOfBounds(frame, lIndex, ach.size()))
                : frame.assignValue(iReturn, xChar.makeHandle(frame, ach.get(nIx)));
    }

    @Override
    public int assignArrayValue(Frame frame, ObjectHandle hTarget, long lIndex, ObjectHandle hValue) {
        return frame.raiseException(xException.immutableObject(frame));
    }

    @Override
    public TypeConstant getElementType(Frame frame, ObjectHandle hTarget, long lIndex) {
        return pool().typeChar();
    }

    @Override
    public long size(ObjectHandle hTarget) {
        return ((StringHandle) hTarget).getValue().size();
    }


    // ----- comparison support --------------------------------------------------------------------

    @Override
    public int callEquals(Frame frame, TypeComposition clazz,
                          ObjectHandle hValue1, ObjectHandle hValue2, int iReturn) {
        return frame.assignValue(iReturn, xBoolean.makeHandle(frame, compareIdentity(hValue1, hValue2)));
    }

    @Override
    public int callCompare(Frame frame, TypeComposition clazz,
                           ObjectHandle hValue1, ObjectHandle hValue2, int iReturn) {
        StringHandle h1 = (StringHandle) hValue1;
        StringHandle h2 = (StringHandle) hValue2;

        return frame.assignValue(iReturn,
                xOrdered.makeHandle(frame,
                        Arrays.compare(h1.m_achValue.unsafeArray(), h2.m_achValue.unsafeArray())));
    }

    @Override
    public boolean compareIdentity(ObjectHandle hValue1, ObjectHandle hValue2) {
        StringHandle h1 = (StringHandle) hValue1;
        StringHandle h2 = (StringHandle) hValue2;

        return h1.m_achValue.contentEquals(h2.m_achValue);
    }

    @Override
    public int buildHashCode(Frame frame, TypeComposition clazz, ObjectHandle hTarget, int iReturn) {
        return frame.assignValue(iReturn, ((StringHandle) hTarget).getHashCode());
    }


    // ----- helpers -------------------------------------------------------------------------------

    /**
     * Extract an array of chars from the Array<Char> handle.
     */
    private static char[] getChars(ArrayHandle hArray) {
        DelegateHandle hDelegate = hArray.getDelegate();
        if (hDelegate instanceof SliceHandle hSlice) {
            CharArrayHandle hChars = (CharArrayHandle) hSlice.f_hSource;
            return xRTCharDelegate.getChars(hChars,
                    (int) hSlice.f_ofStart, (int) hSlice.m_cSize, hSlice.f_fReverse);
        }

        if (hDelegate instanceof CharArrayHandle hChars) {
            return xRTCharDelegate.getChars(hChars, 0, (int) hChars.m_cSize, false);
        }
        throw new UnsupportedOperationException("unsupported delegate: " + hDelegate);
    }

    private static StringHandle concat(StringHandle h1, StringHandle h2) {
        char[] ach1 = h1.m_achValue.unsafeArray();
        char[] ach2 = h2.m_achValue.unsafeArray();

        int c1 = ach1.length;
        int c2 = ach2.length;

        if (c1 == 0) {
            return h2;
        }
        if (c2 == 0) {
            return h1;
        }

        char[] ach = new char[c1 + c2];
        System.arraycopy(ach1, 0, ach, 0, c1);
        System.arraycopy(ach2, 0, ach, c1, c2);
        return makeHandle(h1, ach);
    }

    private static int indexOf(char[] achSource, char chTarget, int ofStart) {
        int cchSource = achSource.length;

        if (ofStart < 0) {
            ofStart = 0;
        } else if (ofStart >= cchSource) {
            return -1;
        }

        if (chTarget < Character.MIN_SUPPLEMENTARY_CODE_POINT) {
            for (int of = ofStart; of < cchSource; of++) {
                if (achSource[of] == chTarget) {
                    return of;
                }
            }
        } else {
            // TODO: see String.java indexOfSupplementary()
        }
        return -1;
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
                                   ObjectHandle hAppender, int iReturn) {
        xString         template       = getInstance(frame);
        MethodStructure methodAppendTo = template.f_methodAppendTo.get(template);
        ObjectHandle[]  ahArg          = new ObjectHandle[methodAppendTo.getMaxVars()];
        ahArg[0] = hAppender;

        return frame.call1(methodAppendTo, hString, ahArg, iReturn);
    }


    // ----- handle --------------------------------------------------------------------------------

    public static class StringHandle
            extends ObjectHandle {
        private final     FrozenCharArray m_achValue;

        // Per-handle memoization intentionally does not use Lazy: these values are immutable and a
        // benign race can only compute the same value twice, while Lazy would add two objects to
        // every StringHandle.
        private transient JavaLong m_hash;   // cached hash value
        private transient String   m_sValue; // cached String value

        protected StringHandle(TypeComposition clazz, char[] achValue) {
            super(clazz);

            m_achValue = FrozenCharArray.adopt(achValue);
        }

        /**
         * @return the character data; frozen, because an Ecstasy String is immutable and this
         *         previously handed out a mutable alias of the very array backing that guarantee
         */
        public FrozenCharArray getValue() {
            return m_achValue;
        }

        public String getStringValue() {
            String sValue = m_sValue;
            if (sValue == null) {
                m_sValue = sValue = m_achValue.asString();
            }
            return sValue;
        }

        public int calcHashCode() {
            char[] ach  = m_achValue.unsafeArray();
            int    cch  = ach.length;
            int    hash = 982_451_653;
            if (cch <= 0x40) {
                for (char ch : ach) {
                    hash = hash * 31 + ch;
                }
            } else {
                // just sample ~60 characters from across the entire length of the string
                for (int of = 0, cchStep = (cch >>> 6) + 1; of < cch; of += cchStep) {
                    hash = hash * 31 + ach[of];
                }
            }
            return hash;
        }

        public JavaLong getHashCode() {
            JavaLong hash = m_hash;
            if (hash == null) {
                // String handles can cache their hash without a frame; the handle composition is
                // the owner for the Int64 hash handle.
                m_hash = hash = xInt64.makeHandle(this, calcHashCode());
            }
            return hash;
        }

        @Override
        public int hashCode() {
            return (int) getHashCode().getValue();
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof StringHandle that) {
                return this.m_achValue.contentEquals(that.m_achValue);
            }
            return false;
        }

        @Override
        public int compareTo(ObjectHandle that) {
            return that instanceof StringHandle hThat
                    ? getStringValue().compareTo(hThat.getStringValue())
                    : -1;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder(super.toString());
            sb.append('\"');
            Handy.appendString(sb, getStringValue());
            return sb.append('\"').toString();
        }
    }

    public static xString getInstance(Frame frame) {
        return NativeTemplates.get(frame).string();
    }

    public static xString getInstance(Container container) {
        return NativeTemplates.get(container).string();
    }

    public static xString getInstance(ClassTemplate template) {
        return NativeTemplates.get(template).string();
    }

    public static StringHandle makeHandle(Frame frame, String sValue) {
        return makeHandle(frame.container(), sValue);
    }

    public static StringHandle makeHandle(Frame frame, char[] achValue) {
        return makeHandle(frame.container(), achValue);
    }

    public static StringHandle makeHandle(Container container, String sValue) {
        return makeHandle(container, sValue.toCharArray());
    }

    public static StringHandle makeHandle(Container container, char[] achValue) {
        return getInstance(container).makeHandle(achValue);
    }

    public static StringHandle makeHandle(ClassTemplate template, String sValue) {
        return NativeTemplates.get(template).string().makeHandle(sValue.toCharArray());
    }

    public static StringHandle makeHandle(ObjectHandle owner, String sValue) {
        return makeHandle(owner.getComposition().getContainer(), sValue);
    }

    public static StringHandle emptyString(Frame frame) {
        return emptyString(frame.container());
    }

    public static StringHandle emptyString(Container container) {
        xString template = getInstance(container);
        return template.f_emptyString.get(template);
    }

    public static StringHandle zero(Frame frame) {
        xString template = getInstance(frame);
        return template.f_zero.get(template);
    }

    public static StringHandle one(Frame frame) {
        xString template = getInstance(frame);
        return template.f_one.get(template);
    }

    private static StringHandle makeHandle(StringHandle owner, char[] achValue) {
        return owner.getTemplate(xString.class).makeHandle(achValue);
    }

    private StringHandle makeHandle(String sValue) {
        return makeHandle(sValue.toCharArray());
    }

    private StringHandle makeHandle(char[] achValue) {
        return achValue.length == 0
            ? f_emptyString.get(this)
            : new StringHandle(getCanonicalClass(), achValue);
    }


    // ----- Composition and handle caching --------------------------------------------------------

    /**
     * @return an immutable array of Strings
     */
    public static ArrayHandle makeArrayHandle(Container container, String[] asValue) {
        int            cValues = asValue.length;
        StringHandle[] ahValue = new StringHandle[cValues];
        for (int i = 0; i < cValues; i++) {
            ahValue[i] = makeHandle(container, asValue[i]);
        }
        return xArray.makeStringArrayHandle(container, ahValue);
    }

    /**
     * @return the handle for an empty Array of String
     */
    public static ArrayHandle ensureEmptyArray(Container container) {
        xString template = getInstance(container);
        return template.f_emptyStringArray.get(template);
    }

    // ----- data members --------------------------------------------------------------------------

    /**
     * Owner-local cached empty string handle. String handles carry a TypeComposition, so even the
     * common empty string must be cached by the owning container/template, not in a JVM global.
     */
    private final Lazy.Bound<xString, StringHandle> f_emptyString =
            Lazy.ofBound(owner -> new StringHandle(owner.getCanonicalClass(), new char[0]));

    private final Lazy.Bound<xString, StringHandle> f_zero =
            Lazy.ofBound(owner -> owner.makeHandle("0"));

    private final Lazy.Bound<xString, StringHandle> f_one =
            Lazy.ofBound(owner -> owner.makeHandle("1"));

    private final Lazy.Bound<xString, MethodStructure> f_methodAppendTo = Lazy.ofBound(owner -> {
        ConstantPool pool    = owner.pool();
        TypeConstant typeArg = pool.ensureClassTypeConstant(
                pool.ensureEcstasyClassConstant("Appender"), null,
                pool.typeChar());

        return owner.getStructure().findMethod("appendTo", 1, typeArg);
    });

    private final Lazy.Bound<xString, ArrayHandle> f_emptyStringArray =
            Lazy.ofBound(owner -> xArray.makeStringArrayHandle(owner.container(), Utils.STRINGS_NONE));
}
