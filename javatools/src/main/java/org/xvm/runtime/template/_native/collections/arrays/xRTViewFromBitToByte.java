package org.xvm.runtime.template._native.collections.arrays;


import org.xvm.asm.ClassStructure;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.Op;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.ClassTemplate;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.JavaLong;
import org.xvm.runtime.TemplateRegistry;
import org.xvm.runtime.TypeComposition;

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

    public xRTViewFromBitToByte(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
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
                getInceptionClassConstant().getType(), pool.typeByte());
        }

    @Override
    public DelegateHandle createBitViewDelegate(DelegateHandle hSource, TypeConstant typeElement,
                                                Mutability mutability)
        {
        if (hSource instanceof SliceHandle)
            {
            // bits.slice().asByteArray() -> bits.asByteArray().slice()
            SliceHandle hSlice = (SliceHandle) hSource;
            ViewHandle  hView  = new ViewHandle(getCanonicalClass(),
                                        hSlice.f_hSource, mutability);
            return slice(hView, hSlice.f_ofStart/8, hSlice.m_cSize/8, hSlice.f_fReverse);
            }
        return new ViewHandle(getCanonicalClass(), hSource, mutability);
        }


    // ----- RTDelegate API ------------------------------------------------------------------------

    @Override
    protected DelegateHandle createCopyImpl(DelegateHandle hTarget, Mutability mutability,
                                            long ofStart, long cSize, boolean fReverse)
        {
        ViewHandle     hView   = (ViewHandle) hTarget;
        DelegateHandle hSource = hView.f_hSource;
        ClassTemplate  tSource = hSource.getTemplate();

        if (tSource instanceof BitView)
            {
            byte[] abBits = ((BitView) tSource).getBits(hSource, ofStart, cSize, fReverse);

            return xRTByteDelegate.INSTANCE.makeHandle(abBits, cSize, mutability);
            }

        throw new UnsupportedOperationException();
        }

    @Override
    protected int extractArrayValueImpl(Frame frame, DelegateHandle hTarget, long lIndex, int iReturn)
        {
        ViewHandle     hView   = (ViewHandle) hTarget;
        DelegateHandle hSource = hView.f_hSource;
        ClassTemplate  tSource = hSource.getTemplate();

        if (tSource instanceof ByteView)
            {
            // the underlying delegate is a bit view, which is a ByteView
            ByteView tView = (ByteView) tSource;

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

        if (tSource instanceof ByteView)
            {
            // the underlying delegate is a bit view, which is a ByteView
            ByteView tView = (ByteView) tSource;

            tView.assignByte(hSource, lIndex, (byte) ((JavaLong) hValue).getValue());
            return Op.R_NEXT;
            }

        throw new UnsupportedOperationException();
        }


    // ----- handle --------------------------------------------------------------------------------

    /**
     * DelegateArray<Byte> view delegate.
     */
    protected static class ViewHandle
            extends DelegateHandle
        {
        public final DelegateHandle f_hSource;

        protected ViewHandle(TypeComposition clazz, DelegateHandle hSource, Mutability mutability)
            {
            super(clazz, mutability);

            f_hSource = hSource;
            m_cSize   = hSource.m_cSize / 8;
            }
        }

    // ----- constants -----------------------------------------------------------------------------
    }