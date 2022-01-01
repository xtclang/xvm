package org.xvm.runtime.template._native.collections.arrays;


import org.xvm.asm.ClassStructure;
import org.xvm.asm.ConstantPool;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.TemplateRegistry;
import org.xvm.runtime.template.numbers.xInt64;


/**
 * Native RTDelegate<Int> implementation.
 */
public class xRTInt64Delegate
        extends LongDelegate
    {
    public static xRTInt64Delegate INSTANCE;

    public xRTInt64Delegate(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
        {
        super(templates, structure, true);

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
                pool.typeCInt64());
        }

    @Override
    protected ObjectHandle makeElementHandle(long lValue)
        {
        return xInt64.INSTANCE.makeJavaLong(lValue);
        }
   }