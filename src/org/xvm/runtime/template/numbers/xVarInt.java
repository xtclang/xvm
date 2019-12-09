package org.xvm.runtime.template.numbers;


import org.xvm.asm.ClassStructure;

import org.xvm.runtime.TemplateRegistry;


/**
 * Native VarInt support.
 */
public class xVarInt
        extends xUnconstrainedInteger
    {
    public static xVarInt INSTANCE;

    public xVarInt(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
        {
        super(templates, structure);

        if (fInstance)
            {
            INSTANCE = this;
            }
        }

    @Override
    public void initDeclared()
        {
        markNativeMethod("abs", VOID, THIS);

        getCanonicalType().invalidateTypeInfo();
        }
    }
