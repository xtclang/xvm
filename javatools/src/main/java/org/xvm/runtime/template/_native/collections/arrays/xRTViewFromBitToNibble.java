package org.xvm.runtime.template._native.collections.arrays;


import org.xvm.asm.ClassStructure;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.Op;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.ClassTemplate;
import org.xvm.runtime.Container;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.GenericHandle;

import org.xvm.runtime.template.collections.xArray.Mutability;

import org.xvm.runtime.template._native.collections.arrays.xRTSlicingDelegate.SliceHandle;


/**
 * The native RTViewFromBit<Nibble> implementation.
 */
public class xRTViewFromBitToNibble
        extends xRTViewFromBit
    {
    public static xRTViewFromBitToNibble INSTANCE;

    public xRTViewFromBitToNibble(Container container, ClassStructure structure, boolean fInstance)
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
                getInceptionClassConstant().getType(), pool.typeNibble());
        }

    @Override
    public DelegateHandle createBitViewDelegate(DelegateHandle hSource,
                                                Mutability mutability)
        {
        if (hSource instanceof SliceHandle hSlice)
            {
            // bits.slice().asNibbleArray() -> bits.asNibbleArray().slice()
            ViewHandle hView = new ViewHandle(getCanonicalClass(),
                    hSlice.f_hSource, hSlice.f_hSource.m_cSize/2, mutability);

            return slice(hView, hSlice.f_ofStart/4, hSlice.m_cSize/4, hSlice.f_fReverse);
            }
        return new ViewHandle(getCanonicalClass(), hSource, hSource.m_cSize/4, mutability);
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
            byte[] abBits = tView.getBytes(hSource, ofStart/2, cSize/2, fReverse);

            return xRTNibbleDelegate.INSTANCE.makeHandle(abBits, cSize, mutability);
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
            int nNibble = tView.extractByte(hSource, lIndex / 2) & 0xFF;
            if (lIndex % 2 == 0)
                {
                nNibble = nNibble >>> 4;
                }
            else
                {
                nNibble = nNibble & 0x0F;
                }
            return xRTNibbleDelegate.assignNibble(frame, nNibble, iReturn);
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
        int            nNibble = xRTNibbleDelegate.getValue((GenericHandle) hValue);

        if (tSource instanceof ByteView tView)
            {
            // the underlying delegate is a BitView, which is a ByteView
            int bValue = tView.extractByte(hSource, lIndex / 2) & 0xFF;
            if (lIndex % 2 == 0)
                {
                bValue = bValue & 0x0F | (nNibble << 4);
                }
            else
                {
                bValue = bValue & 0xF0 | nNibble;
                }
            tView.assignByte(hSource, lIndex, (byte) bValue);
            return Op.R_NEXT;
            }

        throw new UnsupportedOperationException();
        }
    }