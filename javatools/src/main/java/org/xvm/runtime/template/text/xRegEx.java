package org.xvm.runtime.template.text;


import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.Constants;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;

import org.xvm.asm.constants.RegExConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.ClassComposition;
import org.xvm.runtime.ClassTemplate;
import org.xvm.runtime.Container;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.JavaLong;
import org.xvm.runtime.ObjectHandle.GenericHandle;
import org.xvm.runtime.TypeComposition;
import org.xvm.runtime.Utils;

import org.xvm.runtime.template.collections.xArray;
import org.xvm.runtime.template.collections.xArray.Mutability;

import org.xvm.runtime.template.numbers.xInt64;

import org.xvm.runtime.template.text.xString.StringHandle;

import org.xvm.runtime.template.xBoolean;
import org.xvm.runtime.template.xConst;
import org.xvm.runtime.template.xNullable;

import org.xvm.util.Lazy;


/**
 * Native implementation of RegEx.
 */
public class xRegEx
        extends xConst {
    public xRegEx(Container container, ClassStructure structure) {
        super(container, structure);
    }

    @Override
    public void initNative() {
        markNativeProperty("pattern");

        markNativeMethod("construct",   new String[] {"text.String", "numbers.Int64"}, VOID);
        markNativeMethod("find",        new String[] {"text.String", "numbers.Int64"}, null);
        markNativeMethod("match",       STRING, null);
        markNativeMethod("matchPrefix", STRING, null);
        markNativeMethod("replaceAll",  new String[] {"text.String", "text.String"}, STRING);

        invalidateTypeInfo();

        ConstantPool     pool          = pool();
        ClassTemplate    templateMatch = f_container.getTemplate("text.Match");
        ClassComposition clzMatch      = templateMatch.getCanonicalClass();

        m_clzMatchStruct   = clzMatch.ensureAccess(Constants.Access.STRUCT);
        m_constructorMatch = templateMatch.getStructure().findMethod("construct", 3);

        TypeConstant typeRange = pool.ensureRangeType(pool.typeInt64());
        m_clzRangeOfInt = f_container.resolveClass(typeRange);

        TypeConstant typeRangeArray = pool.ensureArrayType(pool.ensureNullableTypeConstant(typeRange));
        m_clzRangeArray = f_container.resolveClass(typeRangeArray);
    }

    @Override
    public int construct(Frame frame, MethodStructure constructor, TypeComposition clazz,
                         ObjectHandle hParent, ObjectHandle[] ahVar, int iReturn) {
        StringHandle hPattern = (StringHandle) ahVar[0];
        ObjectHandle hFlags   = ahVar[1];

        String regex  = hPattern.getStringValue();
        long   nFlags = hFlags == ObjectHandle.DEFAULT ? 0 : ((JavaLong) hFlags).getValue();
        return frame.assignValue(iReturn, makeHandle(regex, nFlags));
    }


    @Override
    public int createConstHandle(Frame frame, Constant constant) {
        if (constant instanceof RegExConstant regex) {
            return frame.pushStack(makeHandle(regex.getValue(), regex.getFlags()));
        }

        return super.createConstHandle(frame, constant);
    }

    /**
     * Construct a new text.Match handle representing the specified {@link MatchResult}.
     *
     * @param frame   the current frame
     * @param match   the immutable {@link MatchResult}
     * @param hText   the handle of the matched String
     * @param hRegEx  the handle of the pattern used to create the result
     * @param iReturn the register id to place the created handle into
     *
     * @return one of the {@link Op#R_NEXT}, {@link Op#R_CALL} or {@link Op#R_EXCEPTION}
     */
    public int createMatchHandle(Frame frame, MatchResult match, StringHandle hText,
                                 RegExHandle hRegEx, int iReturn) {
        TypeComposition clzRange    = m_clzRangeOfInt;
        TypeComposition clzStruct   = m_clzMatchStruct;
        MethodStructure constructor = m_constructorMatch;

        ObjectHandle[] ah = new ObjectHandle[match.groupCount() + 1];
        for (int i = 0; i <= match.groupCount(); i++) {
            int nStart = match.start(i);
            if (nStart >= 0) {
                GenericHandle hRange = new GenericHandle(clzRange);
                hRange.setField(frame, "lowerBound",     xInt64.makeHandle(frame, nStart));
                hRange.setField(frame, "lowerExclusive", xBoolean.falseHandle(frame));
                hRange.setField(frame, "upperBound",     xInt64.makeHandle(frame, match.end(i)));
                hRange.setField(frame, "upperExclusive", xBoolean.trueHandle(frame));
                hRange.setField(frame, "descending",     xBoolean.falseHandle(frame));
                hRange.makeImmutable();
                ah[i] = hRange;
            } else {
                ah[i] = xNullable.makeHandle(frame);
            }
        }

        ObjectHandle   hGroups = xArray.makeArrayHandle(m_clzRangeArray, ah.length, ah, Mutability.Fixed);
        ObjectHandle[] ahArgs  = new ObjectHandle[]{hRegEx, hText, hGroups};
        ObjectHandle[] ahVar   = Utils.ensureSize(ahArgs, constructor.getMaxVars());
        GenericHandle  hMatch  = new GenericHandle(clzStruct);

        return proceedConstruction(frame, constructor, true, hMatch, ahVar, iReturn);
    }

    /**
     * @return a new RegExHandle
     */
    private RegExHandle makeHandle(String regex, long nFlags) {
        return new RegExHandle(getCanonicalClass(), regex, nFlags);
    }

    /**
     * A handle for a regular expression.
     */
    public static class RegExHandle
            extends ObjectHandle {
        /*
         * Native dispatch lives here rather than on xRegEx: `this` is already a RegExHandle, so
         * every cast the template versions opened with is gone by construction. Operations that
         * genuinely belong to the template - the handle factories - are reached back through
         * getTemplate(xRegEx.class). See ObjectHandle's dispatch defaults.
         */


        @Override
        public int invokeNativeN(Frame frame, MethodStructure method,
                                     ObjectHandle[] ahArg, int iReturn) {
            switch (method.getName()) {
            case "replaceAll": {
                StringHandle hText       = (StringHandle) ahArg[0];
                String       text        = hText.getStringValue();
                String       replacement = ((StringHandle) ahArg[1]).getStringValue();
                Matcher      matcher     = this.getPattern().matcher(text);
                StringHandle hResult     = xString.makeHandle(frame, matcher.replaceAll(replacement));
                return frame.assignValue(iReturn, hResult);
            }
            }

            return super.invokeNativeN(frame, method, ahArg, iReturn);
        }

        @Override
        public int invokeNativeNN(Frame frame, MethodStructure method,
                                     ObjectHandle[] ahArg, int[] aiReturn) {
            RegExHandle hRegEx = this;
            switch (method.getName()) {
            case "match": {
                StringHandle hText   = (StringHandle) ahArg[0];
                Pattern      pattern = hRegEx.getPattern();
                Matcher      matcher = pattern.matcher(hText.getStringValue());
                if (matcher.matches()) {
                    MatchResult result = matcher.toMatchResult();
                    return Utils.assignConditionalResult(
                        frame,
                        getTemplate(xRegEx.class).createMatchHandle(frame, result, hText, hRegEx, Op.A_STACK),
                        aiReturn);
                }
                return frame.assignValue(aiReturn[0], xBoolean.falseHandle(frame));
            }
            case "matchPrefix": {
                StringHandle hText   = (StringHandle) ahArg[0];
                Pattern      pattern = hRegEx.getPattern();
                Matcher      matcher = pattern.matcher(hText.getStringValue());
                if (matcher.lookingAt()) {
                    MatchResult result = matcher.toMatchResult();
                    return Utils.assignConditionalResult(
                        frame,
                        getTemplate(xRegEx.class).createMatchHandle(frame, result, hText, hRegEx, Op.A_STACK),
                        aiReturn);
                }
                return frame.assignValue(aiReturn[0], xBoolean.falseHandle(frame));
            }
            case "find": {
                StringHandle  hText   = (StringHandle) ahArg[0];
                Pattern       pattern = hRegEx.getPattern();
                Matcher       matcher = pattern.matcher(hText.getStringValue());
                ObjectHandle  hStart  = ahArg[1];
                long          nStart  = hStart instanceof JavaLong hInt
                                                    ? hInt.getValue()
                                                    : 0L;
                if (matcher.find((int) nStart)) {
                    MatchResult result = matcher.toMatchResult();
                    return Utils.assignConditionalResult(
                        frame,
                        getTemplate(xRegEx.class).createMatchHandle(frame, result, hText, hRegEx, Op.A_STACK),
                        aiReturn);
                }
                return frame.assignValue(aiReturn[0], xBoolean.falseHandle(frame));
            }
            }
            return super.invokeNativeNN(frame, method, ahArg, aiReturn);
        }

        @Override
        public int invokeNativeGet(Frame frame, String sPropName, int iReturn) {
            if ("pattern".equals(sPropName)) {
                return frame.assignValue(iReturn, xString.makeHandle(frame, f_regex));
            }

            return super.invokeNativeGet(frame, sPropName, iReturn);
        }

        @Override
        public int invokeNative1(Frame frame, MethodStructure method, ObjectHandle hArg,
                                 int iReturn) {
            switch (method.getName()) {
            case "append": {
                String regex  = getRegex() + ((RegExHandle) hArg).getRegex();
                long   nFlags = getFlags();
                // the handle reaches its own template for the factory - this is what the
                // existing getTemplate(Class<T>) accessor is for
                return frame.assignValue(iReturn,
                        getTemplate(xRegEx.class).makeHandle(regex, nFlags));
            }
            case "appendTo": {
                StringHandle hRegex = xString.makeHandle(frame, getRegex());
                return xString.callAppendTo(frame, hRegex, hArg, iReturn);
            }
            }

            return super.invokeNative1(frame, method, hArg, iReturn);
        }

        /**
         * The compiled regular expression {@link Pattern}.
         */
        private final String f_regex;

        /**
         * The regular expression flags.
         */
        private final long f_nFlags;

        /**
         * Cached regular expression pattern. This preserves the old first-use compile behavior
         * without publishing the Pattern through an unsynchronized nullable field.
         */
        private final Lazy<Pattern> pattern;

        protected RegExHandle(TypeComposition clazz, String regex, long nFlags) {
            super(clazz);

            f_regex  = regex;
            f_nFlags = nFlags;
            pattern  = Lazy.of(() -> Pattern.compile(regex, (int) nFlags));
        }

        /**
         * @return the regular expression
         */
        public String getRegex() {
            return f_regex;
        }

        /**
         * @return the regular expression flags
         */
        public long getFlags() {
            return f_nFlags;
        }

        /**
         * @return the compiled regular expression {@link Pattern}
         */
        public Pattern getPattern() {
            return pattern.get();
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof RegExHandle that &&
                   this.f_regex.equals(that.f_regex);
        }

        @Override
        public int hashCode() {
            return f_regex.hashCode();
        }

        @Override
        public String toString() {
            return super.toString() + f_regex;
        }
    }

    // ----- data members ---------------------------------------------------

    /**
     * The text.Match constructor.
     */
    private MethodStructure m_constructorMatch;

    /**
     * The TypeComposition for text.Match type.
     */
    private TypeComposition m_clzMatchStruct;

    /**
     * The TypeComposition for Range<Int> type.
     */
    private TypeComposition m_clzRangeOfInt;

    /**
     * The TypeComposition for Range<Int>?[] type.
     */
    private TypeComposition m_clzRangeArray;
}
