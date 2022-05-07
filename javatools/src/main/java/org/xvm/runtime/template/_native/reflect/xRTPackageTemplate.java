package org.xvm.runtime.template._native.reflect;


import org.xvm.asm.ClassStructure;
import org.xvm.asm.PackageStructure;

import org.xvm.runtime.Container;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.TypeComposition;


/**
 * Native PackageTemplate implementation.
 */
public class xRTPackageTemplate
        extends xRTClassTemplate
    {
    public static xRTPackageTemplate INSTANCE;

    public xRTPackageTemplate(Container container, ClassStructure structure, boolean fInstance)
        {
        super(container, structure, false);

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
            PACKAGE_TEMPLATE_COMPOSITION = ensureClass(f_container, getCanonicalType(),
                pool().ensureEcstasyTypeConstant("reflect.PackageTemplate"));
            }
        }

    @Override
    public int invokeNativeGet(Frame frame, String sPropName, ObjectHandle hTarget, int iReturn)
        {
        return super.invokeNativeGet(frame, sPropName, hTarget, iReturn);
        }


    // ----- ObjectHandle support ------------------------------------------------------------------

    /**
     * Obtain a {@link ComponentTemplateHandle} for the specified {@link PackageStructure}.
     *
     * @param pkg  the {@link PackageStructure} to obtain a {@link ComponentTemplateHandle} for
     *
     * @return the resulting {@link ComponentTemplateHandle}
     */
    public static ComponentTemplateHandle makeHandle(PackageStructure pkg)
        {
        // note: no need to initialize the struct because there are no natural fields
        return new ComponentTemplateHandle(PACKAGE_TEMPLATE_COMPOSITION, pkg);
        }


    // ----- constants -----------------------------------------------------------------------------

    private static TypeComposition PACKAGE_TEMPLATE_COMPOSITION;
    }