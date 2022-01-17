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
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.GenericHandle;
import org.xvm.runtime.TemplateRegistry;
import org.xvm.runtime.TypeComposition;
import org.xvm.runtime.Utils;

import org.xvm.runtime.template.collections.xArray;
import org.xvm.runtime.template.numbers.xInt64;
import org.xvm.runtime.template.xBoolean;
import org.xvm.runtime.template.xConst;
import org.xvm.runtime.template.xNullable;


/**
 * Native implementation of RegEx.
 */
public class xRegEx
        extends xConst
    {
    public static xRegEx INSTANCE;

    public xRegEx(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
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
        markNativeProperty("pattern");
        markNativeMethod("construct", new String[] {"text.String", "numbers.Int64"}, VOID);
        markNativeMethod("find", new String[] {"text.String", "numbers.Int64"}, null);
        markNativeMethod("match", STRING, null);
        markNativeMethod("matchPrefix", STRING, null);
        markNativeMethod("replaceAll", new String[] {"text.String", "text.String"}, STRING);

        getCanonicalType().invalidateTypeInfo();

        ClassTemplate    clzTempMatch      = f_templates.getTemplate("text.Match");
        TypeComposition  typeMatch         = clzTempMatch.getCanonicalClass();
        ClassStructure   clzStructMatch    = f_templates.getClassStructure("text.Match");
        ConstantPool     pool              = pool();
        TypeConstant     typeRangeInt      = pool.ensureParameterizedTypeConstant(pool.typeRange(), pool.typeCInt64());
        TypeConstant     typeNullableRange = pool.ensureIntersectionTypeConstant(pool.typeNullable(), typeRangeInt);
        TypeConstant     typeArray         = pool.ensureParameterizedTypeConstant(pool.typeArray(), typeNullableRange);
        ClassComposition clzRange          = f_templates.getTemplate("Range").getCanonicalClass();

        m_clzMatchStruct   = typeMatch.ensureAccess(Constants.Access.STRUCT);
        m_constructorMatch = clzStructMatch.findConstructor(pool.typeRegEx(), pool.typeString(), typeArray);
        m_clzRangeOfInt    = clzRange.ensureCanonicalizedComposition(typeRangeInt);
        }

    @Override
    public int construct(Frame frame, MethodStructure constructor, TypeComposition clazz,
                         ObjectHandle hParent, ObjectHandle[] ahVar, int iReturn)
        {
        String regex = ((xString.StringHandle) ahVar[0]).getStringValue();
        long   nFlags = ((ObjectHandle.JavaLong) ahVar[1]).getValue();
        return frame.assignValue(iReturn, new RegExHandle(INSTANCE.getCanonicalClass(), regex, nFlags));
        }

    @Override
    public int invokeNativeGet(Frame frame, String sPropName, ObjectHandle hTarget, int iReturn)
        {
        RegExHandle hPattern = (RegExHandle) hTarget;
        if ("pattern".equals(sPropName))
            {
            return frame.assignValue(iReturn, xString.makeHandle(hPattern.m_regex));
            }

        return super.invokeNativeGet(frame, sPropName, hTarget, iReturn);
        }

    @Override
    public int invokeNative1(Frame frame, MethodStructure method, ObjectHandle hTarget,
                             ObjectHandle hArg, int iReturn)
        {
        switch (method.getName())
            {
            case "append":
                {
                RegExHandle hRegEx = (RegExHandle) hTarget;
                String      regex  = hRegEx.getRegex() + ((RegExHandle) hArg).getRegex();
                long        nFlags = hRegEx.getFlags();
                return frame.assignValue(iReturn, new RegExHandle(INSTANCE.getCanonicalClass(), regex, nFlags));
                }
            case "appendTo":
                {
                xString.StringHandle hRegex = xString.makeHandle(((RegExHandle) hTarget).getRegex());
                return xString.callAppendTo(frame, hRegex, hArg, iReturn);
                }
            }

        return super.invokeNative1(frame, method, hTarget, hArg, iReturn);
        }

    @Override
    public int invokeNativeN(Frame frame, MethodStructure method, ObjectHandle hTarget,
                             ObjectHandle[] ahArg, int iReturn)
        {
        switch (method.getName())
            {
            case "replaceAll":
                {
                xString.StringHandle hText       = (xString.StringHandle) ahArg[0];
                String               text        = hText.getStringValue();
                String               replacement = ((xString.StringHandle) ahArg[1]).getStringValue();
                Matcher              matcher     = ((RegExHandle) hTarget).getPattern().matcher(text);
                xString.StringHandle hResult     = xString.makeHandle(matcher.replaceAll(replacement));
                return frame.assignValue(iReturn, hResult);
                }
            }

        return super.invokeNativeN(frame, method, hTarget, ahArg, iReturn);
        }

    @Override
    public int invokeNativeNN(Frame frame, MethodStructure method, ObjectHandle hTarget,
                              ObjectHandle[] ahArg, int[] aiReturn)
        {
        RegExHandle hRegEx = (RegExHandle) hTarget;
        switch (method.getName())
            {
            case "match":
                {
                xString.StringHandle hText   = (xString.StringHandle) ahArg[0];
                Pattern              pattern = hRegEx.getPattern();
                Matcher              matcher = pattern.matcher(hText.getStringValue());
                if (matcher.matches())
                    {
                    MatchResult result = matcher.toMatchResult();
                    return Utils.assignConditionalResult(
                        frame,
                        createMatchHandle(frame, result, hText, hRegEx, Op.A_STACK),
                        aiReturn);
                    }
                return frame.assignValue(aiReturn[0], xBoolean.FALSE);
                }
            case "matchPrefix":
                {
                xString.StringHandle hText   = (xString.StringHandle) ahArg[0];
                Pattern              pattern = hRegEx.getPattern();
                Matcher              matcher = pattern.matcher(hText.getStringValue());
                if (matcher.lookingAt())
                    {
                    MatchResult result = matcher.toMatchResult();
                    return Utils.assignConditionalResult(
                        frame,
                        createMatchHandle(frame, result, hText, hRegEx, Op.A_STACK),
                        aiReturn);
                    }
                return frame.assignValue(aiReturn[0], xBoolean.FALSE);
                }
            case "find":
                {
                xString.StringHandle hText   = (xString.StringHandle) ahArg[0];
                Pattern              pattern = hRegEx.getPattern();
                Matcher              matcher = pattern.matcher(hText.getStringValue());
                ObjectHandle         hStart  = ahArg[1];
                long                 nStart  = hStart instanceof ObjectHandle.JavaLong
                                                    ? ((ObjectHandle.JavaLong) hStart).getValue()
                                                    : 0L;
                if (matcher.find((int) nStart))
                    {
                    MatchResult result = matcher.toMatchResult();
                    return Utils.assignConditionalResult(
                        frame,
                        createMatchHandle(frame, result, hText, hRegEx, Op.A_STACK),
                        aiReturn);
                    }
                return frame.assignValue(aiReturn[0], xBoolean.FALSE);
                }
            }
        return super.invokeNativeNN(frame, method, hTarget, ahArg, aiReturn);
        }

    @Override
    public int createConstHandle(Frame frame, Constant constant)
        {
        if (constant instanceof RegExConstant)
            {
            RegExConstant regex = (RegExConstant) constant;
            return frame.pushStack(new RegExHandle(getCanonicalClass(),
                regex.getValue(), regex.getFlags()));
            }

        return super.createConstHandle(frame, constant);
        }

    /**
     * Create an object handle for a RegEx using the specified String pattern and push it on the
     * frame's local stack.
     *
     * @param frame   the current frame
     * @param regex   the regular expression pattern
     * @param nFlags  the flags to pass to the compiler
     *
     * @return one of the {@link Op#R_NEXT}, {@link Op#R_CALL} or {@link Op#R_EXCEPTION} values
     */
    public int createConstHandle(Frame frame, String regex, int nFlags)
        {
        return createConstHandle(frame, new RegExConstant(frame.poolContext(), regex, nFlags));
        }


    /**
     * Construct a new text.Match hanle representing the specified {@link MatchResult}.
     *
     * @param frame   the current frame
     * @param match   the immutable {@link MatchResult}
     * @param hText   the handle of the matched String
     * @param hRegEx  the handle of the pattern used to create the result
     * @param iReturn the register id to place the created handle into
     *
     * @return one of the {@link Op#R_NEXT}, {@link Op#R_CALL} or {@link Op#R_EXCEPTION}
     */
    public int createMatchHandle(Frame frame, MatchResult match, xString.StringHandle hText,
                                 xRegEx.RegExHandle hRegEx, int iReturn)
        {
        TypeComposition clzRange    = m_clzRangeOfInt;
        TypeComposition clzStruct   = m_clzMatchStruct;
        MethodStructure constructor = m_constructorMatch;

        ObjectHandle[] ah = new ObjectHandle[match.groupCount() + 1];
        for (int i = 0; i <= match.groupCount(); i++)
            {
            int nStart = match.start(i);
            if (nStart >= 0)
                {
                GenericHandle hRange = new ObjectHandle.GenericHandle(clzRange);
                hRange.setField(frame, "lowerBound", xInt64.INSTANCE.makeJavaLong(nStart));
                hRange.setField(frame, "lowerExclusive", xBoolean.FALSE);
                hRange.setField(frame, "upperBound", xInt64.INSTANCE.makeJavaLong(match.end(i)));
                hRange.setField(frame, "upperExclusive", xBoolean.TRUE);
                hRange.setField(frame, "descending", xBoolean.FALSE);
                hRange.makeImmutable();
                ah[i] = hRange;
                }
            else
                {
                ah[i] = xNullable.NULL;
                }
            }

        ObjectHandle   hGroups = xArray.makeObjectArrayHandle(ah, xArray.Mutability.Fixed);
        ObjectHandle[] ahArgs  = new ObjectHandle[]{hRegEx, hText, hGroups};
        ObjectHandle[] ahVar   = Utils.ensureSize(ahArgs, constructor.getMaxVars());
        GenericHandle  hMatch  = new GenericHandle(clzStruct);

        return proceedConstruction(frame, constructor, true, hMatch, ahVar, iReturn);
        }

    /**
     * A handle for a regular expression.
     */
    public static class RegExHandle
            extends ObjectHandle
        {
        /**
         * The compiled regular expression {@link Pattern}.
         */
        private final String m_regex;

        private final long m_nFlags;

        private Pattern m_pattern;

        protected RegExHandle(TypeComposition clazz, String regex, long nFlags)
            {
            super(clazz);

            m_regex  = regex;
            m_nFlags = nFlags;
            }

        /**
         * @return the regular expression.
         */
        public String getRegex()
            {
            return m_regex;
            }

        /**
         * @return the regular expression flags.
         */
        public long getFlags()
            {
            return m_nFlags;
            }

        /**
         * @return the compiled regular expression {@link Pattern}.
         */
        public Pattern getPattern()
            {
            if (m_pattern == null)
                {
                m_pattern = Pattern.compile(m_regex, (int) m_nFlags);
                }
            return m_pattern;
            }

        @Override
        public boolean equals(Object obj)
            {
            if (obj instanceof RegExHandle)
                {
                return m_regex.equals(((RegExHandle) obj).m_regex);
                }
            return false;
            }

        @Override
        public int hashCode()
            {
            return m_regex.hashCode();
            }

        @Override
        public String toString()
            {
            return super.toString() + m_regex;
            }
        }

    // ----- data members ---------------------------------------------------

    /**
     * The text.Match constructor.
     */
    private MethodStructure m_constructorMatch;

    /**
     * The text.Match struct.
     */
    private TypeComposition m_clzMatchStruct;

    /**
     * The Range struct.
     */
    private TypeComposition m_clzRangeOfInt;
    }
