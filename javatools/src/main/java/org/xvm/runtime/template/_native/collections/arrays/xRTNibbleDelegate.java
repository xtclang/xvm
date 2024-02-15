package org.xvm.runtime.template._native.collections.arrays;


import org.xvm.asm.ClassStructure;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;

import org.xvm.asm.constants.PropertyConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.Container;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.GenericHandle;
import org.xvm.runtime.TypeComposition;

import org.xvm.runtime.template.collections.xArray.ArrayHandle;
import org.xvm.runtime.template.collections.xArray.Mutability;

import org.xvm.runtime.template.numbers.xInt64;

import org.xvm.runtime.template._native.collections.arrays.BitBasedDelegate.BitArrayHandle;


/**
 * Native RTDelegate<Nibble> implementation.
 */
public class xRTNibbleDelegate
        extends ByteBasedDelegate
        implements ByteView
    {
    public static xRTNibbleDelegate INSTANCE;

    public xRTNibbleDelegate(Container container, ClassStructure structure, boolean fInstance)
        {
        super(container, structure, (byte) 0, (byte) 0xF);

        if (fInstance)
            {
            INSTANCE = this;
            }
        }

    @Override
    public void initNative()
        {
        ConstantPool   pool         = pool();
        ClassStructure structNibble = (ClassStructure) pool.
                typeNibble().getSingleUnderlyingClass(false).getComponent();
        FN_OF_INT = structNibble.findMethod("of", 1, pool.typeInt64());
        PROP_BITS = (PropertyConstant) structNibble.getChild("bits").getIdentityConstant();
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
    public DelegateHandle createDelegate(Container container, TypeConstant typeElement, int cSize,
                                         ObjectHandle[] ahContent, Mutability mutability)
        {
        byte[] ab = new byte[storage(cSize)];

        for (int i = 0, c = ahContent.length; i < c; i++)
            {
            int nNibble = getValue((GenericHandle) ahContent[i]);

            // the very first call to Nibble.of(Int) initializes the "Nibble.values" array, which
            // allows as to cache every Nibble value
            if (NIBBLES[nNibble] == null)
                {
                NIBBLES[nNibble] = ahContent[i];
                }
            setNibble(ab, i, nNibble);
            }
        return new NibbleArrayHandle(getCanonicalClass(), ab, cSize, mutability);
        }

    @Override
    protected int extractArrayValueImpl(Frame frame, DelegateHandle hTarget, long lIndex, int iReturn)
        {
        NibbleArrayHandle hDelegate = (NibbleArrayHandle) hTarget;
        int               nNibble   = getNibble(hDelegate.m_abValue, lIndex);

        return assignNibble(frame, nNibble, iReturn);
        }

    @Override
    protected int assignArrayValueImpl(Frame frame, DelegateHandle hTarget, long lIndex,
                                       ObjectHandle hValue)
        {
        NibbleArrayHandle hDelegate = (NibbleArrayHandle) hTarget;

        long   cSize   = hDelegate.m_cSize;
        byte[] abValue = hDelegate.m_abValue;

        if (lIndex >= cSize)
            {
            if (index(lIndex) >= abValue.length)
                {
                abValue = hDelegate.m_abValue = grow(abValue, storage(lIndex + 1));
                }

            hDelegate.m_cSize = lIndex + 1;
            }

        setNibble(abValue, (int) lIndex, getValue((GenericHandle) hValue));
        return Op.R_NEXT;
        }

    @Override
    protected void insertElementImpl(DelegateHandle hTarget, ObjectHandle hElement, long lIndex)
        {
        NibbleArrayHandle hDelegate = (NibbleArrayHandle) hTarget;
        long              cSize     = hDelegate.m_cSize;
        byte[]            abValue   = hDelegate.m_abValue;

        if (storage(cSize + 1) > abValue.length)
            {
            abValue = hDelegate.m_abValue = grow(hDelegate.m_abValue, storage(cSize) + 1);
            }
        hDelegate.m_cSize++;

        if (lIndex < cSize)
            {
            for (long i = lIndex + 1; i < cSize; i++)
                {
                setNibble(abValue, i, getNibble(abValue, i-1));
                }
            }
        setNibble(abValue, lIndex, getValue((GenericHandle) hElement));
        }

    @Override
    protected void deleteElementImpl(DelegateHandle hTarget, long lIndex)
        {
        NibbleArrayHandle hDelegate = (NibbleArrayHandle) hTarget;
        long           cSize     = hDelegate.m_cSize;
        byte[]         abValue   = hDelegate.m_abValue;

        if (lIndex < cSize - 1)
            {
            for (long i = lIndex + 1; i < cSize; i++)
                {
                setNibble(abValue, i - 1, getNibble(abValue, i));
                }
            }

        setNibble(abValue, --hDelegate.m_cSize, 0);
        }

    @Override
    protected void deleteRangeImpl(DelegateHandle hTarget, long lIndex, long cDelete)
        {
        NibbleArrayHandle hDelegate = (NibbleArrayHandle) hTarget;
        long              cSize     = hDelegate.m_cSize;
        byte[]            abValue   = hDelegate.m_abValue;

        if (lIndex < cSize - cDelete)
            {
            for (long i = lIndex + cDelete; i < cSize; i++)
                {
                setNibble(abValue, i - cDelete, getNibble(abValue, i));
                }
            }

        for (long i = cSize - cDelete; i < cSize; i++)
            {
            setNibble(abValue, i, 0);
            }
        hDelegate.m_cSize -= cDelete;
        }

    @Override
    protected ObjectHandle makeElementHandle(long lValue)
        {
        // we should never get here
        throw new IllegalStateException();
        }


    // ----- helper methods ------------------------------------------------------------------------

    /**
     * Assign an ObjectHandle for the specified Nibble value.
     *
     * @param frame    the current frame
     * @param nNibble  the Nibble value
     * @param iReturn  the register id to place the result into
     *
     * @return Op.R_NEXT, Op.R_CALL or Op.R_EXCEPTION
     */
    public static int assignNibble(Frame frame, int nNibble, int iReturn)
        {
        ObjectHandle hNibble = NIBBLES[nNibble];
        if (hNibble == null)
            {
            ObjectHandle[] ahArg = new ObjectHandle[FN_OF_INT.getMaxVars()];
            ahArg[0] = xInt64.makeHandle(nNibble);

            switch (frame.call1(FN_OF_INT, null, ahArg, Op.A_STACK))
                {
                case Op.R_CALL:
                    Frame.Continuation stepNext = frameCaller ->
                        {
                        ObjectHandle h = frameCaller.popStack();
                        NIBBLES[nNibble] = h;
                        return frameCaller.assignValue(iReturn, h);
                        };
                    frame.m_frameNext.addContinuation(stepNext);
                    return Op.R_CALL;

                case Op.R_EXCEPTION:
                    return Op.R_EXCEPTION;

                default:
                    throw new IllegalStateException();
                }
            }
        return frame.assignValue(iReturn, hNibble);
        }

    /**
     * @return the four-bit value contained in the natural Nibble handle
     */
    public static int getValue(GenericHandle hNibble)
        {
        ArrayHandle    haBits    = (ArrayHandle) hNibble.getField(null, PROP_BITS);
        BitArrayHandle hDelegate = (BitArrayHandle) haBits.m_hDelegate;
        return (hDelegate.m_abValue[0] & 0xF0) >>> 4;
        }

    /**
     * Calculate the size of a byte array to represent a Nibble array.
     *
     * @param cNibbles  the Nibble array size
     *
     * @return the byte array size
     */
    private static int storage(long cNibbles)
        {
        return (int) ((cNibbles - 1) / 2 + 1);
        }

    /**
     * Calculate an index of the specified Nibble in the byte array.
     *
     * @param iNibble  the Nibble index
     *
     * @return the byte index
     */
    private static int index(long iNibble)
        {
        return (int) (iNibble / 2);
        }

    /**
     * Get a Nibble in the specified array of bytes.
     *
     * @param abValue  the byte array
     * @param iIndex   the Nibble index
     *
     * @return the Nibble value
     */
    public static int getNibble(byte[] abValue, long iIndex)
        {
        return ((byte) (abValue[index(iIndex)] >>> (4 * (1 - iIndex % 2)))) & 0xF;
        }

    /**
     * Set or clear a Nibble in the specified array of bytes.
     *
     * @param abValue  the byte array
     * @param iIndex   the Nibble index
     * @param bNibble  the Nibble value
     */
    public static void setNibble(byte[] abValue, long iIndex, int bNibble)
        {
        int ix = index(iIndex);

        bNibble &= 0xF;
        if (iIndex % 2 == 0)
            {
            abValue[ix] = (byte) ((abValue[ix] & 0x0F) | (byte) (bNibble << 4));
            }
        else
            {
            abValue[ix] = (byte) ((abValue[ix] & 0xF0) | (byte) bNibble);
            }
        }

    @Override
    public NibbleArrayHandle makeHandle(byte[] abValue, long cNibbles, Mutability mutability)
        {
        return new NibbleArrayHandle(getCanonicalClass(), abValue, cNibbles, mutability);
        }

    /**
     * The handle for Nibble array delegate.
     */
    public static class NibbleArrayHandle
            extends ByteArrayHandle
        {
        public NibbleArrayHandle(TypeComposition clazz, byte[] abValue,
                                 long cSize, Mutability mutability)
            {
            super(clazz, abValue, cSize, mutability);
            }

        @Override
        public long getBitCount()
            {
            return m_cSize*4;
            }

        @Override
        protected void purgeUnusedSpace()
            {
            byte[] ab = m_abValue;
            int    c  = storage(m_cSize);
            if (ab.length != c)
                {
                byte[] abNew = new byte[c];
                System.arraycopy(ab, 0, abNew, 0, c);
                m_abValue = abNew;
                }
            }

        @Override
        public int compareTo(ObjectHandle that)
            {
            byte[] abThis = m_abValue;
            long   cThis  = m_cSize;
            byte[] abThat = ((NibbleArrayHandle) that).m_abValue;
            long   cThat  = ((NibbleArrayHandle) that).m_cSize;

            if (cThis != cThat)
                {
                return (int) (cThis - cThat);
                }

            for (int i = 0, c = storage(cThis); i < c; i++)
                {
                int iDiff = abThis[i] - abThat[i];
                if (iDiff != 0)
                    {
                    return iDiff < 0 ? -1 : 1;
                    }
                }
            return 0;
            }
        }


    // ----- constants -----------------------------------------------------------------------------

    private static MethodStructure FN_OF_INT;
    private static PropertyConstant PROP_BITS;

    /**
     * Caches Nibble handles.
     */
    private static final ObjectHandle[] NIBBLES = new ObjectHandle[16];
    }