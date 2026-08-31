package org.xvm.runtime.template._native.collections.arrays;


import java.util.Arrays;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Constant;
import org.xvm.asm.Op;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.ClassTemplate;
import org.xvm.runtime.Container;
import org.xvm.runtime.Frame;
import org.xvm.runtime.NativeTemplate;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.TypeComposition;

import org.xvm.runtime.template.xBoolean;
import org.xvm.runtime.template.xException;

import org.xvm.runtime.template.collections.xArray.Mutability;

import org.xvm.runtime.template.numbers.xInt64;

import static org.xvm.util.Handy.copyOf;


/**
 * The delegate for an array whose elements are stored as {@code ObjectHandle}s - that is, for every
 * element type with no more specific storage.
 *
 * <p>This separates the two roles {@link xRTDelegate} was serving. It was both the base declaring
 * the storage protocol and the implementation of that protocol for object arrays, so a delegate
 * that failed to implement part of the protocol silently inherited the object-array version. That
 * was not hypothetical: {@code xRTStringDelegate} had no {@code deleteRangeImpl}, inherited this
 * one, and deleting a range from a {@code String[]} failed with
 * {@code ClassCastException: StringArrayHandle cannot be cast to GenericArrayDelegate}.</p>
 *
 * <p>With the object-array implementation here, the base declares the protocol without also
 * answering it, so a missing implementation becomes a question the compiler asks. The binding to
 * the Ecstasy class is declared rather than taken from this file's name, which is what lets the
 * concrete class be named for what it is.</p>
 */
