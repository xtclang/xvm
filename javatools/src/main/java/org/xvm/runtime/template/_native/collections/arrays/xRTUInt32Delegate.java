package org.xvm.runtime.template._native.collections.arrays;


import org.xvm.asm.ClassStructure;
import org.xvm.asm.ConstantPool;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.TemplateRegistry;

import org.xvm.runtime.template.numbers.xUInt32;


/**
 * Native RTDelegate<UInt32> implementation.
 */
public class xRTUInt32Delegate
        extends LongBasedDelegate
        implements ByteView
    {
    public static xRTUInt32Delegate INSTANCE;

    public xRTUInt32Delegate(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
        {
        super(templates, structure, 32, false);

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
                pool.typeCUInt32());
        }

    @Override
    protected ObjectHandle makeElementHandle(long lValue)
        {
        return xUInt32.INSTANCE.makeJavaLong(lValue);
        }
    }
