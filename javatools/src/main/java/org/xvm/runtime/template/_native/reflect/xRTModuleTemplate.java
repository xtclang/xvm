package org.xvm.runtime.template._native.reflect;


import org.xvm.asm.ClassStructure;
import org.xvm.asm.ModuleStructure;

import org.xvm.runtime.ClassComposition;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.TemplateRegistry;

import org.xvm.runtime.template.text.xString;


/**
 * Native ModuleTemplate implementation.
 */
public class xRTModuleTemplate
        extends xRTClassTemplate
    {
    public static xRTModuleTemplate INSTANCE;

    public xRTModuleTemplate(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
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
        if (this == INSTANCE)
            {
            MODULE_TEMPLATE_COMPOSITION = ensureClass(getCanonicalType(),
                pool().ensureEcstasyTypeConstant("reflect.ModuleTemplate"));

            markNativeProperty("qualifiedName");
            }
        }

    @Override
    public int invokeNativeGet(Frame frame, String sPropName, ObjectHandle hTarget, int iReturn)
        {
        ComponentTemplateHandle hTemplate = (ComponentTemplateHandle) hTarget;
        switch (sPropName)
            {
            case "qualifiedName":
                {
                ModuleStructure module = (ModuleStructure) hTemplate.getComponent();
                return frame.assignValue(iReturn,
                    xString.makeHandle(module.getIdentityConstant().getName()));
                }
            }

        return super.invokeNativeGet(frame, sPropName, hTarget, iReturn);
        }


    // ----- ObjectHandle support ------------------------------------------------------------------

    /**
     * Obtain a {@link ComponentTemplateHandle} for the specified {@link ModuleStructure}.
     *
     * @param module  the {@link ModuleStructure} to obtain a {@link ComponentTemplateHandle} for
     *
     * @return the resulting {@link ComponentTemplateHandle}
     */
    public static ComponentTemplateHandle makeHandle(ModuleStructure module)
        {
        // note: no need to initialize the struct because there are no natural fields
        return new ComponentTemplateHandle(MODULE_TEMPLATE_COMPOSITION, module);
        }


    // ----- constants -----------------------------------------------------------------------------

    private static ClassComposition MODULE_TEMPLATE_COMPOSITION;
    }
