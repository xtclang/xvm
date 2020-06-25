package org.xvm.runtime.template._native.numbers;


import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.JavaLong;
import org.xvm.runtime.TemplateRegistry;

import org.xvm.runtime.template.numbers.xBit;
import org.xvm.runtime.template.numbers.xDec64;
import org.xvm.runtime.template.numbers.xFloat64;
import org.xvm.runtime.template.numbers.xInt64;
import org.xvm.runtime.template.numbers.xUInt64;
import org.xvm.runtime.template.numbers.xUInt8;
import org.xvm.runtime.template.xException;
import org.xvm.runtime.template.xService;

import org.xvm.runtime.template.collections.BitBasedArray.BitArrayHandle;
import org.xvm.runtime.template.collections.xByteArray.ByteArrayHandle;


/**
 * An injectable "Random" number generator.
 */
public class xRTRandom
        extends xService
    {
    public xRTRandom(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
        {
        super(templates, structure, false);
        }

    @Override
    public void initNative()
        {
        final String[] BIT       = new String[] {"numbers.Bit"};
        final String[] BITARRAY  = new String[] {"collections.Array<numbers.Bit>"};
        final String[] BYTE      = new String[] {"numbers.UInt8"};
        final String[] BYTEARRAY = new String[] {"collections.Array<numbers.UInt8>"};
        final String[] UINT      = new String[] {"numbers.UInt64"};
        final String[] DEC       = new String[] {"numbers.Dec64"};
        final String[] FLOAT     = new String[] {"numbers.Float64"};

        markNativeMethod("bit"  , VOID     , BIT  );
        markNativeMethod("fill" , BITARRAY , VOID );
        markNativeMethod("byte" , VOID     , BYTE );
        markNativeMethod("fill" , BYTEARRAY, VOID );
        markNativeMethod("int"  , VOID     , INT  );
        markNativeMethod("int"  , INT      , INT  );
        markNativeMethod("uint" , VOID     , UINT );
        markNativeMethod("dec"  , VOID     , DEC  );
        markNativeMethod("float", VOID     , FLOAT);

        getCanonicalType().invalidateTypeInfo();
        }

    @Override
    public int invokeNative1(Frame frame, MethodStructure method,
                             ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        switch (method.getName())
            {
            case "fill": // Bit[] or Byte[]
                return hArg instanceof BitArrayHandle
                        ? invokeFill(frame, hTarget, (BitArrayHandle) hArg)
                        : invokeFill(frame, hTarget, (ByteArrayHandle) hArg);

            case "int":
                return invokeInt(frame, hTarget, (JavaLong) hArg, iReturn);
            }
        return super.invokeNative1(frame, method, hTarget, hArg, iReturn);
        }

    @Override
    public int invokeNativeN(Frame frame, MethodStructure method,
                             ObjectHandle hTarget, ObjectHandle[] ahArg, int iReturn)
        {
        switch (method.getName())
            {
            case "bit":
                return invokeBit(frame, hTarget, iReturn);

            case "byte":
                return invokeByte(frame, hTarget, iReturn);

            case "int":
                return invokeInt(frame, hTarget, iReturn);

            case "uint":
                return invokeUInt(frame, hTarget, iReturn);

            case "dec":
                return invokeDec(frame, hTarget, iReturn);

            case "float":
                return invokeFloat(frame, hTarget, iReturn);
            }

        return super.invokeNativeN(frame, method, hTarget, ahArg, iReturn);
        }


    // ----- methods -------------------------------------------------------------------------------

    protected int invokeFill(Frame frame, ObjectHandle hTarget, BitArrayHandle hArray)
        {
        if (hArray.isMutable() && hArray.m_mutability.compareTo(ObjectHandle.Mutability.FixedSize) <= 0)
            {
            // REVIEW GG this may set bits beyond "m_cSize" ... is that a problem?
            rnd(hTarget).nextBytes(hArray.m_abValue);
            return Op.R_NEXT;
            }
        else
            {
            return frame.raiseException(xException.immutableObject(frame));
            }
        }

    protected int invokeFill(Frame frame, ObjectHandle hTarget, ByteArrayHandle hArray)
        {
        rnd(hTarget).nextBytes(hArray.m_abValue);
        return Op.R_NEXT;
        }

    protected int invokeBit(Frame frame, ObjectHandle hTarget, int iReturn)
        {
        return frame.assignValue(iReturn, xBit.makeHandle(rnd(hTarget).nextBoolean()));
        }

    protected int invokeByte(Frame frame, ObjectHandle hTarget, int iReturn)
        {
        return frame.assignValue(iReturn, xUInt8.makeHandle(rnd(hTarget).nextInt(256)));
        }

    protected int invokeInt(Frame frame, ObjectHandle hTarget, int iReturn)
        {
        return frame.assignValue(iReturn, xInt64.makeHandle(rnd(hTarget).nextLong()));
        }

    protected int invokeInt(Frame frame, ObjectHandle hTarget, JavaLong hArg, int iReturn)
        {
        long lMax = hArg.getValue();
        if (lMax <= 0)
            {
            return frame.raiseException(xException.illegalArgument(frame,
                    "Illegal exclusive maximum (" + lMax +"); maximum must be >= 0"));
            }

        Random rnd = rnd(hTarget);
        long   lRnd;
        if (lMax <= Integer.MAX_VALUE)
            {
            // it's a 32-bit random, so take a fast path in Java that handles 32-bit values
            lRnd = rnd.nextInt((int) lMax);
            }
        else if ((lMax & lMax-1) == 0)
            {
            // it's a power of 2, so avoid the 64-bit modulo
            lRnd = rnd.nextLong() & lMax-1;
            }
        else
            {
            // this works in theory, but has a slightly weaker guarantee on a perfect distribution
            // of random values
            lRnd = rnd.nextLong() % lMax;
            }

        return frame.assignValue(iReturn, xInt64.makeHandle(lRnd));
        }

    protected int invokeUInt(Frame frame, ObjectHandle hTarget, int iReturn)
        {
        return frame.assignValue(iReturn, xUInt64.INSTANCE.makeJavaLong(rnd(hTarget).nextLong()));
        }

    protected int invokeDec(Frame frame, ObjectHandle hTarget, int iReturn)
        {
        // Float64 has more precision than Dec64, so this should work fine, although there won't
        // be as solid of a guarantee on a perfect distribution of random values
        return frame.assignValue(iReturn, xDec64.INSTANCE.makeHandle(rnd(hTarget).nextDouble()));
        }

    protected int invokeFloat(Frame frame, ObjectHandle hTarget, int iReturn)
        {
        return frame.assignValue(iReturn, xFloat64.INSTANCE.makeHandle(rnd(hTarget).nextDouble()));
        }


    // ----- internal ------------------------------------------------------------------------------

    /**
     * @return the Random to use
     */
    protected Random rnd(ObjectHandle hTarget)
        {
        return ThreadLocalRandom.current();
        }
    }
