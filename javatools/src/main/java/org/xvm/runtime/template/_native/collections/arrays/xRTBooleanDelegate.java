package org.xvm.runtime.template._native.collections.arrays;


import org.xvm.asm.ClassStructure;
import org.xvm.asm.ConstantPool;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.Container;
import org.xvm.runtime.ObjectHandle;

import org.xvm.runtime.template.xBoolean;
import org.xvm.runtime.template.xBoolean.BooleanHandle;


/**
 * Native RTDelegate<Boolean> implementation.
 */
public class xRTBooleanDelegate
        extends BitBasedDelegate
    {
    public static xRTBooleanDelegate INSTANCE;

    public xRTBooleanDelegate(Container container, ClassStructure structure, boolean fInstance)
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
                pool.typeBoolean());
        }

    @Override
    protected boolean isSet(ObjectHandle hValue)
        {
        return ((BooleanHandle) hValue).get();
        }

    @Override
    protected ObjectHandle makeBitHandle(boolean f)
        {
        return xBoolean.makeHandle(f);
        }
    }