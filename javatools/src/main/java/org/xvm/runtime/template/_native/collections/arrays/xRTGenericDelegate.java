package org.xvm.runtime.template._native.collections.arrays;


import java.util.Arrays;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Op;
import org.xvm.asm.Constant;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.Container;
import org.xvm.runtime.Frame;
import org.xvm.runtime.NativeTemplate;
import org.xvm.runtime.ObjectHandle;

import org.xvm.runtime.template.xException;

import org.xvm.runtime.template.collections.xArray.Mutability;


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
        extends xRTDelegate {
    public xRTGenericDelegate(Container container, ClassStructure structure) {
        super(container, structure);
    }

    @Override
    protected DelegateHandle createCopyImpl(DelegateHandle hTarget, Mutability mutability,
                                            long ofStart, long cSize, boolean fReverse) {
        GenericArrayDelegate hDelegate = (GenericArrayDelegate) hTarget;

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
    protected int extractArrayValueImpl(Frame frame, DelegateHandle hTarget, long lIndex, int iReturn) {
        return frame.assignValue(iReturn, ((GenericArrayDelegate) hTarget).m_ahValue[(int) lIndex]);
    }

    @Override
    protected int assignArrayValueImpl(Frame frame, DelegateHandle hTarget, long lIndex,
                                       ObjectHandle hValue) {
        GenericArrayDelegate hDelegate = (GenericArrayDelegate) hTarget;
        ObjectHandle[]       ahValue   = hDelegate.m_ahValue;
        int                  cSize     = (int) hDelegate.m_cSize;
        int                  nIndex    = (int) lIndex;

        if (nIndex == cSize) {
            if (cSize == ahValue.length) {
                ahValue = hDelegate.m_ahValue = grow(ahValue, cSize + 1);
            }
            hDelegate.m_cSize++;
        } else if (nIndex > cSize) {
            TypeConstant typeElement  = hTarget.getType().getParamType(0);
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
    protected void deleteRangeImpl(DelegateHandle hTarget, long lIndex, long cDelete) {
        GenericArrayDelegate hDelegate = (GenericArrayDelegate) hTarget;
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
}
