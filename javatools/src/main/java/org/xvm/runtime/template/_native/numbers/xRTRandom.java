package org.xvm.runtime.template._native.numbers;


import java.util.Random;

import java.util.concurrent.ThreadLocalRandom;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.MethodStructure;

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

import org.xvm.runtime.template.collections.xArray.Mutability;
import org.xvm.runtime.template.collections.xBitArray;
import org.xvm.runtime.template.collections.xByteArray;

import org.xvm.runtime.template.numbers.BaseInt128.LongLongHandle;
import org.xvm.runtime.template.numbers.LongLong;
import org.xvm.runtime.template.numbers.xBit;
import org.xvm.runtime.template.numbers.xDec64;
import org.xvm.runtime.template.numbers.xFloat32;
import org.xvm.runtime.template.numbers.xFloat64;
import org.xvm.runtime.template.numbers.xInt64;
import org.xvm.runtime.template.numbers.xInt8;
import org.xvm.runtime.template.numbers.xInt16;
import org.xvm.runtime.template.numbers.xInt32;
import org.xvm.runtime.template.numbers.xIntLiteral.IntNHandle;
import org.xvm.runtime.template.numbers.xUInt8;
import org.xvm.runtime.template.numbers.xUInt16;
import org.xvm.runtime.template.numbers.xUInt32;
import org.xvm.runtime.template.numbers.xUInt64;


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
        String[] BITARRAY  = new String[] {"immutable collections.Array<numbers.Bit>"};
        String[] BYTEARRAY = new String[] {"immutable collections.Array<numbers.UInt8>"};
        String[] INT8      = new String[] {"numbers.Int8"};
        String[] INT16     = new String[] {"numbers.Int16"};
        String[] INT32     = new String[] {"numbers.Int32"};
        String[] INT64     = new String[] {"numbers.Int64"};
        String[] UINT8     = new String[] {"numbers.UInt8"};
        String[] UINT16    = new String[] {"numbers.UInt16"};
        String[] UINT32    = new String[] {"numbers.UInt32"};
        String[] UINT64    = new String[] {"numbers.UInt64"};
        String[] DEC64     = new String[] {"numbers.Dec64"};
        String[] FLOAT32   = new String[] {"numbers.Float32"};
        String[] FLOAT64   = new String[] {"numbers.Float64"};

        markNativeMethod("bit"    , VOID     , BIT      );
        markNativeMethod("bits"   , INT      , BITARRAY );
        markNativeMethod("bytes"  , INT      , BYTEARRAY);
        markNativeMethod("int"    , INT      , INT      );
        markNativeMethod("int8"   , VOID     , INT8     );
        markNativeMethod("int16"  , VOID     , INT16    );
        markNativeMethod("int32"  , VOID     , INT32    );
        markNativeMethod("int64"  , VOID     , INT64    );
        markNativeMethod("uint8"  , VOID     , UINT8    );
        markNativeMethod("uint16" , VOID     , UINT16   );
        markNativeMethod("uint32" , VOID     , UINT32   );
        markNativeMethod("uint64" , VOID     , UINT64   );
        markNativeMethod("dec64"  , VOID     , DEC64    );
        markNativeMethod("float32", VOID     , FLOAT32  );
        markNativeMethod("float64", VOID     , FLOAT64  );

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
            case "bits": // "Bit[] bits(Int size)"
                {
                long cBits = ((JavaLong) hArg).getValue();
                if (cBits < 0)
                    {
                    return frame.raiseException(xException.illegalArgument(frame,
                            "size must be >= 0: " + cBits));
                    }

                if (cBits > 2_000_000_000L)
                    {
                    return frame.raiseException(xException.illegalArgument(frame,
                            "size limit (2 billion bits) exceeded: " + cBits));
                    }

                byte[] ab = new byte[(int) ((cBits+7)>>>3)];
                rnd(hTarget).nextBytes(ab);
                return frame.assignValue(iReturn, xBitArray.makeBitArrayHandle(ab, (int) cBits, Mutability.Constant));
                }

            case "bytes": // "Byte[] bytes(Int size)"
                {
                long cBytes = ((JavaLong) hArg).getValue();
                if (cBytes < 0)
                    {
                    return frame.raiseException(xException.illegalArgument(frame,
                            "array size must be >= 0: " + cBytes));
                    }

                if (cBytes > 2_000_000_000L)
                    {
                    return frame.raiseException(xException.illegalArgument(frame,
                            "array size limit (2 billion bits) exceeded: " + cBytes));
                    }

                byte[] ab = new byte[(int) cBytes];
                rnd(hTarget).nextBytes(ab);
                return frame.assignValue(iReturn, xByteArray.makeByteArrayHandle(ab, Mutability.Constant));
                }

            case "int":
                return invokeInt(frame, hTarget, hArg, iReturn);
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
                return frame.assignValue(iReturn, xBit.makeHandle(rnd(hTarget).nextBoolean()));

            case "int8":
                return frame.assignValue(iReturn, xInt8.INSTANCE.makeJavaLong(rnd(hTarget).nextInt()));

            case "int16":
                return frame.assignValue(iReturn, xInt16.INSTANCE.makeJavaLong(rnd(hTarget).nextInt()));

            case "int32":
                return frame.assignValue(iReturn, xInt32.INSTANCE.makeJavaLong(rnd(hTarget).nextInt()));

            case "int64":
                return frame.assignValue(iReturn, xInt64.INSTANCE.makeJavaLong(rnd(hTarget).nextLong()));

            case "uint8":
                return frame.assignValue(iReturn, xUInt8.INSTANCE.makeJavaLong(rnd(hTarget).nextInt()));

            case "uint16":
                return frame.assignValue(iReturn, xUInt16.INSTANCE.makeJavaLong(rnd(hTarget).nextInt()));

            case "uint32":
                return frame.assignValue(iReturn, xUInt32.INSTANCE.makeJavaLong(rnd(hTarget).nextInt()));

            case "uint64":
                return frame.assignValue(iReturn, xUInt64.INSTANCE.makeJavaLong(rnd(hTarget).nextLong()));

            case "dec64":
                // Float64 has more precision than Dec64, so this should work fine, although there
                // won't be as solid of a guarantee on a perfect distribution of random values
                return frame.assignValue(iReturn, xDec64.INSTANCE.makeHandle(rnd(hTarget).nextDouble()));

            case "float32":
                return frame.assignValue(iReturn, xFloat32.INSTANCE.makeHandle(rnd(hTarget).nextFloat()));

            case "float64":
                return frame.assignValue(iReturn, xFloat64.INSTANCE.makeHandle(rnd(hTarget).nextDouble()));
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

    /**
     * Native implementation of "Int&nbsp;int(Int max)".
     */
    protected int invokeInt(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        boolean  fSmall = false;
        long     lMax   = 0;
        LongLong llMax  = null;

        if (hArg instanceof JavaLong hL)
            {
            fSmall = true;
            lMax   = hL.getValue();
            }
        else
            {
            llMax = ((LongLongHandle) hArg).getValue();
            if (llMax.isSmall(true))
                {
                fSmall = true;
                lMax   = llMax.getLowValue();
                }
            else
                {
                throw new IllegalStateException();
                }
            }

        Random rnd = rnd(hTarget);

        if (lMax <= 0)
            {
            return frame.raiseException(xException.illegalArgument(frame,
                    "Illegal exclusive maximum (" + lMax +"); maximum must be > 0"));
            }

        return frame.assignValue(iReturn, xInt64.makeHandle(computeRandom(rnd, lMax)));
        }

    /**
     * @return a random positive long value that is lesser than the specified max
     */
    private long computeRandom(Random rnd, long lMax)
        {
        assert lMax > 0;

        if (lMax <= Integer.MAX_VALUE)
            {
            // it's a 32-bit random, so take a fast path in Java that handles 32-bit values
            return rnd.nextInt((int) lMax);
            }
        else if ((lMax & (lMax-1)) == 0)
            {
            // it's a power of 2, so avoid the 64-bit modulo
            return rnd.nextLong() & (lMax - 1);
            }
        else
            {
            // this works in theory, but has a slightly weaker guarantee on a perfect distribution
            // of random values
            return (rnd.nextLong() % lMax) & ~Long.MIN_VALUE;
            }
        }


    // ----- handle --------------------------------------------------------------------------------

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
    private Random rnd(ObjectHandle hTarget)
        {
        Random random = ((RandomHandle) hTarget).f_random;

        return random == null ? ThreadLocalRandom.current() : random;
        }

    /**
     * Cached Random handle.
     */
    private ObjectHandle m_hRandom;
    }