package org.xvm.runtime.template._native.reflect;


import org.xvm.asm.Annotation;
import org.xvm.asm.ClassStructure;
import org.xvm.asm.MethodStructure;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.TemplateRegistry;
import org.xvm.runtime.Utils;

/**
 * Native Method implementation.
 */
public class xRTMethodTemplate
        extends xRTComponentTemplate
    {
    public xRTMethodTemplate(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
        {
        super(templates, structure, false);
        }

    @Override
    public void initNative()
        {
        markNativeProperty("annotations");

        super.initNative();
        }

    @Override
    public int invokeNativeGet(Frame frame, String sPropName, ObjectHandle hTarget, int iReturn)
        {
        ComponentTemplateHandle hMethod = (ComponentTemplateHandle) hTarget;
        switch (sPropName)
            {
            case "annotations":
                return getPropertyAnnotations(frame, hMethod, iReturn);
            }

        return super.invokeNativeGet(frame, sPropName, hTarget, iReturn);
        }


    // ----- property implementations --------------------------------------------------------------

    /**
     * Implements property: annotations.get()
     */
    public int getPropertyAnnotations(Frame frame, ComponentTemplateHandle hMethod, int iReturn)
        {
        MethodStructure method = (MethodStructure) hMethod.getComponent();
        Annotation[]    aAnno  = method.getAnnotations();

        return aAnno.length > 0
                ? new Utils.CreateAnnos(aAnno, iReturn).doNext(frame)
                : frame.assignValue(iReturn,
                    Utils.makeAnnoArrayHandle(frame.poolContext(), Utils.OBJECTS_NONE));
        }
    }
