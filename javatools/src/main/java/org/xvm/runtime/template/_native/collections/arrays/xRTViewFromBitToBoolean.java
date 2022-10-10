package org.xvm.runtime.template._native.collections.arrays;


import org.xvm.asm.ClassStructure;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.Op;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.ClassComposition;
import org.xvm.runtime.ClassTemplate;
import org.xvm.runtime.Container;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;

import org.xvm.runtime.template.xBoolean;
import org.xvm.runtime.template.xBoolean.BooleanHandle;

import org.xvm.runtime.template.collections.xArray.Mutability;

import org.xvm.runtime.template._native.collections.arrays.xRTSlicingDelegate.SliceHandle;


/**
 * The native RTViewFromBit<Boolean> implementation.
 */
public class xRTViewFromBitToBoolean
        extends xRTViewFromBit
    {
    public static xRTViewFromBitToBoolean INSTANCE;

    public xRTViewFromBitToBoolean(Container container, ClassStructure structure, boolean fInstance)
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
                getInceptionClassConstant().getType(), pool.typeBoolean());
        }

    @Override
    public DelegateHandle createBitViewDelegate(DelegateHandle hSource, Mutability mutability)
        {
        ClassComposition clzView = getCanonicalClass();
        if (hSource instanceof SliceHandle hSlice)
            {
            // bits.slice().asBooleanArray() -> bits.asBooleanArray().slice()
            ViewHandle hView = new ViewHandle(clzView,
                    hSlice.f_hSource, hSlice.f_hSource.m_cSize, mutability);

            return slice(hView, hSlice.f_ofStart, hSlice.m_cSize, hSlice.f_fReverse);
            }

        if (hSource instanceof ViewHandle hView)
            {
            return new ViewHandle(clzView, hView.f_hSource, hSource.m_cSize, mutability);
            }

        return new ViewHandle(clzView, hSource, hSource.m_cSize, mutability);
        }


    // ----- RTDelegate API ------------------------------------------------------------------------

    @Override
    protected DelegateHandle createCopyImpl(DelegateHandle hTarget, Mutability mutability,
                                            long ofStart, long cSize, boolean fReverse)
        {
        ViewHandle     hView   = (ViewHandle) hTarget;
        DelegateHandle hSource = hView.f_hSource;
        ClassTemplate  tSource = hSource.getTemplate();

        if (tSource instanceof BitView tView)
            {
            byte[] abBits = tView.getBytes(hSource, ofStart, cSize, fReverse);

            return xRTBooleanDelegate.INSTANCE.makeHandle(abBits, cSize, mutability);
            }

        throw new UnsupportedOperationException();
        }

    @Override
    protected int extractArrayValueImpl(Frame frame, DelegateHandle hTarget, long lIndex, int iReturn)
        {
        ViewHandle     hView   = (ViewHandle) hTarget;
        DelegateHandle hSource = hView.f_hSource;
        ClassTemplate  tSource = hSource.getTemplate();

        if (tSource instanceof BitView tView)
            {
            // the underlying delegate is a BitView, which is a ByteView
            return frame.assignValue(iReturn,
                    xBoolean.makeHandle(tView.extractBit(hSource, lIndex)));
            }

        throw new UnsupportedOperationException();
        }

    @Override
    public int assignArrayValueImpl(Frame frame, DelegateHandle hTarget, long lIndex,
                                    ObjectHandle hValue)
        {
        ViewHandle     hView   = (ViewHandle) hTarget;
        DelegateHandle hSource = hView.f_hSource;
        ClassTemplate  tSource = hSource.getTemplate();

        if (tSource instanceof BitView tView)
            {
            tView.assignBit(hSource, lIndex, ((BooleanHandle) hValue).get());
            return Op.R_NEXT;
            }

        throw new UnsupportedOperationException();
        }
    }