package org.xvm.runtime.template.collections;


import org.xvm.asm.ClassStructure;
import org.xvm.asm.ConstantPool;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.TemplateRegistry;

import org.xvm.runtime.template._native.collections.arrays.xRTCharDelegate;
import org.xvm.runtime.template._native.collections.arrays.xRTCharDelegate.CharArrayHandle;
import org.xvm.runtime.template._native.collections.arrays.xRTDelegate.DelegateHandle;
import org.xvm.runtime.template._native.collections.arrays.xRTSlicingDelegate.SliceHandle;


/**
 * Native Array<Char> implementation.
 */
public class xCharArray
        extends xArray
    {
    public static xCharArray INSTANCE;

    public xCharArray(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
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
        return pool.ensureArrayType(pool.typeChar());
        }

    /**
     * Extract a array of chars from the Array<Char> handle.
     */
    public static char[] getChars(ArrayHandle hArray)
        {
        DelegateHandle hDelegate = hArray.m_hDelegate;
        if (hDelegate instanceof SliceHandle)
            {
            SliceHandle     hSlice = (SliceHandle) hDelegate;
            CharArrayHandle hChars = (CharArrayHandle) hSlice.f_hSource;
            return xRTCharDelegate.getChars(hChars,
                    hSlice.f_ofStart, hSlice.m_cSize, hSlice.f_fReverse);
            }

        if (hDelegate instanceof CharArrayHandle)
            {
            CharArrayHandle hChars = (CharArrayHandle) hDelegate;
            return xRTCharDelegate.getChars(hChars, 0, hChars.m_cSize, false);
            }
        throw new UnsupportedOperationException();
        }
    }
