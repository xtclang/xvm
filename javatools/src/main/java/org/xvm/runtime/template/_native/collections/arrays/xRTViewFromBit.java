package org.xvm.runtime.template._native.collections.arrays;


import org.xvm.asm.ClassStructure;

import org.xvm.runtime.ClassTemplate;
import org.xvm.runtime.Container;
import org.xvm.runtime.TypeComposition;

import org.xvm.runtime.template.collections.xArray.Mutability;


/**
 * The native RTViewFromBit base implementation.
 */
public class xRTViewFromBit
        extends xRTView
        implements ByteView
    {
    public static xRTViewFromBit INSTANCE;

    public xRTViewFromBit(Container container, ClassStructure structure, boolean fInstance)
        {
        super(container, structure);

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
            registerNativeTemplate(new xRTViewFromBitToBoolean (f_container, f_struct, true));
            registerNativeTemplate(new xRTViewFromBitToByte    (f_container, f_struct, true));
            registerNativeTemplate(new xRTViewFromBitToNibble  (f_container, f_struct, true));
            }
        }

    @Override
    public void initNative()
        {
        }

    /**
     * Create an ArrayDelegate<NumType> view into the specified ArrayDelegate<Bit> source.
     *
     * @param hSource     the source (of bit type) delegate
     * @param mutability  the desired mutability
     */
    public DelegateHandle createBitViewDelegate(DelegateHandle hSource, Mutability mutability)
        {
        throw new UnsupportedOperationException();
        }


    // ----- ByteView implementation ---------------------------------------------------------------

    @Override
    public byte[] getBytes(DelegateHandle hDelegate, long ofStart, long cBytes, boolean fReverse)
        {
        ViewHandle     hView   = (ViewHandle) hDelegate;
        DelegateHandle hSource = hView.f_hSource;
        ClassTemplate  tSource = hSource.getTemplate();

        if (tSource instanceof ByteView tView)
            {
            return tView.getBytes(hSource, ofStart, cBytes, fReverse);
            }

        throw new UnsupportedOperationException();
        }

    @Override
    public byte extractByte(DelegateHandle hDelegate, long of)
        {
        ViewHandle     hView   = (ViewHandle) hDelegate;
        DelegateHandle hSource = hView.f_hSource;
        ClassTemplate  tSource = hSource.getTemplate();

        if (tSource instanceof ByteView tView)
            {
            return tView.extractByte(hSource, of);
            }

        throw new UnsupportedOperationException();
        }

    @Override
    public void assignByte(DelegateHandle hDelegate, long of, byte bValue)
        {
        ViewHandle     hView   = (ViewHandle) hDelegate;
        DelegateHandle hSource = hView.f_hSource;
        ClassTemplate  tSource = hSource.getTemplate();

        if (tSource instanceof ByteView tView)
            {
            tView.assignByte(hSource, of, bValue);
            return;
            }

        throw new UnsupportedOperationException();
        }

    // ----- handle --------------------------------------------------------------------------------

    /**
     * DelegateArray<NumType> view delegate.
     */
    protected static class ViewHandle
            extends xRTView.ViewHandle
        {
        public final DelegateHandle f_hSource;

        protected ViewHandle(TypeComposition clazz, DelegateHandle hSource,
                             long cSize, Mutability mutability)
            {
            super(clazz, mutability);

            f_hSource = hSource;
            m_cSize   = cSize;
            }

        @Override
        public DelegateHandle getSource()
            {
            return f_hSource;
            }
        }
    }