package org.xvm.runtime.template._native.collections.arrays;


import org.xvm.asm.ClassStructure;
import org.xvm.asm.ConstantPool;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.TemplateRegistry;


/**
 * The native RTViewToBit<Int32> implementation.
 */
public class xRTViewToBitFromInt32
        extends LongBasedBitView
    {
    public static xRTViewToBitFromInt32 INSTANCE;

    public xRTViewToBitFromInt32(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
        {
        super(templates, structure, 32);

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
                pool.typeCInt32());
        }
    }