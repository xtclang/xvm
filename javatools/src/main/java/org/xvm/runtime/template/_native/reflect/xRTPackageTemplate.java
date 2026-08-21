package org.xvm.runtime.template._native.reflect;


import org.xvm.asm.ClassStructure;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.PackageStructure;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.Container;
import org.xvm.runtime.TypeComposition;

import org.xvm.util.Lazy;


/**
 * Native PackageTemplate implementation.
 */
public class xRTPackageTemplate
        extends xRTClassTemplate {
    public xRTPackageTemplate(Container container, ClassStructure structure) {
        super(container, structure);
    }

    @Override
    public void initNative() {
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
        xRTPackageTemplate template = container.getTemplate("_native.reflect.RTPackageTemplate",
                xRTPackageTemplate.class);
        TypeComposition clz = template.ensureClass(container,
                template.getCanonicalType(), template.f_typePackageTemplate.get());
        return new ComponentTemplateHandle(clz, pkg);
    }


    // ----- data members --------------------------------------------------------------------------

    /**
     * PackageTemplate is a container-owned type constant, not JVM-global metadata.
     */
    private final Lazy<TypeConstant> f_typePackageTemplate = Lazy.of(() -> {
        ConstantPool pool = f_container.getConstantPool();
        return pool.ensureEcstasyTypeConstant("reflect.PackageTemplate");
    });
}
