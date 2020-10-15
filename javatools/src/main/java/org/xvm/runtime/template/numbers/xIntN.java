package org.xvm.runtime.template.numbers;


import org.xvm.asm.ClassStructure;

import org.xvm.runtime.TemplateRegistry;


/**
 * Native IntN support.
 */
public class xIntN
        extends xUnconstrainedInteger
    {
    public static xIntN INSTANCE;

    public xIntN(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
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
        markNativeMethod("abs", VOID, THIS);

        getCanonicalType().invalidateTypeInfo();
        }
    }
