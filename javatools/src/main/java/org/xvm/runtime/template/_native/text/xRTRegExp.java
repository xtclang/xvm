package org.xvm.runtime.template._native.text;

import java.util.regex.Pattern;
import org.xvm.asm.ClassStructure;
import org.xvm.asm.MethodStructure;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.TemplateRegistry;
import org.xvm.runtime.template.text.xString;
import org.xvm.runtime.template.xService;

/**
 */
public class xRTRegExp
        extends xService
    {
    public xRTRegExp(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
        {
        super(templates, structure, false);
        }

    @Override
    public void initNative()
        {
        markNativeMethod("compile", new String[]{"text.String"}, new String[]{"text.Pattern"});

        getCanonicalType().invalidateTypeInfo();
        }

    @Override
    public int invokeNative1(Frame frame, MethodStructure method,
                             ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        switch (method.getName())
            {
            case "compile":
                {
                String value = ((xString.StringHandle) hArg).getStringValue();
                Pattern pattern = Pattern.compile(value);
                return xRTPattern.INSTANCE.createHandle(frame, pattern, iReturn);
                }
            }
        return super.invokeNative1(frame, method, hTarget, hArg, iReturn);
        }
    }
