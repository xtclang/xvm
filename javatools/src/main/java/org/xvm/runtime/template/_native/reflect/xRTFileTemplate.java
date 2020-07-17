package org.xvm.runtime.template._native.reflect;


import org.xvm.asm.ClassStructure;
import org.xvm.asm.FileStructure;

import org.xvm.runtime.ClassComposition;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.TemplateRegistry;


/**
 * Native FileTemplate implementation.
 */
public class xRTFileTemplate
        extends xRTModuleTemplate
    {
    public static xRTFileTemplate INSTANCE;

    public xRTFileTemplate(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
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
        FILE_TEMPLATE_COMPOSITION = ensureClass(getCanonicalType(),
            pool().ensureEcstasyTypeConstant("reflect.FileTemplate"));

        markNativeProperty("mainModule");
        }

    @Override
    public int invokeNativeGet(Frame frame, String sPropName, ObjectHandle hTarget, int iReturn)
        {
        ComponentTemplateHandle hTemplate = (ComponentTemplateHandle) hTarget;
        switch (sPropName)
            {
            case "mainModule":
                {
                return frame.assignValue(iReturn, xRTModuleTemplate.makeHandle(
                    ((FileStructure) hTemplate.getComponent()).getModule()));
                }
            }

        return super.invokeNativeGet(frame, sPropName, hTarget, iReturn);
        }


    // ----- ObjectHandle support ------------------------------------------------------------------

    /**
     * Obtain a {@link ComponentTemplateHandle} for the specified file {@link FileStructure}.
     *
     * @param fileStruct  the {@link FileStructure} to obtain a {@link ComponentTemplateHandle} for
     *
     * @return the resulting {@link ComponentTemplateHandle}
     */
    public static ComponentTemplateHandle makeHandle(FileStructure fileStruct)
        {
        // note: no need to initialize the struct because there are no natural fields
        return new ComponentTemplateHandle(FILE_TEMPLATE_COMPOSITION, fileStruct);
        }


    // ----- constants -----------------------------------------------------------------------------

    private static ClassComposition FILE_TEMPLATE_COMPOSITION;
    }
