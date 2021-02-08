package org.xvm.runtime.template._native.text;

import java.util.regex.Matcher;
import org.xvm.asm.ClassStructure;
import org.xvm.asm.Constants;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;
import org.xvm.runtime.TypeComposition;
import org.xvm.runtime.ClassTemplate;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.TemplateRegistry;
import org.xvm.runtime.TypeComposition;
import org.xvm.runtime.Utils;
import org.xvm.runtime.template.numbers.xInt64;
import org.xvm.runtime.template.text.xString;
import org.xvm.runtime.template.xBoolean;
import org.xvm.runtime.template.xConst;
import org.xvm.runtime.template.xNullable;

public class xRTMatcher
    extends xConst
    {
    public static xRTMatcher INSTANCE;

    public xRTMatcher(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
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
        super.initNative();

        markNativeProperty("matched");
        markNativeProperty("groupCount");
        markNativeProperty("pattern");
        markNativeMethod("group", new String[]{"numbers.Int64"}, null);
        markNativeMethod("find", null, new String[] {"Boolean"});

        getCanonicalType().invalidateTypeInfo();

        ClassTemplate   template = f_templates.getTemplate("text.Matcher");
        TypeComposition clz      = ensureClass(template.getCanonicalType());

        s_clzMatcherStruct = clz.ensureAccess(Constants.Access.STRUCT);
        s_constructorMatcher = getStructure().findConstructor();
        }

    @Override
    public int invokeNativeGet(Frame frame, String sPropName, ObjectHandle hTarget, int iReturn)
        {
        MatcherHandle hResult = (MatcherHandle) hTarget;
        switch (sPropName)
            {
            case "matched":
                {
                Matcher result = hResult.getMatcher();
                return frame.assignValue(iReturn, xBoolean.makeHandle(result.matches()));
                }
            case "groupCount":
                {
                Matcher result = hResult.getMatcher();
                return frame.assignValue(iReturn, xInt64.makeHandle(result.groupCount()));
                }
            case "pattern":
                {
                return frame.assignValue(iReturn, hResult.getPattern());
                }
            }

        return super.invokeNativeGet(frame, sPropName, hTarget, iReturn);
        }

    @Override
    public int invokeNative1(Frame frame, MethodStructure method,
                             ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        xRTMatcher.MatcherHandle hMatcher = (xRTMatcher.MatcherHandle) hTarget;
        switch (method.getName())
            {
            case "group":
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
        xRTMatcher.MatcherHandle hMatcher = (xRTMatcher.MatcherHandle) hTarget;
        switch (method.getName())
            {
            case "find":
                {
                Matcher matcher = hMatcher.getMatcher();
                boolean result  = matcher.find();
                return frame.assignValue(iReturn, xBoolean.makeHandle(result));
                }
            case "reset":
                {
                Matcher matcher = hMatcher.getMatcher();
                matcher.reset();
                return frame.assignValue(iReturn, hMatcher);
                }
            }
        return super.invokeNativeN(frame, method, hTarget, ahArg, iReturn);
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
     * @param hPattern the handle of the pattern used to create the result
     * @param iReturn  the register id to place the created handle into
     *
     * @return one of the {@link Op#R_NEXT}, {@link Op#R_CALL} or {@link Op#R_EXCEPTION}
     */
    public int createHandle(Frame frame, Matcher matcher, xRTPattern.PatternHandle hPattern, int iReturn)
        {
        TypeComposition clzStruct   = s_clzMatcherStruct;
        MethodStructure  constructor = s_constructorMatcher;

        MatcherHandle hStruct = new MatcherHandle(clzStruct, matcher, hPattern);
        ObjectHandle[] ahVar   = Utils.ensureSize(Utils.OBJECTS_NONE, constructor.getMaxVars());

        return proceedConstruction(frame, constructor, true, hStruct, ahVar, iReturn);
        }

    // ----- ObjectHandle --------------------------------------------------------------------------

    public static class MatcherHandle
            extends ObjectHandle.GenericHandle
        {
        protected final Matcher f_result;

        protected final xRTPattern.PatternHandle f_hPattern;

        protected MatcherHandle(TypeComposition clazz, Matcher result, xRTPattern.PatternHandle hPattern)
            {
            super(clazz);
            f_result   = result;
            f_hPattern = hPattern;
            }

        public Matcher getMatcher()
            {
            return f_result;
            }

        public xRTPattern.PatternHandle getPattern()
            {
            return f_hPattern;
            }
        }

    // ----- constants -----------------------------------------------------------------------------

    private static TypeComposition s_clzMatcherStruct;
    private static MethodStructure s_constructorMatcher;
    }
