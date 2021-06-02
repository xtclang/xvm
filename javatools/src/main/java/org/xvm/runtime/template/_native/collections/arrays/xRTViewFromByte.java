package org.xvm.runtime.template._native.collections.arrays;


import org.xvm.asm.ClassStructure;

import org.xvm.runtime.TemplateRegistry;
import org.xvm.runtime.TypeComposition;

import org.xvm.runtime.template.collections.xArray.Mutability;

import org.xvm.runtime.template._native.collections.arrays.xRTSlicingDelegate.SliceHandle;


/**
 * The native RTViewFromByte base implementation.
 */
public class xRTViewFromByte
        extends xRTView
    {
    public static xRTViewFromByte INSTANCE;

    public xRTViewFromByte(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
        {
        super(templates, structure);

        if (fInstance)
            {
            INSTANCE = this;
            }
        }

    @Override
    public void registerNativeTemplates()
        {
        if (this == INSTANCE)
            {
            registerNativeTemplate(new xRTViewFromByteToInt8 (f_templates, f_struct, true));
            registerNativeTemplate(new xRTViewFromByteToInt16(f_templates, f_struct, true));
            registerNativeTemplate(new xRTViewFromByteToInt64(f_templates, f_struct, true));
            }
        }

    @Override
    public void initNative()
        {
        }

    /**
     * Create an ArrayDelegate<NumType> view into the specified ArrayDelegate<Byte> source.
     *
     * @param hSource     the source (of byte type) delegate
     * @param mutability  the desired mutability
     * @param nBytes      the number of bytes for the numeric type of this byte-based view
     */
    public DelegateHandle createByteView(DelegateHandle hSource, Mutability mutability,
                                         int nBytes)
        {
        if (hSource instanceof SliceHandle)
            {
            // e.g.: bytes.slice().asInt64Array() -> bytes.asInt64Array().slice()
            SliceHandle hSlice = (SliceHandle) hSource;
            ViewHandle  hView  = new ViewHandle(getCanonicalClass(), hSlice.f_hSource,
                                    hSlice.f_hSource.m_cSize/nBytes, mutability);

            return slice(hView, hSlice.f_ofStart/nBytes, hSlice.m_cSize/nBytes, hSlice.f_fReverse);
            }
        return new ViewHandle(getCanonicalClass(), hSource, hSource.m_cSize/nBytes, mutability);
        }


    // ----- handle --------------------------------------------------------------------------------

    /**
     * DelegateArray<NumType> view delegate.
     */
    protected static class ViewHandle
            extends DelegateHandle
        {
        public final DelegateHandle f_hSource;

        protected ViewHandle(TypeComposition clazz, DelegateHandle hSource,
                             long cSize, Mutability mutability)
            {
            super(clazz, mutability);

            f_hSource = hSource;
            m_cSize   = cSize;
            }
        }
    }