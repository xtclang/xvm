package org.xvm.runtime.template.collections;


import org.xvm.asm.ClassStructure;
import org.xvm.asm.ConstantPool;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.TemplateRegistry;

import org.xvm.runtime.template.xBoolean;
import org.xvm.runtime.template.xBoolean.BooleanHandle;


/**
 * Native Array<Boolean> implementation.
 */
public class xBooleanArray
        extends BitBasedArray
    {
    public static xBooleanArray INSTANCE;

    public xBooleanArray(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
        {
        super(templates, structure);

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
        return pool.ensureArrayType(pool.typeBoolean());
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
