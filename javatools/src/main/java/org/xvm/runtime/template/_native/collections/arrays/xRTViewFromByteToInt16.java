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

import org.xvm.runtime.template.numbers.xInt16;


/**
 * The native RTViewFromByte<Int16> implementation.
 */
public class xRTViewFromByteToInt16
        extends xRTViewFromByte
    {
    public static xRTViewFromByteToInt16 INSTANCE;

    public xRTViewFromByteToInt16(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
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
                getInceptionClassConstant().getType(),
                pool.ensureEcstasyTypeConstant("numbers.Int16"));
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

            int     nSize   = (int) cSize;
            short[] anValue = new short[nSize];
            for (int i = 0; i < nSize; i++)
                {
                byte bValue0 = tView.extractByte(hSource, ofStart + i*2    );
                byte bValue1 = tView.extractByte(hSource, ofStart + i*2 + 1);

                anValue[i] = (short) (((short) bValue0) << 8 | (bValue1 & 0xFF));
                }

            return xRTInt16Delegate.INSTANCE.packHandle(anValue, mutability);
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

            byte bValue0 = tView.extractByte(hSource, lIndex*2    );
            byte bValue1 = tView.extractByte(hSource, lIndex*2 + 1);

            // using a "short" intermediary takes care of the sign handling
            short nValue = (short) (((short) bValue0) << 8 | (bValue1 & 0xFF));
            return frame.assignValue(iReturn, xInt16.INSTANCE.makeJavaLong(nValue));
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

            tView.assignByte(hSource, lIndex*2 + 1, (byte) ((lValue >>>  8) & 0xFF));
            tView.assignByte(hSource, lIndex*2    , (byte) ((lValue       ) & 0xFF));

            return Op.R_NEXT;
            }

        throw new UnsupportedOperationException();
        }
    }