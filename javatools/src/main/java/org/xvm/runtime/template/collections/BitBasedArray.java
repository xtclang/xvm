package org.xvm.runtime.template.collections;


import org.xvm.asm.ClassStructure;

import org.xvm.runtime.TemplateRegistry;


/**
 * A base class for native Array implementations based on bit arrays.
 */
public abstract class BitBasedArray
        extends xArray
    {
    public static BitBasedArray INSTANCE;

    protected BitBasedArray(TemplateRegistry templates, ClassStructure structure)
        {
        super(templates, structure, false);
        }

    @Override
    public void initNative()
        {
        }
    }
