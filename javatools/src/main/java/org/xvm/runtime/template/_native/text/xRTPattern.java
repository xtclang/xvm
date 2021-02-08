package org.xvm.runtime.template._native.text;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.xvm.asm.ClassStructure;
import org.xvm.asm.Constants;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;
import org.xvm.runtime.ClassTemplate;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.TemplateRegistry;
import org.xvm.runtime.TypeComposition;
import org.xvm.runtime.Utils;
import org.xvm.runtime.template.text.xString;
import org.xvm.runtime.template.xConst;

public class xRTPattern
        extends xConst
    {
    public static xRTPattern INSTANCE;

    public xRTPattern(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
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
        markNativeMethod("match", new String[]{"text.String"}, new String[]{"text.Matcher"});

        getCanonicalType().invalidateTypeInfo();

        ClassTemplate    template = f_templates.getTemplate("text.Pattern");
        TypeComposition clz      = ensureClass(template.getCanonicalType());

        s_clzPatternStruct   = clz.ensureAccess(Constants.Access.STRUCT);
        s_constructorPattern = getStructure().findConstructor();
        }

    @Override
    public int invokeNativeGet(Frame frame, String sPropName, ObjectHandle hTarget, int iReturn)
        {
        xRTPattern.PatternHandle hPattern = (xRTPattern.PatternHandle) hTarget;
        switch (sPropName)
            {
            case "pattern":
                {
                Pattern pattern = hPattern.getPattern();
                return frame.assignValue(iReturn, xString.makeHandle(pattern.pattern()));
                }
            }

        return super.invokeNativeGet(frame, sPropName, hTarget, iReturn);
        }

    @Override
    public int invokeNative1(Frame frame, MethodStructure method,
                             ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        PatternHandle hPattern = (PatternHandle) hTarget;
        switch (method.getName())
            {
            case "match":
                {
                String value = ((xString.StringHandle) hArg).getStringValue();
                Pattern pattern = hPattern.getPattern();
                Matcher matcher = pattern.matcher(value);
                return xRTMatcher.INSTANCE.createHandle(frame, matcher, hPattern, iReturn);
                }
            }
        return super.invokeNative1(frame, method, hTarget, hArg, iReturn);
        }

    /**
     * Construct a new {@link PatternHandle} representing the specified {@link Pattern}.
     *
     * @param frame    the current frame
     * @param pattern  the compiled {@link Pattern}
     * @param iReturn  the register id to place the created handle into
     *
     * @return one of the {@link Op#R_NEXT}, {@link Op#R_CALL} or {@link Op#R_EXCEPTION}
     */
    public int createHandle(Frame frame, Pattern pattern, int iReturn)
        {
        TypeComposition clzStruct   = s_clzPatternStruct;
        MethodStructure  constructor = s_constructorPattern;

        PatternHandle hStruct = new PatternHandle(clzStruct, pattern);
        ObjectHandle[] ahVar   = Utils.ensureSize(Utils.OBJECTS_NONE, constructor.getMaxVars());

        return proceedConstruction(frame, constructor, true, hStruct, ahVar, iReturn);
        }

    public static class PatternHandle
            extends ObjectHandle.GenericHandle
        {
        protected PatternHandle(TypeComposition clazz, Pattern pattern)
            {
            super(clazz);

            f_pattern = pattern;
            }

        public Pattern getPattern()
            {
            return f_pattern;
            }

        protected final Pattern f_pattern;
        }

    // ----- constants -----------------------------------------------------------------------------

    private static TypeComposition s_clzPatternStruct;

    private static MethodStructure s_constructorPattern;
    }
