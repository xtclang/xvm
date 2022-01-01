package org.xvm.runtime.template._native.collections.arrays;


import org.xvm.asm.ClassStructure;
import org.xvm.asm.ConstantPool;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.TemplateRegistry;


/**
 * The native RTViewToBit<Int64> implementation.
 */
public class xRTViewToBitFromInt64
        extends LongBasedBitView
    {
    public static xRTViewToBitFromInt64 INSTANCE;

    public xRTViewToBitFromInt64(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
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
                getInceptionClassConstant().getType(), pool.typeCInt64());
        }
    }