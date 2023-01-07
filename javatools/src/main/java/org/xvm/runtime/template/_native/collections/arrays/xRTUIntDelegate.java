package org.xvm.runtime.template._native.collections.arrays;


import org.xvm.asm.ClassStructure;
import org.xvm.asm.ConstantPool;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.Container;
import org.xvm.runtime.ObjectHandle;

import org.xvm.runtime.template.numbers.LongLong;
import org.xvm.runtime.template.numbers.xUInt;


/**
 * Native RTDelegate<UInt> implementation.
 */
public class xRTUIntDelegate
        extends IntBasedDelegate
    {
    public static xRTUIntDelegate INSTANCE;

    public xRTUIntDelegate(Container container, ClassStructure structure, boolean fInstance)
        {
        super(container, structure, false);

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
                pool.typeUInt());
        }

    @Override
    protected ObjectHandle makeElementHandle(long l)
        {
        return xUInt.makeHandle(l);
        }

    @Override
    protected ObjectHandle makeElementHandle(LongLong ll)
        {
        return xUInt.INSTANCE.makeHandle(ll);
        }
   }