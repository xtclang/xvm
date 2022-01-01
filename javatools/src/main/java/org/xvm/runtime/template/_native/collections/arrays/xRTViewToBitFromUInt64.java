package org.xvm.runtime.template._native.collections.arrays;


import org.xvm.asm.ClassStructure;
import org.xvm.asm.ConstantPool;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.TemplateRegistry;


/**
 * The native RTViewToBit<UInt64> implementation.
 */
public class xRTViewToBitFromUInt64
        extends LongBasedBitView
    {
    public static xRTViewToBitFromUInt64 INSTANCE;

    public xRTViewToBitFromUInt64(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
        {
        super(templates, structure, 64);

        if (fInstance)
            {
            INSTANCE = this;
            }
        }

    @Override
    public void initNative()
        {
        }

    @Override
    public TypeConstant getCanonicalType()
        {
        ConstantPool pool = pool();
        return pool.ensureParameterizedTypeConstant(
                getInceptionClassConstant().getType(),
                pool.typeCUInt64());
        }
    }