package org.xvm.runtime.template._native.reflect;


import org.xvm.asm.ClassStructure;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.PackageStructure;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.Container;
import org.xvm.runtime.TypeComposition;


/**
 * Native PackageTemplate implementation.
 */
public class xRTPackageTemplate
        extends xRTClassTemplate {
    public xRTPackageTemplate(Container container, ClassStructure structure, boolean fInstance) {
        super(container, structure, false);
    }

    @Override
    public void initNative() {
        ConstantPool pool = f_container.getConstantPool();

        PACKAGE_TEMPLATE_TYPE = pool.ensureEcstasyTypeConstant("reflect.PackageTemplate");
    }


    // ----- ObjectHandle support ------------------------------------------------------------------

    /**
     * Obtain a {@link ComponentTemplateHandle} for the specified {@link PackageStructure}.
     *
     * @param pkg  the {@link PackageStructure} to obtain a {@link ComponentTemplateHandle} for
     *
     * @return the resulting {@link ComponentTemplateHandle}
     */
    public static ComponentTemplateHandle makeHandle(Container container, PackageStructure pkg) {
        // note: no need to initialize the struct because there are no natural fields
        xRTPackageTemplate template = container.nativeTemplate(xRTPackageTemplate.class);
        TypeComposition clz      = template.ensureClass(container, template.getCanonicalType(),
                PACKAGE_TEMPLATE_TYPE);
        return new ComponentTemplateHandle(clz, pkg);
    }


    // ----- constants -----------------------------------------------------------------------------

    private static TypeConstant PACKAGE_TEMPLATE_TYPE;
}
