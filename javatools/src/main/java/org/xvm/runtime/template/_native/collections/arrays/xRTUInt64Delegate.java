package org.xvm.runtime.template._native.collections.arrays;


import org.xvm.asm.ClassStructure;
import org.xvm.asm.ConstantPool;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.TemplateRegistry;

import org.xvm.runtime.template.numbers.xUInt64;


/**
 * Native RTDelegate<UInt64> implementation.
 */
public class xRTUInt64Delegate
        extends LongDelegate
    {
    public static xRTUInt64Delegate INSTANCE;

    public xRTUInt64Delegate(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
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

    @Override
    public TypeConstant getCanonicalType()
        {
        ConstantPool pool = pool();
        return pool.ensureParameterizedTypeConstant(
                getInceptionClassConstant().getType(),
                pool.typeCUInt64());
        }

    @Override
    protected ObjectHandle makeElementHandle(long lValue)
        {
        return xUInt64.INSTANCE.makeJavaLong(lValue);
        }
   }