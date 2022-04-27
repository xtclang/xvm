package org.xvm.runtime.template._native.collections.arrays;


import org.xvm.asm.ClassStructure;
import org.xvm.asm.ConstantPool;

import org.xvm.asm.Op;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.ClassComposition;
import org.xvm.runtime.Container;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.GenericHandle;
import org.xvm.runtime.TypeComposition;

import org.xvm.runtime.template.collections.xArray.Mutability;


import org.xvm.runtime.template._native.collections.arrays.xRTNibbleDelegate.NibbleArrayHandle;
import org.xvm.runtime.template._native.collections.arrays.xRTSlicingDelegate.SliceHandle;

/**
 * The native RTViewToBit<Nibble> implementation.
 */
public class xRTViewToBitFromNibble
        extends xRTViewToBit
        implements BitView
    {
    public static xRTViewToBitFromNibble INSTANCE;

    public xRTViewToBitFromNibble(Container container, ClassStructure structure, boolean fInstance)
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
                pool.typeNibble());
        }

    @Override
    public DelegateHandle createBitViewDelegate(DelegateHandle hSource, Mutability mutability)
        {
        ClassComposition clzView = getCanonicalClass();
        if (hSource instanceof SliceHandle hSlice)
            {
            // nibbles.slice().asBitArray() -> nibbles.asBitArray().slice()
            NibbleArrayHandle hNibbles = (NibbleArrayHandle) hSlice.f_hSource;
            ViewHandle        hView    = new ViewHandle(clzView,
                        hNibbles, hNibbles.m_cSize*4, mutability);
            return slice(hView, hSlice.f_ofStart*4, hSlice.m_cSize*4, false);
            }

        if (hSource instanceof xRTViewFromBit.ViewHandle hView)
            {
            return hView.f_hSource;
            }

        return new ViewHandle(clzView, (NibbleArrayHandle) hSource, hSource.m_cSize*4, mutability);
        }


    // ----- RTDelegate API ------------------------------------------------------------------------

    @Override
    protected DelegateHandle createCopyImpl(DelegateHandle hTarget, Mutability mutability,
                                            long ofStart, long cSize, boolean fReverse)
        {
        ViewHandle hView = (ViewHandle) hTarget;

        byte[] abBits = getBits(hView, ofStart, cSize, fReverse);

        return xRTNibbleDelegate.INSTANCE.makeHandle(abBits, cSize, mutability);
        }

    @Override
    protected int extractArrayValueImpl(Frame frame, DelegateHandle hTarget, long lIndex, int iReturn)
        {
        ViewHandle hView = (ViewHandle) hTarget;

        return xRTNibbleDelegate.assignNibble(frame,
                xRTNibbleDelegate.getNibble(hView.f_hSource.m_abValue, lIndex), iReturn);
        }

    @Override
    public int assignArrayValueImpl(Frame frame, DelegateHandle hTarget, long lIndex,
                                    ObjectHandle hValue)
        {
        ViewHandle hView = (ViewHandle) hTarget;

        xRTNibbleDelegate.setNibble(hView.f_hSource.m_abValue, lIndex,
                xRTNibbleDelegate.getValue((GenericHandle) hValue));
        return Op.R_NEXT;
        }


    // ----- BitView implementation ----------------------------------------------------------------

    @Override
    public byte[] getBits(DelegateHandle hDelegate, long ofStart, long cBits, boolean fReverse)
        {
        ViewHandle hView = (ViewHandle) hDelegate;

        byte[] abBits = BitBasedDelegate.extractBits(hView.f_hSource.m_abValue, ofStart, cBits);

        if (fReverse)
            {
            abBits = BitBasedDelegate.reverseBits(abBits, cBits);
            }
        return abBits;
        }

    @Override
    public boolean extractBit(DelegateHandle hDelegate, long of)
        {
        ViewHandle hView = (ViewHandle) hDelegate;

        return BitBasedDelegate.getBit(hView.f_hSource.m_abValue, of);
        }

    @Override
    public void assignBit(DelegateHandle hDelegate, long of, boolean fBit)
        {
        ViewHandle hView = (ViewHandle) hDelegate;

        BitBasedDelegate.setBit(hView.f_hSource.m_abValue, of, fBit);
        }


    // ----- ByteView implementation ---------------------------------------------------------------

    @Override
    public byte[] getBytes(DelegateHandle hDelegate, long ofStart, long cBytes, boolean fReverse)
        {
        return getBits(hDelegate, ofStart*4, cBytes*4, fReverse);
        }

    @Override
    public byte extractByte(DelegateHandle hDelegate, long of)
        {
        ViewHandle hView = (ViewHandle) hDelegate;

        return hView.f_hSource.m_abValue[(int) of];
        }

    @Override
    public void assignByte(DelegateHandle hDelegate, long of, byte bValue)
        {
        ViewHandle hView = (ViewHandle) hDelegate;

        hView.f_hSource.m_abValue[(int) of] = bValue;
        }


    // ----- handle --------------------------------------------------------------------------------

    /**
     * DelegateArray<Bit> view delegate.
     */
    protected static class ViewHandle
            extends DelegateHandle
        {
        protected final NibbleArrayHandle f_hSource;

        protected ViewHandle(TypeComposition clazz, NibbleArrayHandle hSource, long cSize,
                             Mutability mutability)
            {
            super(clazz, mutability);

            f_hSource = hSource;
            m_cSize   = cSize;
            }
        }
    }