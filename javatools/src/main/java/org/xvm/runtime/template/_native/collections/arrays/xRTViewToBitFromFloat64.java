package org.xvm.runtime.template._native.collections.arrays;


import org.xvm.asm.ClassStructure;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.Op;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.ClassComposition;
import org.xvm.runtime.Container;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.JavaLong;
import org.xvm.runtime.TypeComposition;

import org.xvm.runtime.template.collections.xArray.Mutability;

import org.xvm.runtime.template.numbers.xBit;

import org.xvm.runtime.template._native.collections.arrays.xRTFloat64Delegate.DoubleArrayHandle;
import org.xvm.runtime.template._native.collections.arrays.xRTSlicingDelegate.SliceHandle;

import org.xvm.util.Handy;

// "long" and "double" are both 64-bit; use corresponding helpers
import static org.xvm.runtime.template._native.collections.arrays.LongBasedDelegate.bitIndex;
import static org.xvm.runtime.template._native.collections.arrays.LongBasedDelegate.bitMask;


/**
 * A base class for native ArrayDelegate<Bit> views that point to delegates holding double arrays.
 */
public class xRTViewToBitFromFloat64
        extends xRTViewToBit
        implements BitView {
    public static xRTViewToBitFromFloat64 INSTANCE;

    public xRTViewToBitFromFloat64(Container container, ClassStructure structure, boolean fInstance) {
        super(container, structure, false);

        if (fInstance) {
            INSTANCE = this;
        }
    }

    @Override
    public TypeConstant getCanonicalType() {
        ConstantPool pool = pool();
        return pool.ensureParameterizedTypeConstant(
                getInceptionClassConstant().getType(), pool.typeFloat64());
    }

    @Override
    public DelegateHandle createBitViewDelegate(DelegateHandle hSource, Mutability mutability) {
        // REVIEW: we probably need some logic similar to ByteBasedBitView.java

        ClassComposition clzView = getCanonicalClass();
        if (hSource instanceof SliceHandle hSlice) {
            // doubles.slice().asBitArray() -> doubles.asBitArray().slice()
            DoubleArrayHandle hLong = (DoubleArrayHandle) hSlice.f_hSource;
            ViewHandle        hView   = new ViewHandle(clzView,
                                        hLong, hLong.m_cSize*64, mutability);
            return slice(hView, hSlice.f_ofStart*64, hSlice.m_cSize*64, false);
        }

        if (hSource instanceof xRTViewFromBit.ViewHandle hView) {
            return new ViewHandle(clzView, (DoubleArrayHandle) hView.f_hSource, hSource.m_cSize, mutability);
        }

        DoubleArrayHandle hLong = (DoubleArrayHandle) hSource;
        return new ViewHandle(clzView, hLong, hSource.m_cSize*64, mutability);
    }


    // ----- RTDelegate API ------------------------------------------------------------------------

    @Override
    protected DelegateHandle createCopyImpl(DelegateHandle hTarget, Mutability mutability,
                                            long ofStart, long cSize, boolean fReverse) {
        ViewHandle hView = (ViewHandle) hTarget;

        byte[] abBits = getBits(hView, ofStart, cSize, fReverse);

        return xRTBitDelegate.INSTANCE.makeHandle(abBits, cSize, mutability);
    }

    @Override
    protected int extractArrayValueImpl(Frame frame, DelegateHandle hTarget, long lIndex, int iReturn) {
        ViewHandle hView = (ViewHandle) hTarget;

        return frame.assignValue(iReturn, xBit.makeHandle(
                getBit(hView.f_hSource.m_adValue, lIndex)));
    }

    @Override
    public int assignArrayValueImpl(Frame frame, DelegateHandle hTarget, long lIndex,
                                    ObjectHandle hValue) {
        ViewHandle hView = (ViewHandle) hTarget;

        setBit(hView.f_hSource.m_adValue, lIndex, ((JavaLong) hValue).getValue() != 0);
        return Op.R_NEXT;
    }


    // ----- BitView implementation ----------------------------------------------------------------

    @Override
    public byte[] getBits(DelegateHandle hDelegate, long ofStart, long cBits, boolean fReverse) {
        ViewHandle hView = (ViewHandle) hDelegate;

        byte[] abBits = extractBits(hView.f_hSource.m_adValue, ofStart, cBits);
        if (fReverse) {
            abBits = BitBasedDelegate.reverseBits(abBits, cBits);
        }
        return abBits;
    }

    @Override
    public boolean extractBit(DelegateHandle hDelegate, long of) {
        ViewHandle hView = (ViewHandle) hDelegate;

        return getBit(hView.f_hSource.m_adValue, of);
    }

    @Override
    public void assignBit(DelegateHandle hDelegate, long of, boolean fBit) {
        ViewHandle hView = (ViewHandle) hDelegate;

        setBit(hView.f_hSource.m_adValue, of, fBit);
    }


    // ----- converting methods --------------------------------------------------------------------

    /**
     * Get a byte in the specified array of doubles.
     *
     * @param adValue  the double array
     * @param of       the byte index
     *
     * @return the byte value
     */
    protected static byte getByte(double[] adValue, long of) {
        int  ixVal  = (int) (of / 8);
        int  ofByte = 7 - (int) (of & 7);

        return (byte) (Double.doubleToRawLongBits(adValue[ixVal]) >>> (ofByte * 8) & 0xFF);
    }

    /**
     * Set a byte in the specified array of doubles.
     *
     * @param adValue  the double array
     * @param of       the byte index
     * @param bValue   the byte value
     */
    protected static void setByte(double[] adValue, long of, byte bValue) {
        int  ixVal  = (int) (of / 8);
        int  ofByte = 7 - (int) (of & 7);
        long lMask  = 0xFFL << (ofByte * 8);

        adValue[ixVal] =
            Double.doubleToRawLongBits(adValue[ixVal]) & ~lMask | (bValue & 0xFFL) << (ofByte * 8);
    }

    /**
     * Extract an array of bits from the specified array of doubles.
     *
     * @param adValue  the double array
     * @param ofStart  the starting bit index
     * @param cBits    the number of bits to extract
     *
     * @return the byte array for the bits
     */
    protected static byte[] extractBits(double[] adValue, long ofStart, long cBits) {
        int    cBytes = BitBasedDelegate.storage(cBits);
        byte[] abBits = new byte[cBytes];
        int    ofDest = 0;

        if (ofStart % 64 == 0) {
            int ixSource = (int) (ofStart / 64);
            int cVals    = (int) (cBits / 64);

            for (int i = 0; i < cVals; i++, ofDest += 8) {
                Handy.toByteArray(Double.doubleToRawLongBits(adValue[ixSource + i]), abBits, ofDest);
            }
            if (cBits % 64 == 0) {
                return abBits;
            }

            // fill the tail
            ofStart = ofStart + cVals*64L;
            cBits   = cBits % 64;
        }

        for (long i = 0; i < cBits; i++) {
            BitBasedDelegate.setBit(abBits, ofDest + i, getBit(adValue, ofStart + i));
        }
        return abBits;
    }

    /**
     * Get a bit in the specified array of doubles.
     *
     * @param adValue  the double array
     * @param lIndex   the bit index
     *
     * @return true iff the bit is set
     */
    protected static boolean getBit(double[] adValue, long lIndex) {
        return (Double.doubleToRawLongBits(adValue[bitIndex(lIndex)]) & bitMask(lIndex)) != 0;
    }

    /**
     * Set or clear a bit in the specified array of doubles.
     *
     * @param adValue  the double array
     * @param lIndex   the bit index
     * @param fSet     true if the bit is to be set; false for clear
     */
    protected static void setBit(double[] adValue, long lIndex, boolean fSet) {
        int  nIndex = bitIndex(lIndex);
        long lValue = Double.doubleToRawLongBits(adValue[nIndex]);
        if (fSet) {
            adValue[nIndex] = Double.longBitsToDouble(lValue | bitMask(lIndex));
        } else {
            adValue[nIndex] = Double.longBitsToDouble(lValue & ~bitMask(lIndex));
        }
    }


    // ----- ByteView implementation ---------------------------------------------------------------

    @Override
    public byte[] getBytes(DelegateHandle hDelegate, long ofStart, long cBytes, boolean fReverse) {
        return getBits(hDelegate, ofStart*8, cBytes*8, fReverse);
    }

    @Override
    public byte extractByte(DelegateHandle hDelegate, long of) {
        ViewHandle hView = (ViewHandle) hDelegate;

        return getByte(hView.f_hSource.m_adValue, of);
    }

    @Override
    public void assignByte(DelegateHandle hDelegate, long of, byte bValue) {
        ViewHandle hView = (ViewHandle) hDelegate;

        setByte(hView.f_hSource.m_adValue, of, bValue);
    }



    // ----- handle --------------------------------------------------------------------------------

    /**
     * DelegateArray<Bit> view delegate.
     */
    protected static class ViewHandle
            extends xRTView.ViewHandle {
        protected final DoubleArrayHandle f_hSource;

        protected ViewHandle(TypeComposition clazz, DoubleArrayHandle hSource, long cSize,
                             Mutability mutability) {
            super(clazz, mutability);

            f_hSource = hSource;
            m_cSize   = cSize;
        }

        @Override
        public DelegateHandle getSource() {
            return f_hSource;
        }
    }
}