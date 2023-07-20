package org.xvm.runtime.template._native.collections.arrays;


import org.xvm.asm.ClassStructure;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.Op;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.ClassTemplate;
import org.xvm.runtime.Container;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.JavaLong;

import org.xvm.runtime.template.collections.xArray.Mutability;

import org.xvm.runtime.template.numbers.xInt8;


/**
 * The native RTViewFromByte<Int8> implementation.
 */
public class xRTViewFromByteToInt8
        extends xRTViewFromByte
    {
    public static xRTViewFromByteToInt8 INSTANCE;

    public xRTViewFromByteToInt8(Container container, ClassStructure structure, boolean fInstance)
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
                getInceptionClassConstant().getType(),
                pool.typeInt8());
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
            byte[] abValue = tView.getBytes(hSource, ofStart, cSize, fReverse);

            return xRTInt8Delegate.INSTANCE.makeHandle(abValue, cSize, mutability);
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
            byte bValue = tView.extractByte(hSource, lIndex);

            return frame.assignValue(iReturn, xInt8.INSTANCE.makeJavaLong(bValue));
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
            tView.assignByte(hSource, lIndex, (byte) ((JavaLong) hValue).getValue());
            return Op.R_NEXT;
            }

        throw new UnsupportedOperationException();
        }
    }