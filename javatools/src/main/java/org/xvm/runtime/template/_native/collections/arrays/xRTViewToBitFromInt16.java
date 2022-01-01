package org.xvm.runtime.template._native.collections.arrays;


import org.xvm.asm.ClassStructure;
import org.xvm.asm.ConstantPool;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.TemplateRegistry;


/**
 * The native RTViewToBit<Int16> implementation.
 */
public class xRTViewToBitFromInt16
        extends LongBasedBitView
    {
    public static xRTViewToBitFromInt16 INSTANCE;

    public xRTViewToBitFromInt16(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
        {
        super(templates, structure, 16);

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
                pool.typeCInt16());
        }
    }