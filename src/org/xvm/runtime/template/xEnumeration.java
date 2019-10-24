package org.xvm.runtime.template;


import org.xvm.asm.ClassStructure;

import org.xvm.runtime.TemplateRegistry;
import org.xvm.runtime.template.reflect.xClass;

/**
 * Native Enumeration implementation.
 */
public class xEnumeration
        extends xClass
    {
    public static xEnumeration INSTANCE;

    public xEnumeration(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
        {
        super(templates, structure, false);

        if (fInstance)
            {
            INSTANCE = this;
            }
        }

    @Override
    public void initDeclared()
        {
        if (this == INSTANCE)
            {
            // REVIEW even though the super is native, the PropertyInfo requires a field unless
            //        we mark this property as native too
            markNativeProperty("name");

            getCanonicalType().invalidateTypeInfo();
            }
        }
    }
