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

import org.xvm.runtime.template.collections.xArray.Mutability;

import org.xvm.runtime.template.numbers.xInt64;

import org.xvm.util.Handy;


/**
 * The native RTViewFromByte<Int64> implementation.
 */
public class xRTViewFromByteToInt64
        extends xRTViewFromByte
    {
    public static xRTViewFromByteToInt64 INSTANCE;

    public xRTViewFromByteToInt64(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
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
                getInceptionClassConstant().getType(), pool.typeInt());
        }


    // ----- RTDelegate API ------------------------------------------------------------------------

    @Override
    protected DelegateHandle createCopyImpl(DelegateHandle hTarget, Mutability mutability,
                                            long ofStart, long cSize, boolean fReverse)
        {
        ViewHandle     hView   = (ViewHandle) hTarget;
        DelegateHandle hSource = hView.f_hSource;
        ClassTemplate  tSource = hSource.getTemplate();

        if (tSource instanceof ByteView)
            {
            ByteView tView = (ByteView) tSource;

            long[] alValue = new long[(int) cSize];
            for (int i = 0; i < cSize; i++)
                {
                byte[] ab = tView.getBytes(hSource, ofStart + i*8, 8, fReverse);

                alValue[i] = Handy.byteArrayToLong(ab, 0);
                }

            return xRTInt64Delegate.INSTANCE.makeHandle(alValue, mutability);
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
            ByteView tView = (ByteView) tSource;

            byte[] ab = tView.getBytes(hSource, lIndex*8, 8, false);

            return frame.assignValue(iReturn, xInt64.makeHandle(Handy.byteArrayToLong(ab, 0)));
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
            ByteView tView = (ByteView) tSource;

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