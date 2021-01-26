package org.xvm.runtime.template;


import org.xvm.asm.ClassStructure;

import org.xvm.runtime.ClassComposition;
import org.xvm.runtime.ClassTemplate;
import org.xvm.runtime.TemplateRegistry;


/**
 * Native Object functionality implementation.
 */
public class xObject
        extends ClassTemplate
    {
    public static xObject INSTANCE;
    public static ClassComposition CLASS;

    public xObject(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
        {
        super(templates, structure);

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
            CLASS = getCanonicalClass();

            markNativeMethod("equals", null, BOOLEAN);
            markNativeMethod("makeImmutable", VOID, null);

            getCanonicalType().invalidateTypeInfo();
            }
        }
    }
