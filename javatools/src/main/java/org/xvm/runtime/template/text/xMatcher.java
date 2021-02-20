package org.xvm.runtime.template.text;


import java.util.regex.Matcher;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.Constants;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;

import org.xvm.asm.constants.StringConstant;

import org.xvm.runtime.TypeComposition;
import org.xvm.runtime.ClassTemplate;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.TemplateRegistry;
import org.xvm.runtime.Utils;

import org.xvm.runtime.template.xBoolean;
import org.xvm.runtime.template.xNullable;

import org.xvm.runtime.template.numbers.xInt64;


/**
 * Native implementation of Matcher.
 */
public class xMatcher
    extends ClassTemplate
    {
    public static xMatcher INSTANCE;

    public xMatcher(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
        {
        super(templates, structure);

        if (fInstance)
            {
            INSTANCE = this;
            }
        }

    @Override
    public void initNative()
        {
        super.initNative();

        markNativeProperty("groupCount");
        markNativeProperty("regEx");
        markNativeMethod("group", INT, null);
        markNativeMethod("getGroupOrNull", INT, null);
        markNativeMethod("next", null, BOOLEAN);
        markNativeMethod("replaceAll", STRING, STRING);

        getCanonicalType().invalidateTypeInfo();

        ClassTemplate   template = f_templates.getTemplate("text.Matcher");
        TypeComposition clz      = ensureClass(template.getCanonicalType());
        ConstantPool    pool     = pool();

        s_clzMatcherStruct   = clz.ensureAccess(Constants.Access.STRUCT);
        s_constructorMatcher = getStructure().findConstructor(pool.typeRegEx(), pool.typeInt());
        }

    @Override
    public int invokeNativeGet(Frame frame, String sPropName, ObjectHandle hTarget, int iReturn)
        {
        MatcherHandle hMatcher = (MatcherHandle) hTarget;
        switch (sPropName)
            {
            case "groupCount":
                {
                Matcher matcher = hMatcher.getMatcher();

                return frame.assignValue(iReturn, xInt64.INSTANCE.makeJavaLong(matcher.groupCount()));
                }
            case "regEx":
                {
                return frame.assignValue(iReturn, hMatcher.getRegExHandle());
                }
            }

        return super.invokeNativeGet(frame, sPropName, hTarget, iReturn);
        }

    @Override
    public int invokeNative1(Frame frame, MethodStructure method,
                             ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        xMatcher.MatcherHandle hMatcher = (xMatcher.MatcherHandle) hTarget;
        switch (method.getName())
            {
            case "getGroupOrNull":
                {
                long    index   = ((ObjectHandle.JavaLong) hArg).getValue();
                Matcher matcher = hMatcher.getMatcher();
                String  result  = matcher.group((int) index);
                return frame.assignValue(iReturn, makePossiblyNullHandle(result));
                }
            case "replaceAll":
                {
                String  replacement = ((xString.StringHandle) hArg).getStringValue();
                Matcher matcher     = hMatcher.getMatcher();
                String  result      = matcher.replaceAll(replacement);
                return frame.assignValue(iReturn, makePossiblyNullHandle(result));
                }
            }
        return super.invokeNative1(frame, method, hTarget, hArg, iReturn);
        }

    public int invokeNativeN(Frame frame, MethodStructure method,
                             ObjectHandle hTarget, ObjectHandle[] ahArg, int iReturn)
        {
        xMatcher.MatcherHandle hMatcher = (xMatcher.MatcherHandle) hTarget;
        if ("next".equals(method.getName()))
            {
            Matcher matcher = hMatcher.getMatcher();
            boolean result  = matcher.find();
            return frame.assignValue(iReturn, xBoolean.makeHandle(result));
            }
        return super.invokeNativeN(frame, method, hTarget, ahArg, iReturn);
        }

    @Override
    public int invokeNativeNN(Frame frame, MethodStructure method, ObjectHandle hTarget,
                              ObjectHandle[] ahArg, int[] aiReturn)
        {
        xMatcher.MatcherHandle hMatcher = (xMatcher.MatcherHandle) hTarget;
        switch (method.getName())
            {
            case "group":
                {
                long    index   = ((ObjectHandle.JavaLong) ahArg[0]).getValue();
                Matcher matcher = hMatcher.getMatcher();
                String  value   = matcher.group((int) index);
                if (value != null)
                    {
                    return Utils.assignConditionalResult(
                        frame,
                        xString.INSTANCE.createConstHandle(frame, new StringConstant(pool(), value)),
                        aiReturn);
                    }
                return frame.assignValue(aiReturn[0], xBoolean.FALSE);
                }
            }
        return super.invokeNativeNN(frame, method, hTarget, ahArg, aiReturn);
        }

    private ObjectHandle makePossiblyNullHandle(String s)
        {
        if (s == null)
            {
            return xNullable.NULL;
            }
        return xString.makeHandle(s);
        }

    /**
     * Construct a new {@link MatcherHandle} representing the specified {@link Matcher}.
     *
     * @param frame    the current frame
     * @param matcher  the compiled {@link Matcher}
     * @param hRegEx the handle of the pattern used to create the result
     * @param iReturn  the register id to place the created handle into
     *
     * @return one of the {@link Op#R_NEXT}, {@link Op#R_CALL} or {@link Op#R_EXCEPTION}
     */
    public int createHandle(Frame frame, Matcher matcher, xRegEx.RegExHandle hRegEx, int iReturn)
        {
        TypeComposition clzStruct   = s_clzMatcherStruct;
        MethodStructure constructor = s_constructorMatcher;

        MatcherHandle         hStruct = new MatcherHandle(clzStruct, matcher, hRegEx);
        ObjectHandle.JavaLong anCount = xInt64.makeHandle(matcher.groupCount());
        ObjectHandle[]        ahVar   = Utils.ensureSize(new ObjectHandle[]{hRegEx, anCount}, constructor.getMaxVars());

        return proceedConstruction(frame, constructor, true, hStruct, ahVar, iReturn);
        }

    // ----- ObjectHandle --------------------------------------------------------------------------

    /**
     * The handle for a Matcher.
     */
    public static class MatcherHandle
            extends ObjectHandle.GenericHandle
        {
        protected MatcherHandle(TypeComposition clazz, Matcher matcher, xRegEx.RegExHandle hRegEx)
            {
            super(clazz);
            m_matcher = matcher;
            m_hRegEx  = hRegEx;
            }

        public Matcher getMatcher()
            {
            return m_matcher;
            }

        public xRegEx.RegExHandle getRegExHandle()
            {
            return m_hRegEx;
            }

        private final Matcher m_matcher;

        private final xRegEx.RegExHandle m_hRegEx;
        }

    // ----- constants -----------------------------------------------------------------------------

    private static TypeComposition s_clzMatcherStruct;
    private static MethodStructure s_constructorMatcher;
    }
