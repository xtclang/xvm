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

import org.xvm.runtime.template._native.collections.arrays.xRTInt64Delegate.IntArrayHandle;
import org.xvm.runtime.template._native.collections.arrays.xRTSlicingDelegate.SliceHandle;

import org.xvm.util.Handy;


/**
 * The native RTViewToBit<Int> implementation.
 */
public class xRTViewToBitFromInt
        extends xRTViewToBit
        implements BitView
    {
    public static xRTViewToBitFromInt INSTANCE;

    public xRTViewToBitFromInt(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
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
                                    (IntArrayHandle) hSlice.f_hSource, mutability);
            return slice(hView, hSlice.f_ofStart*64, hSlice.m_cSize*64, false);
            }
        return new ViewHandle(getCanonicalClass(), (IntArrayHandle) hSource, mutability);
        }


    // ----- RTDelegate API ------------------------------------------------------------------------

    @Override
    protected DelegateHandle createCopyImpl(DelegateHandle hTarget, Mutability mutability,
                                            long ofStart, long cSize, boolean fReverse)
        {
        ViewHandle     hView   = (ViewHandle) hTarget;
        IntArrayHandle hSource = hView.f_hSource;
        int            cVals   = (int) (cSize / 64);
        long[]         alVal   = hSource.m_alValue;
        byte[]         abBit   = new byte[cVals * 8];

        for (int iL = 0, iB = 0; iL < cVals; iL++)
            {
            long l = alVal[iL];

            abBit[iB++] = (byte) (l >> 56);
            abBit[iB++] = (byte) (l >> 48);
            abBit[iB++] = (byte) (l >> 40);
            abBit[iB++] = (byte) (l >> 32);
            abBit[iB++] = (byte) (l >> 24);
            abBit[iB++] = (byte) (l >> 16);
            abBit[iB++] = (byte) (l >> 8 );
            abBit[iB++] = (byte) l;
            }

        if (fReverse)
            {
            abBit = BitBasedDelegate.reverseBits(abBit, cVals);
            }

        return xRTBitDelegate.INSTANCE.makeHandle(abBit, cSize, mutability);
        }

    @Override
    protected int extractArrayValueImpl(Frame frame, DelegateHandle hTarget, long lIndex, int iReturn)
        {
        ViewHandle     hView   = (ViewHandle) hTarget;
        IntArrayHandle hSource = hView.f_hSource;

        return frame.assignValue(iReturn, xBit.makeHandle(
                getBit(hSource.m_alValue, (int) lIndex)));
        }

    @Override
    public int assignArrayValueImpl(Frame frame, DelegateHandle hTarget, long lIndex,
                                    ObjectHandle hValue)
        {
        ViewHandle     hView   = (ViewHandle) hTarget;
        IntArrayHandle hSource = hView.f_hSource;

        setBit(hSource.m_alValue, (int) lIndex, ((JavaLong) hValue).getValue() != 0);
        return Op.R_NEXT;
        }


    // ----- BitView implementation ----------------------------------------------------------------

    @Override
    public byte[] getBits(DelegateHandle hDelegate, long ofStart, long cBits, boolean fReverse)
        {
        ViewHandle hView   = (ViewHandle) hDelegate;
        long[]     alValue = hView.f_hSource.m_alValue;

        // TODO: relax the limitation
        assert ofStart % 64 == 0 && cBits % 64 == 0;

        int    cBytes = BitBasedDelegate.storage(cBits);
        byte[] abBits = new byte[cBytes];

        int ixSource = (int) (ofStart / 64);
        int cVals    = (int) (cBits / 64);

        for (int i = 0, of = 0; i < cVals; i++, of += 8)
            {
            Handy.toByteArray(alValue[ixSource + i], abBits, of);
            }

        return abBits;
        }

    @Override
    public boolean extractBit(DelegateHandle hDelegate, long of)
        {
        ViewHandle hView = (ViewHandle) hDelegate;

        return getBit(hView.f_hSource.m_alValue, of);
        }

    @Override
    public void assignBit(DelegateHandle hDelegate, long of, boolean fBit)
        {
        ViewHandle hView = (ViewHandle) hDelegate;

        setBit(hView.f_hSource.m_alValue, of, fBit);
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
        ViewHandle hView   = (ViewHandle) hDelegate;
        long[]     alValue = hView.f_hSource.m_alValue;

        int  ixVal  = (int) (of / 8);
        int  ofByte = 7 - (int) (of & 7);
        long lVal   = alValue[ixVal];

        return (byte) (lVal >>> (ofByte * 8) & 0xFF);
        }

    @Override
    public void assignByte(DelegateHandle hDelegate, long of, byte bValue)
        {
        ViewHandle hView   = (ViewHandle) hDelegate;
        long[]     alValue = hView.f_hSource.m_alValue;

        int  ixVal  = (int) (of / 8);
        int  ofByte = 7 - (int) (of & 7);
        long lVal   = alValue[ixVal];
        long lMask  = 0xFFL << (ofByte * 8);

        alValue[ixVal] = lVal & ~lMask | ((long) bValue & 0xFFL) << (ofByte * 8);
        }


    // ----- helpers -------------------------------------------------------------------------------

    /**
     * Get a bit in the specified array of longs.
     *
     * @param alValue  the long array
     * @param lIndex   the bit index
     *
     * @return true iff the bit is set
     */
    private static boolean getBit(long[] alValue, long lIndex)
        {
        return (alValue[index(lIndex)] & bitMask(lIndex)) != 0;
        }

    /**
     * Set or clear a bit in the specified array of longs.
     *
     * @param alValue  the long array
     * @param lIndex   the bit index
     * @param fSet     true if the bit is to be set; false for clear
     */
    private static void setBit(long[] alValue, long lIndex, boolean fSet)
        {
        if (fSet)
            {
            alValue[index(lIndex)] |= bitMask(lIndex);
            }
        else
            {
            alValue[index(lIndex)] &= ~bitMask(lIndex);
            }
        }

    /**
     * Calculate an index of the specified bit in the byte array.
     *
     * @param lBit  the bit index
     *
     * @return the byte index
     */
    private static int index(long lBit)
        {
        return (int) (lBit / 64);
        }

    /**
     * Calculate a mask of the specified bit in the byte array at {@ling #index}.
     *
     * @param lBit  the bit index
     *
     * @return the mask
     */
    private static long bitMask(long lBit)
        {
        return 0x8000_0000_0000_0000L >>> (lBit & 0x3F);
        }


    // ----- handle --------------------------------------------------------------------------------

    /**
     * DelegateArray<Bit> view delegate.
     */
    protected static class ViewHandle
            extends DelegateHandle
        {
        public final IntArrayHandle f_hSource;

        protected ViewHandle(TypeComposition clazz, IntArrayHandle hSource, Mutability mutability)
            {
            super(clazz, mutability);

            f_hSource = hSource;
            m_cSize   = hSource.m_cSize*64;
            }
        }
    }