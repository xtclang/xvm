package org.xvm.runtime.template._native.reflect;


import org.xvm.asm.ClassStructure;
import org.xvm.asm.PropertyStructure;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.TemplateRegistry;


/**
 * Native PropertyTemplate implementation.
 */
public class xRTPropertyTemplate
        extends xRTComponentTemplate
    {
    public xRTPropertyTemplate(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
        {
        super(templates, structure, false);
        }

    @Override
    public void initNative()
        {
        markNativeProperty("type");

        super.initNative();
        }

    @Override
    public int invokeNativeGet(Frame frame, String sPropName, ObjectHandle hTarget, int iReturn)
        {
        ComponentTemplateHandle hProp = (ComponentTemplateHandle) hTarget;
        switch (sPropName)
            {
            case "type":
                return getPropertyType(frame, hProp, iReturn);
            }

        return super.invokeNativeGet(frame, sPropName, hTarget, iReturn);
        }


    // ----- property implementations --------------------------------------------------------------

    /**
     * Implements property: type.get()
     */
    public int getPropertyType(Frame frame, ComponentTemplateHandle hProp, int iReturn)
        {
        PropertyStructure prop = (PropertyStructure) hProp.getComponent();
        TypeConstant      type = prop.getType();

        return 0;
        }
    }
