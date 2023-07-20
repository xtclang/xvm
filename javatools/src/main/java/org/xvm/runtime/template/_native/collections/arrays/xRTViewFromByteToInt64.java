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

import org.xvm.runtime.template.numbers.xInt;

import org.xvm.util.Handy;


/**
 * The native RTViewFromByte<Int64> implementation.
 */
public class xRTViewFromByteToInt64
        extends xRTViewFromByte
    {
    public static xRTViewFromByteToInt64 INSTANCE;

    public xRTViewFromByteToInt64(Container container, ClassStructure structure, boolean fInstance)
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
                getInceptionClassConstant().getType(), pool.typeInt64());
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
            long[] alValue = new long[(int) cSize];
            for (int i = 0; i < cSize; i++)
                {
                byte[] ab = tView.getBytes(hSource, ofStart + i*8L, 8, fReverse);

                alValue[i] = Handy.byteArrayToLong(ab, 0);
                }

            return xRTInt64Delegate.INSTANCE.makeHandle(alValue, alValue.length, mutability);
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
            byte[] ab = tView.getBytes(hSource, lIndex*8, 8, false);
            long   l  = Handy.byteArrayToLong(ab, 0);

            return frame.assignValue(iReturn, xInt.makeHandle(l));
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
            long lValue = ((JavaLong) hValue).getValue();

            tView.assignByte(hSource, (lIndex++)*8, (byte) ((lValue >>> 56) & 0xFF));
            tView.assignByte(hSource, (lIndex++)*8, (byte) ((lValue >>> 48) & 0xFF));
            tView.assignByte(hSource, (lIndex++)*8, (byte) ((lValue >>> 40) & 0xFF));
            tView.assignByte(hSource, (lIndex++)*8, (byte) ((lValue >>> 32) & 0xFF));
            tView.assignByte(hSource, (lIndex++)*8, (byte) ((lValue >>> 24) & 0xFF));
            tView.assignByte(hSource, (lIndex++)*8, (byte) ((lValue >>> 16) & 0xFF));
            tView.assignByte(hSource, (lIndex++)*8, (byte) ((lValue >>>  8) & 0xFF));
            tView.assignByte(hSource, (lIndex  )*8, (byte) ((lValue       ) & 0xFF));

            return Op.R_NEXT;
            }

        throw new UnsupportedOperationException();
        }
    }