package org.xvm.runtime.template;


import org.xvm.asm.ClassStructure;

import org.xvm.runtime.TemplateRegistry;

import org.xvm.runtime.template.reflect.xClass;

/**
 * Native Enumeration implementation.
 *
 * TODO GG: remove if no native support is needed
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
    public void initNative()
        {
        }
    }
