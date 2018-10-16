package org.xvm.runtime.template;

import org.xvm.asm.ClassStructure;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.TemplateRegistry;
import org.xvm.runtime.TypeComposition;


/**
 * Base class for unsigned integer classes.
 */
public abstract class xUnsignedConstrainedInt
        extends xConstrainedInteger
    {
    protected xUnsignedConstrainedInt(TemplateRegistry templates, ClassStructure structure,
                                      long cMinValue, long cMaxValue,
                                      int cNumBits, boolean fChecked)
        {
        super(templates, structure, cMinValue, cMaxValue, cNumBits, true, fChecked);
        }

    @Override
    public int invokeAdd(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        long l1 = ((ObjectHandle.JavaLong) hTarget).getValue();
        long l2 = ((ObjectHandle.JavaLong) hArg).getValue();
        long lr = l1 + l2;

        if (f_fChecked && ( ((l1 & l2) | ((l1 | l2) & ~lr)) << f_cAddCheckShift ) < 0)
            {
            return overflow(frame);
            }

        return frame.assignValue(iReturn, makeJavaLong(lr));
        }

    @Override
    public int invokeSub(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        long l1 = ((ObjectHandle.JavaLong) hTarget).getValue();
        long l2 = ((ObjectHandle.JavaLong) hArg).getValue();
        long lr = l1 - l2;

        if (f_fChecked && ( ((~l1 & l2) | ((~l1 | l2) & lr)) << f_cAddCheckShift ) < 0)
            {
            return overflow(frame);
            }

        return frame.assignValue(iReturn, makeJavaLong(lr));
        }

    @Override
    public int callCompare(Frame frame, TypeComposition clazz,
                           ObjectHandle hValue1, ObjectHandle hValue2, int iReturn)
        {
        ObjectHandle.JavaLong h1 = (ObjectHandle.JavaLong) hValue1;
        ObjectHandle.JavaLong h2 = (ObjectHandle.JavaLong) hValue2;

        return frame.assignValue(iReturn, xOrdered.makeHandle(unsignedCompare(h1.getValue(), h2.getValue())));
        }

    public static int unsignedCompare(long a, long b)
        {
        if (a == b)
            {
            return 0;
            }

        if (((a & Long.MIN_VALUE) ^ (b & Long.MIN_VALUE)) != 0)
            {
            return a > b ? 1 : -1; // if both have the same neg bit, comparison works as normal
            }

        return a < b ? 1 : -1; // if one has neg bit high, comparison is inverted
        }

    public static String unsignedLongToString(long l)
        {
        if (l >= 0)
            {
            return String.valueOf(l);
            }

        int[] nPlaces = {0, 9, 2, 2, 3, 3, 7, 2, 0, 3, 6, 8, 5, 4, 7, 7, 5, 8, 0, 8}; // 2^64 in a size 20 array

        for (int i = 0; i < 64; ++i)
            {
            long lBitValue = l & (1L << i);
            int nPlace = nPlaces.length - 1;

            while (lBitValue > 0)
                {
                int bLastValue = (int)(lBitValue % 10);
                nPlaces[nPlace] += bLastValue;

                if (nPlaces[nPlace] >= 10)
                    {
                    nPlaces[nPlace] -= 10;

                    ++nPlaces[nPlace - 1]; // should never leave bounds, since the max number of digits for a 64 bit uint is 20
                    }

                --nPlace;
                lBitValue /= 10;
                }
            }

        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < nPlaces.length; ++i)
            {
            sb.append(nPlaces[i]);
            }

        return sb.toString();
        }
    }
