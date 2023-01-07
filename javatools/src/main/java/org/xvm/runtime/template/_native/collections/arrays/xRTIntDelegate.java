package org.xvm.runtime.template._native.collections.arrays;


import org.xvm.asm.ClassStructure;
import org.xvm.asm.ConstantPool;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.Container;
import org.xvm.runtime.ObjectHandle;

import org.xvm.runtime.template.numbers.LongLong;
import org.xvm.runtime.template.numbers.xInt;


/**
 * Native RTDelegate<Int> implementation.
 */
public class xRTIntDelegate
        extends IntBasedDelegate
    {
    public static xRTIntDelegate INSTANCE;

    public xRTIntDelegate(Container container, ClassStructure structure, boolean fInstance)
        {
        super(container, structure, true);

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
                pool.typeInt());
        }

    @Override
    protected ObjectHandle makeElementHandle(long l)
        {
        return xInt.makeHandle(l);
        }

    @Override
    protected ObjectHandle makeElementHandle(LongLong ll)
        {
        return xInt.INSTANCE.makeHandle(ll);
        }
   }