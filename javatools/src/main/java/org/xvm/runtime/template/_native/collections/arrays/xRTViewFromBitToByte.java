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
import org.xvm.runtime.ObjectHandle.JavaLong;

import org.xvm.runtime.template.collections.xArray.Mutability;

import org.xvm.runtime.template.numbers.xUInt8;

import org.xvm.runtime.template._native.collections.arrays.xRTSlicingDelegate.SliceHandle;


/**
 * The native RTViewFromBit<Byte> implementation.
 */
public class xRTViewFromBitToByte
        extends xRTViewFromBit
    {
    public static xRTViewFromBitToByte INSTANCE;

    public xRTViewFromBitToByte(Container container, ClassStructure structure, boolean fInstance)
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
                getInceptionClassConstant().getType(), pool.typeByte());
        }

    @Override
    public DelegateHandle createBitViewDelegate(DelegateHandle hSource,
                                                Mutability mutability)
        {
        ClassComposition clzView = getCanonicalClass();
        if (hSource instanceof SliceHandle hSlice)
            {
            // bits.slice().asByteArray() -> bits.asByteArray().slice()
            ViewHandle hView = new ViewHandle(clzView,
                    hSlice.f_hSource, hSlice.f_hSource.m_cSize/8, mutability);

            return slice(hView, hSlice.f_ofStart/8, hSlice.m_cSize/8, hSlice.f_fReverse);
            }

        if (hSource instanceof xRTViewFromBit.ViewHandle hView)
            {
            return new ViewHandle(clzView, hView.f_hSource, hSource.m_cSize/8, mutability);
            }

        return new ViewHandle(clzView, hSource, hSource.m_cSize/8, mutability);
        }


    // ----- RTDelegate API ------------------------------------------------------------------------

    @Override
    protected DelegateHandle createCopyImpl(DelegateHandle hTarget, Mutability mutability,
                                            long ofStart, long cSize, boolean fReverse)
        {
        ViewHandle     hView   = (ViewHandle) hTarget;
        DelegateHandle hSource = hView.f_hSource;
        ClassTemplate  tSource = hSource.getTemplate();

        if (tSource instanceof ByteView tView)
            {
            byte[] abBits = tView.getBytes(hSource, ofStart, cSize, fReverse);

            return xRTUInt8Delegate.INSTANCE.makeHandle(abBits, cSize, mutability);
            }

        throw new UnsupportedOperationException();
        }

    @Override
    protected int extractArrayValueImpl(Frame frame, DelegateHandle hTarget, long lIndex, int iReturn)
        {
        ViewHandle     hView   = (ViewHandle) hTarget;
        DelegateHandle hSource = hView.f_hSource;
        ClassTemplate  tSource = hSource.getTemplate();

        if (tSource instanceof ByteView tView)
            {
            // the underlying delegate is a BitView, which is a ByteView
            return frame.assignValue(iReturn,
                    xUInt8.INSTANCE.makeJavaLong(tView.extractByte(hSource, lIndex)));
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

        if (tSource instanceof ByteView tView)
            {
            // the underlying delegate is a BitView, which is a ByteView
            tView.assignByte(hSource, lIndex, (byte) ((JavaLong) hValue).getValue());
            return Op.R_NEXT;
            }

        throw new UnsupportedOperationException();
        }
    }