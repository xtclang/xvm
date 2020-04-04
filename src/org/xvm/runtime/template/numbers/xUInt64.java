package org.xvm.runtime.template.numbers;


import java.math.BigInteger;
import org.xvm.asm.ClassStructure;
import org.xvm.asm.Constant;
import org.xvm.asm.Op;

import org.xvm.asm.constants.IntConstant;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.TemplateRegistry;

import org.xvm.runtime.template.xString;

import org.xvm.util.PackedInteger;


/**
 * Native UInt64 support.
 */
public class xUInt64
        extends xUnsignedConstrainedInt
    {
    public static xUInt64 INSTANCE;

    public xUInt64(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
        {
        super(templates, structure, 0, -1, 64,  true);

        if (fInstance)
            {
            INSTANCE = this;

            // create unchecked template
            new xUncheckedUInt64(templates, structure, true);
            }
        }

    @Override
    public void initDeclared()
        {
        super.initDeclared();

        xUncheckedUInt64.INSTANCE.initDeclared();
        }

    @Override
    protected xConstrainedInteger getComplimentaryTemplate()
        {
        return xInt64.INSTANCE;
        }

    @Override
    protected xUncheckedConstrainedInt getUncheckedTemplate()
        {
        return xUncheckedUInt64.INSTANCE;
        }

    @Override
    public int createConstHandle(Frame frame, Constant constant)
        {
        if (constant instanceof IntConstant)
            {
            PackedInteger piValue = ((IntConstant) constant).getValue();
            long          lValue;
            if (piValue.isBig())
                {
                // this must be a value outside the long range
                lValue = piValue.getBigInteger().longValue();
                }
            else
                {
                lValue = piValue.getLong();
                }
            frame.pushStack(makeJavaLong(lValue));
            return Op.R_NEXT;
            }

        return super.createConstHandle(frame, constant);
        }

    @Override
    public int invokeMul(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        long l1 = ((ObjectHandle.JavaLong) hTarget).getValue();
        long l2 = ((ObjectHandle.JavaLong) hArg).getValue();

        return frame.assignValue(iReturn, makeJavaLong(mulUnassigned(frame, l1, l2)));
        }

    @Override
    public int invokeDiv(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        long l1 = ((ObjectHandle.JavaLong) hTarget).getValue();
        long l2 = ((ObjectHandle.JavaLong) hArg).getValue();

        if (l2 == 0)
            {
            return overflow(frame);
            }

        return frame.assignValue(iReturn, makeJavaLong(divUnassigned(l1, l2)));
        }

    @Override
    public int invokeMod(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        long l1 = ((ObjectHandle.JavaLong) hTarget).getValue();
        long l2 = ((ObjectHandle.JavaLong) hArg).getValue();

        if (l2 == 0)
            {
            return overflow(frame);
            }

        return frame.assignValue(iReturn, makeJavaLong(modUnassigned(l1, l2)));
        }

    @Override
    public int convertLong(Frame frame, PackedInteger piValue, int iReturn)
        {
        if (piValue.isBig())
            {
            // there is a range: 0x7FFF_FFFF_FFFF_FFFF .. 0xFFFF_FFFF_FFFF_FFFF
            // that fits "long", but represented by the PackedInteger as "big"
            BigInteger bi = piValue.getBigInteger();
            if (bi.signum() > 0 && bi.bitLength() <= 64)
                {
                return frame.assignValue(iReturn, makeJavaLong(bi.longValue()));
                }
            else
                {
                return overflow(frame);
                }
            }
        else
            {
            return super.convertLong(frame, piValue, iReturn);
            }
        }


    // ----- helpers -------------------------------------------------------------------------------

    protected long mulUnassigned(Frame frame, long l1, long l2)
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

    protected long divUnassigned(long l1, long l2)
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
            long l1L = l1 & 0x7FFF_FFFF_FFFF_FFFFl;

            // l1 = l1L + 2^63; r = (l1L + 2^63)/l2 =
            // l1L/l2 + 2^63/l2 + (l1L % l2 + 2^63 % l2)/l2
            //
            // Note: Long.MIN_VALUE/l2 and Long.MIN_VALUE % l2 are negative values

            return l1L/l2 - Long.MIN_VALUE/l2 + (l1L % l2 - Long.MIN_VALUE % l2)/l2;
            }

        return l1/l2;
        }

    protected long modUnassigned(long l1, long l2)
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
            long l1L = l1 & 0x7FFF_FFFF_FFFF_FFFFl;

            // l1 = l1L + 2^63; r = (l1L + 2^63) % l2 =
            // (l1L % l2 + 2^63 % l2)/l2
            //
            // Note: Long.MIN_VALUE/l2 and Long.MIN_VALUE % l2 are negative values

            return (l1L % l2 - Long.MIN_VALUE % l2) % l2;
            }

        return l1 % l2;
        }

    @Override
    protected int buildStringValue(Frame frame, ObjectHandle hTarget, int iReturn)
        {
        long l = ((ObjectHandle.JavaLong) hTarget).getValue();

        return frame.assignValue(iReturn, xString.makeHandle(Long.toUnsignedString(l)));
        }
    }
