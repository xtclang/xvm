package org.xvm.runtime.template.numbers;


import org.xvm.asm.ClassStructure;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.JavaLong;
import org.xvm.runtime.TemplateRegistry;
import org.xvm.runtime.TypeComposition;

import org.xvm.runtime.template.xOrdered;


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
        long l1 = ((JavaLong) hTarget).getValue();
        long l2 = ((JavaLong) hArg).getValue();
        long lr = l1 + l2;

        if (f_fChecked && (((l1 & l2) | ((l1 | l2) & ~lr)) << f_cAddCheckShift) < 0)
            {
            return overflow(frame);
            }

        return frame.assignValue(iReturn, makeJavaLong(lr));
        }

    @Override
    public int invokeSub(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        long l1 = ((JavaLong) hTarget).getValue();
        long l2 = ((JavaLong) hArg).getValue();
        long lr = l1 - l2;

        if (f_fChecked && (((~l1 & l2) | ((~l1 | l2) & lr)) << f_cAddCheckShift) < 0)
            {
            return overflow(frame);
            }

        return frame.assignValue(iReturn, makeJavaLong(lr));
        }

    @Override
    public int invokeDivRem(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int[] aiReturn)
        {
        long l1 = ((JavaLong) hTarget).getValue();
        long l2 = ((JavaLong) hArg).getValue();

        long lQuo = divUnassigned(l1, l2);
        long lRem = modUnassigned(l1, l2);

        return frame.assignValues(aiReturn, makeJavaLong(lQuo), makeJavaLong(lRem));
        }

    @Override
    public int callCompare(Frame frame, TypeComposition clazz,
                           ObjectHandle hValue1, ObjectHandle hValue2, int iReturn)
        {
        JavaLong h1 = (JavaLong) hValue1;
        JavaLong h2 = (JavaLong) hValue2;

        return frame.assignValue(iReturn, xOrdered.makeHandle(
            Long.compareUnsigned(h1.getValue(), h2.getValue())));
        }


    // ----- Uint64 helpers ------------------------------------------------------------------------

    public long mulUnassigned(Frame frame, long l1, long l2)
        {
        if (l1 <= 0)
            {
            // the first factor is bigger or equal than 2^63, so the answer is either 0 or l1
            if (l2 == 0 || l1 == 0)
                {
                return 0;
                }
            if (l2 == 1)
                {
                return l1;
                }
            return overflow(frame);
            }

        if (l2 <= 0)
            {
            // the first factor is bigger or equal than 2^63, so the answer is either 0 or l1
            if (l1 == 0 || l2 == 0)
                {
                return 0;
                }
            if (l1 == 1)
                {
                return l2;
                }
            return overflow(frame);
            }

        long lr = l1 * l2;

        if (f_fChecked &&
                (l1 | l2) >>> 31 != 0 && divUnassigned(lr, l2) != l1)
            {
            return overflow(frame);
            }
        return lr;
        }

    public long divUnassigned(long l1, long l2)
        {
        if (l2 < 0)
            {
            // the divisor is bigger or equal than 2^63, so the answer is either 0 or 1
            return l1 < 0 && l1 < l2 ? 1 : 0;
            }

        if (l1 < 0)
            {
            if (l2 == 1)
                {
                return l1;
                }

            // the dividend is bigger or equal then 2^63
            long l1L = l1 & 0x7FFF_FFFF_FFFF_FFFFL;

            // l1 = l1L + 2^63; r = (l1L + 2^63)/l2 =
            // l1L/l2 + 2^63/l2 + (l1L % l2 + 2^63 % l2)/l2
            //
            // Note: Long.MIN_VALUE/l2 and Long.MIN_VALUE % l2 are negative values

            return l1L/l2 - Long.MIN_VALUE/l2 + (l1L % l2 - Long.MIN_VALUE % l2)/l2;
            }

        return l1/l2;
        }

    public long modUnassigned(long l1, long l2)
        {
        if (l2 < 0)
            {
            // the divisor is bigger or equal than 2^63, so the answer is trivial
            return l1 < 0 && l1 < l2 ? l1 - l2 : l1;
            }

        if (l1 < 0)
            {
            if (l2 == 1)
                {
                return 0;
                }

            // the dividend is bigger or equal then 2^63
            long l1L = l1 & 0x7FFF_FFFF_FFFF_FFFFL;

            // l1 = l1L + 2^63; r = (l1L + 2^63) % l2 =
            // (l1L % l2 + 2^63 % l2)/l2
            //
            // Note: Long.MIN_VALUE/l2 and Long.MIN_VALUE % l2 are negative values

            return (l1L % l2 - Long.MIN_VALUE % l2) % l2;
            }

        return l1 % l2;
        }
    }
