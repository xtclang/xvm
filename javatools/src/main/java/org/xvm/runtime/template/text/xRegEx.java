package org.xvm.runtime.template.text;


import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Constant;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;

import org.xvm.asm.constants.RegExConstant;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.TemplateRegistry;
import org.xvm.runtime.TypeComposition;
import org.xvm.runtime.Utils;

import org.xvm.runtime.template.xBoolean;
import org.xvm.runtime.template.xConst;


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
        String[] Regex = new String[]{"text.RegEx"};

        markNativeProperty("pattern");
        markNativeMethod("construct", STRING, VOID);
        markNativeMethod("find", STRING, null);
        markNativeMethod("match", STRING, null);
        markNativeMethod("matchPrefix", STRING, null);

        getCanonicalType().invalidateTypeInfo();
        }

    @Override
    public int construct(Frame frame, MethodStructure constructor, TypeComposition clazz,
                         ObjectHandle hParent, ObjectHandle[] ahVar, int iReturn)
        {
        String regex = ((xString.StringHandle) ahVar[0]).getStringValue();
        return frame.assignValue(iReturn, new RegExHandle(INSTANCE.getCanonicalClass(), regex));
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
                String regex = ((RegExHandle) hTarget).getRegex() + ((RegExHandle) hArg).getRegex();
                return frame.assignValue(iReturn, new RegExHandle(INSTANCE.getCanonicalClass(), regex));
                }
            }

        return super.invokeNative1(frame, method, hTarget, hArg, iReturn);
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
                String value = ((xString.StringHandle) ahArg[0]).getStringValue();
                Pattern pattern = hRegEx.getPattern();
                Matcher matcher = pattern.matcher(value);
                if (matcher.matches())
                    {
                    return Utils.assignConditionalResult(
                        frame,
                        xMatcher.INSTANCE.createHandle(frame, matcher, hRegEx, Op.A_STACK),
                        aiReturn);
                    }
                return frame.assignValue(aiReturn[0], xBoolean.FALSE);
                }
            case "matchPrefix":
                {
                String value = ((xString.StringHandle) ahArg[0]).getStringValue();
                Pattern pattern = hRegEx.getPattern();
                Matcher matcher = pattern.matcher(value);
                if (matcher.lookingAt())
                    {
                    return Utils.assignConditionalResult(
                        frame,
                        xMatcher.INSTANCE.createHandle(frame, matcher, hRegEx, Op.A_STACK),
                        aiReturn);
                    }
                return frame.assignValue(aiReturn[0], xBoolean.FALSE);
                }
            case "find":
                {
                String value = ((xString.StringHandle) ahArg[0]).getStringValue();
                Pattern pattern = hRegEx.getPattern();
                Matcher matcher = pattern.matcher(value);
                if (matcher.find())
                    {
                    return Utils.assignConditionalResult(
                        frame,
                        xMatcher.INSTANCE.createHandle(frame, matcher, hRegEx, Op.A_STACK),
                        aiReturn);
                    }
                return frame.assignValue(aiReturn[0], xBoolean.FALSE);
                }
            }
        return super.invokeNativeNN(frame, method, hTarget, ahArg, aiReturn);
        }

    public int createConstHandle(Frame frame, String regex)
        {
        return createConstHandle(frame, new RegExConstant(frame.poolContext(), regex));
        }

    @Override
    public int createConstHandle(Frame frame, Constant constant)
        {
        if (constant instanceof RegExConstant)
            {
            return frame.pushStack(new RegExHandle(getCanonicalClass(),
                    ((RegExConstant) constant).getValue()));
            }

        return super.createConstHandle(frame, constant);
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

        private Pattern m_pattern;

        protected RegExHandle(TypeComposition clazz, String regex)
            {
            super(clazz);

            m_regex = regex;
            }

        /**
         * @return the regular expression.
         */
        public String getRegex()
            {
            return m_regex;
            }

        /**
         * @return the compiled regular expression {@link Pattern}.
         */
        public Pattern getPattern()
            {
            if (m_pattern == null)
                {
                m_pattern = Pattern.compile(m_regex);
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
    }
