package org.xvm.runtime.template.numbers;


import java.math.BigInteger;
import org.xvm.asm.ClassStructure;
import org.xvm.asm.Constant;

import org.xvm.asm.constants.IntConstant;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.JavaLong;
import org.xvm.runtime.TemplateRegistry;

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
            }
        }

    @Override
    public void registerNativeTemplates()
        {
        // create unchecked template
        registerNativeTemplate(new xUncheckedUInt64(f_templates, f_struct, true));
        }

    @Override
    protected xConstrainedInteger getComplimentaryTemplate()
        {
        return xInt64.INSTANCE;
        }

    @Override
    protected xConstrainedInteger getUncheckedTemplate()
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
            return frame.pushStack(makeJavaLong(lValue));
            }

        return super.createConstHandle(frame, constant);
        }

    @Override
    public int invokeMul(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        long l1 = ((JavaLong) hTarget).getValue();
        long l2 = ((JavaLong) hArg).getValue();

        return frame.assignValue(iReturn, makeJavaLong(mulUnassigned(frame, l1, l2)));
        }

    @Override
    public int invokeDiv(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        long l1 = ((JavaLong) hTarget).getValue();
        long l2 = ((JavaLong) hArg).getValue();

        if (l2 == 0)
            {
            return overflow(frame);
            }

        return frame.assignValue(iReturn, makeJavaLong(divUnassigned(l1, l2)));
        }

    @Override
    public int invokeMod(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        long l1 = ((JavaLong) hTarget).getValue();
        long l2 = ((JavaLong) hArg).getValue();

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
    }
