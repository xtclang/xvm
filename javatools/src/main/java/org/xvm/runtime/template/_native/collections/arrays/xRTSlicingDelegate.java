package org.xvm.runtime.template._native.collections.arrays;


import org.xvm.asm.ClassStructure;
import org.xvm.asm.Op;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.ClassTemplate;
import org.xvm.runtime.Container;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.TypeComposition;

import org.xvm.runtime.template.xException;

import org.xvm.runtime.template.collections.xArray.Mutability;


/**
 * The native RTSlicingDelegate<Object> implementation.
 */
public class xRTSlicingDelegate
        extends xRTDelegate
    {
    public static xRTSlicingDelegate INSTANCE;

    public xRTSlicingDelegate(Container container, ClassStructure structure, boolean fInstance)
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
    public ClassTemplate getTemplate(TypeConstant type)
        {
        return this;
        }


    // ----- RTDelegate API ------------------------------------------------------------------------

    @Override
    protected int getPropertyCapacity(Frame frame, ObjectHandle hTarget, int iReturn)
        {
        return getPropertySize(frame, hTarget, iReturn);
        }

    @Override
    protected int setPropertyCapacity(Frame frame, ObjectHandle hTarget, long nCapacity)
        {
        SliceHandle hSlice = (SliceHandle) hTarget;

        return nCapacity == hSlice.m_cSize
            ? Op.R_NEXT
            : frame.raiseException(xException.readOnly(frame, hSlice.getMutability()));
        }

    @Override
    protected int invokeInsertElement(Frame frame, ObjectHandle hTarget,
                                      ObjectHandle.JavaLong hIndex, ObjectHandle hValue, int iReturn)
        {
        return frame.raiseException(
                xException.readOnly(frame, ((SliceHandle) hTarget).getMutability()));
        }

    @Override
    protected int invokeDeleteElement(Frame frame, ObjectHandle hTarget, ObjectHandle hValue, int iReturn)
        {
        return frame.raiseException(
                xException.readOnly(frame, ((SliceHandle) hTarget).getMutability()));
        }

    @Override
    public DelegateHandle fill(DelegateHandle hTarget, int cSize, ObjectHandle hValue)
        {
        return null;
        }

    @Override
    public DelegateHandle deleteRange(DelegateHandle hTarget, long ofStart, long cSize)
        {
        return null;
        }

    @Override
    public DelegateHandle slice(DelegateHandle hTarget, long ofStart, long cSize, boolean fReverse)
        {
        SliceHandle    hSlice  = (SliceHandle) hTarget;
        DelegateHandle hSource = hSlice.f_hSource;

        return ofStart == 0 && cSize == hSlice.m_cSize && !fReverse
                ? hSlice
                : ((xRTDelegate) hSource.getTemplate()).slice(hSource,
                        ofStart + hSlice.f_ofStart, cSize, fReverse ^ hSlice.f_fReverse);
        }

    @Override
    protected DelegateHandle createCopyImpl(DelegateHandle hTarget, Mutability mutability,
                                            long ofStart, long cSize, boolean fReverse)
        {
        SliceHandle    hSlice  = (SliceHandle) hTarget;
        DelegateHandle hSource = hSlice.f_hSource;

        return ((xRTDelegate) hSource.getTemplate()).createCopyImpl(hSource, mutability,
                (int) translateIndex(hSlice, ofStart), cSize, hSlice.f_fReverse);
        }

    @Override
    protected int extractArrayValueImpl(Frame frame, DelegateHandle hTarget, long lIndex, int iReturn)
        {
        SliceHandle    hSlice  = (SliceHandle) hTarget;
        DelegateHandle hSource = hSlice.f_hSource;

        return ((xRTDelegate) hSource.getTemplate()).
                extractArrayValue(frame, hSource, translateIndex(hSlice, lIndex), iReturn);
        }

    @Override
    protected int assignArrayValueImpl(Frame frame, DelegateHandle hTarget, long lIndex,
                                       ObjectHandle hValue)
        {
        SliceHandle    hSlice  = (SliceHandle) hTarget;
        DelegateHandle hSource = hSlice.f_hSource;

        return ((xRTDelegate) hSource.getTemplate()).
                assignArrayValue(frame, hSource, translateIndex(hSlice, lIndex), hValue);
        }

    private static long translateIndex(SliceHandle hSlice, long lIndex)
        {
        return hSlice.f_fReverse
                ? hSlice.f_ofStart + hSlice.m_cSize - 1 - lIndex
                : hSlice.f_ofStart + lIndex;
        }


    // ----- handle --------------------------------------------------------------------------------

    /**
     * Make a slicing handle into the specified source.
     */
    public SliceHandle makeHandle(DelegateHandle hSource, long ofStart, long cSize, boolean fReverse)
        {
        TypeConstant typeElement = hSource.getType().getParamType(0);
        TypeConstant typeSlice   = typeElement.getConstantPool().
                ensureParameterizedTypeConstant(getClassConstant().getType(), typeElement);
        return new SliceHandle(ensureClass(typeSlice),
                hSource, Mutability.Fixed, (int) ofStart, (int) cSize, fReverse);
        }

    /**
     * Array slice delegate.
     */
    public static class SliceHandle
            extends DelegateHandle
        {
        public final DelegateHandle f_hSource;
        public final long           f_ofStart;
        public final boolean        f_fReverse;

        protected SliceHandle(TypeComposition clazz, DelegateHandle hSource,
                              Mutability mutability, long ofStart, long cSize, boolean fReverse)
            {
            super(clazz, mutability);

            f_hSource  = hSource;
            f_ofStart  = ofStart;
            f_fReverse = fReverse;
            m_cSize    = cSize;
            }

        @Override
        public String toString()
            {
            return super.toString() + " @" + f_ofStart;
            }
        }
    }