@NativeTemplate("_native.collections.arrays.RTDelegate")
public class xRTGenericDelegate
        extends xRTDelegate<xRTDelegate.GenericArrayDelegate> {
    public xRTGenericDelegate(Container container, ClassStructure structure) {
        super(container, structure);
    }

    @Override
    protected DelegateHandle createCopyImpl(GenericArrayDelegate hDelegate, Mutability mutability,
                                            long ofStart, long cSize, boolean fReverse) {

        if (ofStart == 0 && cSize == hDelegate.m_cSize && cSize == hDelegate.m_ahValue.length
                && mutability == hDelegate.getMutability() && mutability == Mutability.Constant
                && !fReverse) {
            // there is absolutely no reason to create a copy
            return hDelegate;
        }

        ObjectHandle[] ahValue = Arrays.copyOfRange(hDelegate.m_ahValue,
                                    (int) ofStart, (int) (ofStart + cSize));
        if (fReverse) {
            ahValue = reverse(ahValue, (int) cSize);
        }
        return new GenericArrayDelegate(hDelegate.getComposition(), ahValue, mutability);
    }

    @Override
    protected int extractArrayValueImpl(Frame frame, GenericArrayDelegate hDelegate, long lIndex, int iReturn) {
        return frame.assignValue(iReturn, hDelegate.m_ahValue[(int) lIndex]);
    }

    @Override
    protected int assignArrayValueImpl(Frame frame, GenericArrayDelegate hDelegate, long lIndex,
                                       ObjectHandle hValue) {
        ObjectHandle[]       ahValue   = hDelegate.m_ahValue;
        int                  cSize     = (int) hDelegate.m_cSize;
        int                  nIndex    = (int) lIndex;

        if (nIndex == cSize) {
            if (cSize == ahValue.length) {
                ahValue = hDelegate.m_ahValue = grow(ahValue, cSize + 1);
            }
            hDelegate.m_cSize++;
        } else if (nIndex > cSize) {
            TypeConstant typeElement  = hDelegate.getType().getParamType(0);
            Constant     constDefault = typeElement.getDefaultValue();

            if (constDefault == null) {
                return frame.raiseException(xException.unsupported(
                        frame, "No default value for " + typeElement.getValueString()));
            }

            if (nIndex >= ahValue.length) {
                hDelegate.m_ahValue = ahValue = grow(ahValue, nIndex + 1);
            }
            hDelegate.m_cSize = nIndex + 1;

            ObjectHandle hDefault = frame.getConstHandle(constDefault);
            if (Op.isDeferred(hDefault)) {
                ObjectHandle[] ahVal = ahValue;
                return hDefault.proceed(frame, frameCaller -> {
                    Arrays.fill(ahVal, cSize, nIndex, frameCaller.popStack());
                    ahVal[nIndex] = hValue;
                    return Op.R_NEXT;
                });
            }
            Arrays.fill(ahValue, cSize, nIndex, hDefault);
        }

        ahValue[nIndex] = hValue;
        return Op.R_NEXT;
    }

    @Override
    protected void deleteRangeImpl(GenericArrayDelegate hDelegate, long lIndex, long cDelete) {
        int                  cSize     = (int) hDelegate.m_cSize;
        ObjectHandle[]       ahValue   = hDelegate.m_ahValue;
        int                  nIndex    = (int) lIndex;
        int                  nDelete   = (int) cDelete;

        if (nIndex < cSize - nDelete) {
            System.arraycopy(ahValue, nIndex + nDelete, ahValue, nIndex, cSize - nIndex - nDelete);
        }
        Arrays.fill(ahValue, cSize - nDelete, cSize, null);
        hDelegate.m_cSize -= cDelete;
    }

    @Override
    public DelegateHandle createDelegate(Container container, TypeConstant typeElement, int cCapacity,
                                         ObjectHandle[] ahContent, Mutability mutability) {
        TypeComposition clzDelegate = ensureParameterizedClass(container, typeElement);

        int            cSize = ahContent.length;
        ObjectHandle[] ahValue;
        if (cCapacity > cSize) {
            ahValue = new ObjectHandle[cCapacity];
            if (cSize > 0) {
                System.arraycopy(ahContent, 0, ahValue, 0, cSize);
            }
        } else {
            ahValue = mutability == Mutability.Constant
                ? ahContent
                : copyOf(ahContent);
        }
        return new GenericArrayDelegate(clzDelegate, ahValue, cSize, mutability);
    }

    @Override
    public int callEquals(Frame frame, TypeComposition clazz,
                          ObjectHandle hValue1, ObjectHandle hValue2, int iReturn) {
        GenericArrayDelegate h1 = (GenericArrayDelegate) hValue1;
        GenericArrayDelegate h2 = (GenericArrayDelegate) hValue2;

        ObjectHandle[] ah1 = h1.m_ahValue;
        ObjectHandle[] ah2 = h2.m_ahValue;

        // compare the array dimensions
        int cElements = ah1.length;
        if (cElements != ah2.length) {
            return frame.assignValue(iReturn, xBoolean.falseHandle(frame));
        }

        // use the compile-time element type
        // and compare arrays elements one-by-one
        TypeConstant typeEl = clazz.getType().getParamType(0);

        int[] holder = new int[] {0}; // the index holder
        return new Equals(ah1, ah2, typeEl, cElements, holder, iReturn).doNext(frame);
    }

    @Override
    public boolean compareIdentity(ObjectHandle hValue1, ObjectHandle hValue2) {
        if (!(hValue1 instanceof GenericArrayDelegate h1) || !(hValue2 instanceof GenericArrayDelegate h2)) {
            return false;
        }

        if (h1 == h2) {
            return true;
        }

        if (h1.getMutability() != h2.getMutability() || h1.m_cSize != h2.m_cSize) {
            return false;
        }

        ObjectHandle[] ah1 = h1.m_ahValue;
        ObjectHandle[] ah2 = h2.m_ahValue;

        if (ah1 == ah2) {
            return true;
        }

        for (int i = 0, c = (int) h1.m_cSize; i < c; i++) {
            ObjectHandle hV1 = ah1[i];
            ObjectHandle hV2 = ah2[i];

            ClassTemplate template = hV1.getTemplate();
            if (template != hV2.getTemplate() || !template.compareIdentity(hV1, hV2)) {
                return false;
            }
        }
        return true;
    }

    @Override
    protected int getPropertyCapacity(Frame frame, GenericArrayDelegate hDelegate, int iReturn) {

        return frame.assignValue(iReturn, xInt64.makeHandle(frame, hDelegate.m_ahValue.length));
    }

    @Override
    protected int setPropertyCapacity(Frame frame, GenericArrayDelegate hDelegate, long nCapacity) {

        ObjectHandle[] ahOld = hDelegate.m_ahValue;
        int            nSize = (int) hDelegate.m_cSize;

        if (nCapacity < nSize) {
            return frame.raiseException(
                xException.illegalArgument(frame, "Capacity cannot be less then size"));
        }

        // for now, no trimming
        int nCapacityOld = ahOld.length;
        if (nCapacity > nCapacityOld) {
            ObjectHandle[] ahNew = new ObjectHandle[(int) nCapacity];
            System.arraycopy(ahOld, 0, ahNew, 0, ahOld.length);
            hDelegate.m_ahValue = ahNew;
        }
        return Op.R_NEXT;
    }

    @Override
    public DelegateHandle fill(GenericArrayDelegate hDelegate, int cSize, ObjectHandle hValue) {

        Arrays.fill(hDelegate.m_ahValue, 0, cSize, hValue);
        hDelegate.m_cSize = cSize;
        return hDelegate;
    }

    @Override
    protected void insertElementImpl(GenericArrayDelegate hDelegate, ObjectHandle hElement, long lIndex) {
        int                  cSize     = (int) hDelegate.m_cSize;
        ObjectHandle[]       ahValue   = hDelegate.m_ahValue;
        int                  nIndex    = (int) lIndex;

        if (cSize == ahValue.length) {
            ahValue = hDelegate.m_ahValue = grow(ahValue, cSize + 1);
        }
        hDelegate.m_cSize++;

        if (lIndex == cSize) {
            // append
            ahValue[cSize] = hElement;
        } else {
            // insert
            System.arraycopy(ahValue, nIndex, ahValue, nIndex +1, cSize - nIndex);
            ahValue[nIndex] = hElement;
        }
    }

    @Override
    protected void deleteElementImpl(GenericArrayDelegate hDelegate, long lIndex) {
        int                  cSize     = (int) hDelegate.m_cSize;
        ObjectHandle[]       ahValue   = hDelegate.m_ahValue;
        int                  nIndex    = (int) lIndex;

        if (nIndex < cSize - 1) {
            System.arraycopy(ahValue, nIndex + 1, ahValue, nIndex, cSize - nIndex - 1);
        }
        ahValue[(int) --hDelegate.m_cSize] = null;
    }
}
