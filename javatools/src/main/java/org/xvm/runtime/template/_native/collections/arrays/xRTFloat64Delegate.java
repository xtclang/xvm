package org.xvm.runtime.template._native.collections.arrays;

import java.util.Arrays;
import org.xvm.asm.ClassStructure;
import org.xvm.asm.ConstantPool;

import org.xvm.asm.Op;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.Container;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.TypeComposition;

import org.xvm.runtime.template.xBoolean;
import org.xvm.runtime.template.xException;

import org.xvm.runtime.template.collections.xArray;

import org.xvm.runtime.template.numbers.BaseBinaryFP.FloatHandle;
import org.xvm.runtime.template.numbers.xFloat64;
import org.xvm.runtime.template.numbers.xInt64;


/**
 * Native RTDelegate<Float64> implementation.
 */
public class xRTFloat64Delegate
        extends xRTDelegate {
    public static xRTFloat64Delegate INSTANCE;

    public xRTFloat64Delegate(Container container, ClassStructure structure, boolean fInstance) {
        super(container, structure, false);

        if (fInstance) {
            INSTANCE = this;
        }
    }

    @Override
    public void initNative() {
    }

    @Override
    public TypeConstant getCanonicalType() {
        ConstantPool pool = pool();
        return pool.ensureParameterizedTypeConstant(
                getInceptionClassConstant().getType(),
                pool.typeFloat64());
    }

    @Override
    public DelegateHandle createDelegate(Container container, TypeConstant typeElement, int cCapacity,
                                         ObjectHandle[] ahContent, xArray.Mutability mutability) {
        double[] adValue = new double[cCapacity];
        int      cSize   = ahContent.length;

        for (int i = 0; i < cSize; i++) {
            adValue[i] = ((FloatHandle) ahContent[i]).getValue();
        }
        return makeHandle(adValue, cSize, mutability);
    }


    // ----- RTDelegate API ------------------------------------------------------------------------

    @Override
    public DelegateHandle fill(DelegateHandle hTarget, int cSize, ObjectHandle hValue) {
        DoubleArrayHandle hDelegate = (DoubleArrayHandle) hTarget;

        Arrays.fill(hDelegate.m_adValue, 0, cSize, ((FloatHandle) hValue).getValue());
        hDelegate.m_cSize = cSize;
        return hDelegate;
    }

    @Override
    public int getPropertyCapacity(Frame frame, ObjectHandle hTarget, int iReturn) {
        DoubleArrayHandle hDelegate = (DoubleArrayHandle) hTarget;

        return frame.assignValue(iReturn, xInt64.makeHandle(hDelegate.m_adValue.length));
    }

    @Override
    public int setPropertyCapacity(Frame frame, ObjectHandle hTarget, long nCapacity) {
        DoubleArrayHandle hDelegate = (DoubleArrayHandle) hTarget;

        double[] adOld = hDelegate.m_adValue;
        long     cSize = hDelegate.m_cSize;

        if (nCapacity < cSize) {
            return frame.raiseException(
                xException.illegalArgument(frame, "Capacity cannot be less then size"));
        }

        // for now, no trimming
        int nNew = (int) nCapacity;
        int nOld = adOld.length;
        if (nNew > nOld) {
            double[] adNew = new double[nNew];
            System.arraycopy(adOld, 0, adNew, 0, adOld.length);
            hDelegate.m_adValue = adNew;
        }
        return Op.R_NEXT;
    }

    @Override
    protected DelegateHandle createCopyImpl(DelegateHandle hTarget, xArray.Mutability mutability,
                                            long ofStart, long cSize, boolean fReverse) {
        DoubleArrayHandle hDelegate = (DoubleArrayHandle) hTarget;

        double[] alValue;
        if (ofStart == 0) {
            alValue = Arrays.copyOfRange(hDelegate.m_adValue, (int) ofStart, (int) (ofStart + cSize));
        } else {
            throw new UnsupportedOperationException("TODO"); // copy one by one
        }

        if (fReverse) {
            alValue = reverse(alValue, (int) cSize);
        }
        return new DoubleArrayHandle(hDelegate.getComposition(), alValue, cSize, mutability);
    }

    @Override
    protected int extractArrayValueImpl(Frame frame, DelegateHandle hTarget, long lIndex, int iReturn) {
        DoubleArrayHandle hDelegate = (DoubleArrayHandle) hTarget;

        return frame.assignValue(iReturn,
                xFloat64.INSTANCE.makeHandle(hDelegate.m_adValue[(int) lIndex]));
    }

    @Override
    protected int assignArrayValueImpl(Frame frame, DelegateHandle hTarget, long lIndex,
                                       ObjectHandle hValue) {
        DoubleArrayHandle hDelegate = (DoubleArrayHandle) hTarget;

        int      cSize   = (int) hDelegate.m_cSize;
        double[] adValue = hDelegate.m_adValue;
        int      nIndex  = (int) lIndex;

        if (nIndex >= cSize) {
            if (nIndex >= adValue.length) {
                adValue = hDelegate.m_adValue = grow(adValue, nIndex + 1);
            }

            hDelegate.m_cSize = nIndex + 1;
        }

        adValue[nIndex] = ((FloatHandle) hValue).getValue();
        return Op.R_NEXT;
    }

    @Override
    protected void insertElementImpl(DelegateHandle hTarget, ObjectHandle hElement, long lIndex) {
        DoubleArrayHandle hDelegate = (DoubleArrayHandle) hTarget;
        int               cSize     = (int) hDelegate.m_cSize;
        double[]          adValue   = hDelegate.m_adValue;

        if (cSize == adValue.length) {
            adValue = hDelegate.m_adValue = grow(hDelegate.m_adValue, cSize + 1);
        }
        hDelegate.m_cSize++;

        if (lIndex == cSize) {
            adValue[cSize] = ((FloatHandle) hElement).getValue();
        } else {
            // insert
            int nIndex = (int) lIndex;
            System.arraycopy(adValue, nIndex, adValue, nIndex + 1, cSize - nIndex);
            adValue[nIndex] = ((FloatHandle) hElement).getValue();
        }
    }

    @Override
    protected void deleteElementImpl(DelegateHandle hTarget, long lIndex) {
        DoubleArrayHandle hDelegate = (DoubleArrayHandle) hTarget;
        int               cSize     = (int) hDelegate.m_cSize;
        double[]          adValue   = hDelegate.m_adValue;

        if (lIndex < cSize - 1) {
            int nIndex = (int) lIndex;
            System.arraycopy(adValue, nIndex + 1, adValue, nIndex, cSize - nIndex - 1);
        }

        adValue[(int) --hDelegate.m_cSize] = 0;
    }

    @Override
    protected void deleteRangeImpl(DelegateHandle hTarget, long lIndex, long cDelete) {
        DoubleArrayHandle hDelegate = (DoubleArrayHandle) hTarget;
        int               cSize     = (int) hDelegate.m_cSize;
        double[]          adValue   = hDelegate.m_adValue;
        int               nIndex    = (int) lIndex;
        int               nDelete   = (int) cDelete;

        if (nIndex < cSize - nDelete) {
            System.arraycopy(adValue, nIndex + nDelete, adValue, nIndex, cSize - nIndex - nDelete);
        }
        hDelegate.m_cSize -= cDelete;
    }


    // ----- ClassTemplate API ---------------------------------------------------------------------

    @Override
    public int callEquals(Frame frame, TypeComposition clazz,
                          ObjectHandle hValue1, ObjectHandle hValue2, int iReturn) {
        DoubleArrayHandle h1 = (DoubleArrayHandle) hValue1;
        DoubleArrayHandle h2 = (DoubleArrayHandle) hValue2;

        if (h1 == h2) {
            return frame.assignValue(iReturn, xBoolean.TRUE);
        }
        if (h1.m_cSize != h2.m_cSize) {
            return frame.assignValue(iReturn, xBoolean.FALSE);
        }

        // this is slightly incorrect; it assumes that we always trim the tail
        int cStore = (int) h1.m_cSize;
        return frame.assignValue(iReturn, xBoolean.makeHandle(
                Arrays.equals(h1.m_adValue, 0, cStore, h2.m_adValue, 0, cStore)));
    }

    @Override
    public boolean compareIdentity(ObjectHandle hValue1, ObjectHandle hValue2) {
        DoubleArrayHandle h1 = (DoubleArrayHandle) hValue1;
        DoubleArrayHandle h2 = (DoubleArrayHandle) hValue2;

        if (h1 == h2) {
            return true;
        }

        return h1.getMutability() == h2.getMutability()
            && h1.m_cSize == h2.m_cSize
            && Arrays.equals(h1.m_adValue, h2.m_adValue);
    }


    // ----- helper methods ------------------------------------------------------------------------

    /**
     * Reverse the array of longs represented by the specified array.
     *
     * @param adValue  the double array
     * @param cSize    the actual number of values held by the array
     */
    public double[] reverse(double[] adValue, int cSize) {
        double[] adValueR = new double[cSize];
        for (int i = 0; i < cSize; i++) {
            adValueR[cSize - 1 - i] = adValue[i];
        }
        return adValueR;
    }

    public static double[] grow(double[] adValue, int cNew) {
        int cCapacity = calculateCapacity(adValue.length, cNew);

        double[] alNew = new double[cCapacity];
        System.arraycopy(adValue, 0, alNew, 0, adValue.length);
        return alNew;
    }


    // ----- handle --------------------------------------------------------------------------------

    public DelegateHandle makeHandle(double[] adValue, long cSize, xArray.Mutability mutability) {
        return new DoubleArrayHandle(getCanonicalClass(), adValue, cSize, mutability);
    }

    /**
     * Array delegate handle based on a java long array.
     */
    public static class DoubleArrayHandle
            extends DelegateHandle {
        protected double[] m_adValue;

        protected DoubleArrayHandle(TypeComposition clazz, double[] adValue, long cValues,
                                    xArray.Mutability mutability) {
            super(clazz, mutability);

            m_adValue = adValue;
            m_cSize   = cValues;
        }

        @Override
        public boolean makeImmutable() {
            if (isMutable()) {
                purgeUnusedSpace();
            }
            return super.makeImmutable();
        }

        protected void purgeUnusedSpace() {
            double[] ad = m_adValue;
            int      c  = ad.length;
            if (ad.length != c) {
                double[] adNew = new double[c];
                System.arraycopy(ad, 0, adNew, 0, c);
                m_adValue = adNew;
            }
        }

        @Override
        public int compareTo(ObjectHandle that) {
            double[] adThis = m_adValue;
            long     cThis  = m_cSize;
            double[] adThat = ((DoubleArrayHandle) that).m_adValue;
            long     cThat  = ((DoubleArrayHandle) that).m_cSize;

            if (cThis != cThat) {
                return (int) (cThis - cThat);
            }

            for (int i = 0; i < cThis; i++) {
                double dDiff = adThis[i] - adThat[i];
                if (dDiff != 0) {
                    return dDiff < 0 ? -1 : 1;
                }
            }
            return 0;
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(m_adValue);
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof DoubleArrayHandle that
                && Arrays.equals(this.m_adValue, that.m_adValue);
        }
    }
   }