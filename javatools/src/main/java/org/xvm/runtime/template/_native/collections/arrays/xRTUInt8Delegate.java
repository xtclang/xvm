package org.xvm.runtime.template._native.collections.arrays;


import org.xvm.asm.ClassStructure;
import org.xvm.asm.ConstantPool;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.Container;
import org.xvm.runtime.ObjectHandle;

import org.xvm.runtime.template.numbers.xUInt8;


/**
 * Native RTDelegate<Byte> implementation.
 */
public class xRTUInt8Delegate
        extends ByteBasedDelegate
        implements ByteView
    {
    public static xRTUInt8Delegate INSTANCE;

    public xRTUInt8Delegate(Container container, ClassStructure structure, boolean fInstance)
        {
        super(container, structure);

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
                pool.typeByte());
        }

    @Override
    protected ObjectHandle makeElementHandle(long lValue)
        {
        return xUInt8.INSTANCE.makeJavaLong(lValue);
        }

    /**
     * Obtain an array of bytes from the specified ByteArrayHandle.
     */
    public static byte[] getBytes(ByteArrayHandle hDelegate)
        {
        return INSTANCE.getBytes(hDelegate, 0, hDelegate.m_cSize, false);
        }
    }