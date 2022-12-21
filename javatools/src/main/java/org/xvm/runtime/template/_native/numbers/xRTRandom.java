package org.xvm.runtime.template._native.numbers;


import java.util.Random;

import java.util.concurrent.ThreadLocalRandom;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.ClassComposition;
import org.xvm.runtime.Container;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.JavaLong;
import org.xvm.runtime.ServiceContext;
import org.xvm.runtime.TypeComposition;

import org.xvm.runtime.template.xException;
import org.xvm.runtime.template.xService;

import org.xvm.runtime.template.collections.xArray.ArrayHandle;
import org.xvm.runtime.template.collections.xArray.Mutability;
import org.xvm.runtime.template.collections.xBitArray;
import org.xvm.runtime.template.collections.xByteArray;

import org.xvm.runtime.template.numbers.xBit;
import org.xvm.runtime.template.numbers.xDec64;
import org.xvm.runtime.template.numbers.xFloat64;
import org.xvm.runtime.template.numbers.xInt64;
import org.xvm.runtime.template.numbers.xIntLiteral.IntNHandle;
import org.xvm.runtime.template.numbers.xUInt64;
import org.xvm.runtime.template.numbers.xUInt8;


/**
 * An injectable "Random" number generator.
 */
public class xRTRandom
        extends xService
    {
    public static xRTRandom INSTANCE;

    public xRTRandom(Container container, ClassStructure structure, boolean fInstance)
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
        String[] BIT       = new String[] {"numbers.Bit"};
        String[] BITARRAY  = new String[] {"collections.Array<numbers.Bit>"};
        String[] BYTE      = new String[] {"numbers.UInt8"};
        String[] BYTEARRAY = new String[] {"collections.Array<numbers.UInt8>"};
        String[] UINT      = new String[] {"numbers.UInt64"};
        String[] DEC       = new String[] {"numbers.Dec64"};
        String[] FLOAT     = new String[] {"numbers.Float64"};

        markNativeMethod("bit"  , VOID     , BIT  );
        markNativeMethod("fill" , BITARRAY , VOID );
        markNativeMethod("byte" , VOID     , BYTE );
        markNativeMethod("fill" , BYTEARRAY, VOID );
        markNativeMethod("int"  , VOID     , INT  );
        markNativeMethod("int"  , INT      , INT  );
        markNativeMethod("uint" , VOID     , UINT );
        markNativeMethod("dec"  , VOID     , DEC  );
        markNativeMethod("float", VOID     , FLOAT);

        invalidateTypeInfo();
        }

    @Override
    public TypeConstant getCanonicalType()
        {
        return pool().ensureEcstasyTypeConstant("numbers.Random");
        }

    @Override
    public int invokeNative1(Frame frame, MethodStructure method,
                             ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        switch (method.getName())
            {
            case "fill": // Bit[] or Byte[]
                {
                ArrayHandle hArray = (ArrayHandle) hArg;
                if (!hArray.isMutable() || hArray.getMutability().compareTo(Mutability.Fixed) < 0)
                    {
                    return frame.raiseException(xException.immutableObject(frame));
                    }

                long cSize = hArray.m_hDelegate.m_cSize;
                if (hArray.getTemplate() instanceof xBitArray)
                    {
                    byte[] ab = new byte[(int) (cSize / 8) + 1];
                    rnd(hTarget).nextBytes(ab);
                    xBitArray.setBits(hArray, ab, cSize);
                    }
                else
                    {
                    byte[] ab = new byte[(int) cSize];
                    rnd(hTarget).nextBytes(ab);
                    xByteArray.setBytes(hArray, ab);
                    }
                return Op.R_NEXT;
                }

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

    /**
     * Injection support.
     */
    public ObjectHandle ensureDefaultRandom(Frame frame, ObjectHandle hOpts)
        {
        long lSeed = hOpts instanceof JavaLong   hInt  ? hInt.getValue() :
                     hOpts instanceof IntNHandle hIntN ? hIntN.getValue().getLong() :
                     0;
        if (lSeed != 0)
            {
            return createRandomHandle(f_container.createServiceContext("Random"),
                    getCanonicalClass(), getCanonicalType(), lSeed);
            }

        ObjectHandle hRnd = m_hRandom;
        if (hRnd == null)
            {
            m_hRandom = hRnd = createRandomHandle(
                f_container.createServiceContext("Random"),
                    getCanonicalClass(), getCanonicalType(), 0L);
            }

        return hRnd;
        }


    // ----- methods -------------------------------------------------------------------------------

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

    public ServiceHandle createRandomHandle(ServiceContext context,
                                            ClassComposition clz, TypeConstant typeMask, long lSeed)
        {
        RandomHandle hService = new RandomHandle(clz.maskAs(typeMask), context,
                                        lSeed == 0 ? null : new Random(lSeed));
        context.setService(hService);
        return hService;
        }

    static public class RandomHandle
            extends ServiceHandle
        {
        public final Random f_random;

        public RandomHandle(TypeComposition clazz, ServiceContext context, Random random)
            {
            super(clazz, context);

            f_random = random;
            }
        }


    // ----- internal ------------------------------------------------------------------------------

    /**
     * @return the Random to use
     */
    protected Random rnd(ObjectHandle hTarget)
        {
        Random random = ((RandomHandle) hTarget).f_random;

        return random == null ? ThreadLocalRandom.current() : random;
        }

    /**
     * Cached Random handle.
     */
    private ObjectHandle m_hRandom;
    }