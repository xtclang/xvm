package org.xvm.runtime.template._native.collections.arrays;


import org.xvm.asm.ClassStructure;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.Op;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.JavaLong;
import org.xvm.runtime.TemplateRegistry;
import org.xvm.runtime.TypeComposition;

import org.xvm.runtime.template.collections.xArray.Mutability;

import org.xvm.runtime.template.numbers.xBit;

import org.xvm.runtime.template._native.collections.arrays.LongBasedDelegate.LongArrayHandle;
import org.xvm.runtime.template._native.collections.arrays.xRTSlicingDelegate.SliceHandle;


/**
 * The native RTViewToBit<Int64> implementation.
 */
public class xRTViewToBitFromInt64
        extends xRTViewToBit
        implements BitView
    {
    public static xRTViewToBitFromInt64 INSTANCE;

    public xRTViewToBitFromInt64(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
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
        return pool.ensureParameterizedTypeConstant(
                getInceptionClassConstant().getType(), pool.typeInt());
        }

    @Override
    public DelegateHandle createBitViewDelegate(DelegateHandle hSource, Mutability mutability)
        {
        if (hSource instanceof SliceHandle)
            {
            // ints.slice().asBitArray() -> ints.asBitArray().slice()
            SliceHandle hSlice = (SliceHandle) hSource;
            ViewHandle  hView  = new ViewHandle(getCanonicalClass(),
                                    (LongArrayHandle) hSlice.f_hSource, mutability);
            return slice(hView, hSlice.f_ofStart*64, hSlice.m_cSize*64, false);
            }
        return new ViewHandle(getCanonicalClass(), (LongArrayHandle) hSource, mutability);
        }


    // ----- RTDelegate API ------------------------------------------------------------------------

    @Override
    protected DelegateHandle createCopyImpl(DelegateHandle hTarget, Mutability mutability,
                                            long ofStart, long cSize, boolean fReverse)
        {
        ViewHandle hView = (ViewHandle) hTarget;

        byte[] abBits = getBits(hView, ofStart, cSize, fReverse);

        return xRTBitDelegate.INSTANCE.makeHandle(abBits, cSize, mutability);
        }

    @Override
    protected int extractArrayValueImpl(Frame frame, DelegateHandle hTarget, long lIndex, int iReturn)
        {
        ViewHandle     hView   = (ViewHandle) hTarget;
        LongArrayHandle hSource = hView.f_hSource;

        return frame.assignValue(iReturn, xBit.makeHandle(
                LongBasedDelegate.getBit(hSource.m_alValue, (int) lIndex)));
        }

    @Override
    public int assignArrayValueImpl(Frame frame, DelegateHandle hTarget, long lIndex,
                                    ObjectHandle hValue)
        {
        ViewHandle     hView   = (ViewHandle) hTarget;
        LongArrayHandle hSource = hView.f_hSource;

        LongBasedDelegate.setBit(hSource.m_alValue, (int) lIndex, ((JavaLong) hValue).getValue() != 0);
        return Op.R_NEXT;
        }


    // ----- BitView implementation ----------------------------------------------------------------

    @Override
    public byte[] getBits(DelegateHandle hDelegate, long ofStart, long cBits, boolean fReverse)
        {
        ViewHandle     hView  = (ViewHandle) hDelegate;
        LongArrayHandle hLongs = hView.f_hSource;

        byte[] abBits = LongBasedDelegate.extractBits(hLongs.m_alValue, ofStart, cBits);
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

        return LongBasedDelegate.getBit(hView.f_hSource.m_alValue, of);
        }

    @Override
    public void assignBit(DelegateHandle hDelegate, long of, boolean fBit)
        {
        ViewHandle hView = (ViewHandle) hDelegate;

        LongBasedDelegate.setBit(hView.f_hSource.m_alValue, of, fBit);
        }


    // ----- ByteView implementation ---------------------------------------------------------------

    @Override
    public byte[] getBytes(DelegateHandle hDelegate, long ofStart, long cBytes, boolean fReverse)
        {
        return getBits(hDelegate, ofStart*8, cBytes*8, fReverse);
        }

    @Override
    public byte extractByte(DelegateHandle hDelegate, long of)
        {
        ViewHandle hView = (ViewHandle) hDelegate;

        return LongBasedDelegate.getByte(hView.f_hSource.m_alValue, of);
        }

    @Override
    public void assignByte(DelegateHandle hDelegate, long of, byte bValue)
        {
        ViewHandle hView = (ViewHandle) hDelegate;

        LongBasedDelegate.setByte(hView.f_hSource.m_alValue, of, bValue);
        }


    // ----- handle --------------------------------------------------------------------------------

    /**
     * DelegateArray<Bit> view delegate.
     */
    protected static class ViewHandle
            extends DelegateHandle
        {
        public final LongArrayHandle f_hSource;

        protected ViewHandle(TypeComposition clazz, LongArrayHandle hSource, Mutability mutability)
            {
            super(clazz, mutability);

            f_hSource = hSource;
            m_cSize   = hSource.m_cSize*64;
            }
        }
    }