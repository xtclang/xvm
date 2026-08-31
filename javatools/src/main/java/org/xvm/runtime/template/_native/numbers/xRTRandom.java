package org.xvm.runtime.template._native.numbers;


import java.util.Random;

import java.util.concurrent.ThreadLocalRandom;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.MethodStructure;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.ClassComposition;
import org.xvm.runtime.Container;
import org.xvm.runtime.Frame;
import org.xvm.runtime.IntegralValue;
import org.xvm.runtime.NativeType;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.JavaLong;
import org.xvm.runtime.ServiceContext;
import org.xvm.runtime.TypeComposition;

import org.xvm.runtime.template.numbers.xNibble;
import org.xvm.runtime.template.xException;
import org.xvm.runtime.template.xService;

import org.xvm.runtime.template.collections.xArray.Mutability;
import org.xvm.runtime.template.collections.xBitArray;
import org.xvm.runtime.template.collections.xByteArray;

import org.xvm.runtime.template.numbers.BaseInt128.LongLongHandle;
import org.xvm.runtime.template.numbers.xBit;
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
        extends xService {
    public xRTRandom(Container container, ClassStructure structure) {
        super(container, structure);
    }

    /** The receiver type: RTRandom is a service, represented by its own RandomHandle. */
    private static final NativeType<RandomHandle> SELF =
            NativeType.of("_native.numbers.RTRandom", RandomHandle.class);

    /**
     * The {@code numbers.Int64} parameter type. Bound only for natives whose existing body casts
     * the argument to {@link JavaLong} UNCONDITIONALLY; {@code int(Int max)} is deliberately not
     * bound, because {@code invokeInt} accepts either a JavaLong or a LongLongHandle for the same
     * declared type.
     */
    private static final NativeType<JavaLong> INT_TYPE =
            NativeType.of("numbers.Int64", JavaLong.class);

    /**
     * The same {@code numbers.Int64} declaration, typed by what its handles SHARE rather than by a
     * handle class, for {@code int(Int max)} - whose bound may arrive in either representation.
     */
    private static final NativeType<IntegralValue> INT_VALUE =
            NativeType.ofShared("numbers.Int64", IntegralValue.class);

    @Override
    public void initNative() {
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
        markNativeMethod1("bits", SELF, INT_TYPE, BITARRAY,
                (frame, hRandom, hSize, iReturn) -> bits(frame, hRandom, hSize, iReturn));
        markNativeMethod1("bytes", SELF, INT_TYPE, BYTEARRAY,
                (frame, hRandom, hSize, iReturn) -> bytes(frame, hRandom, hSize, iReturn));
        markNativeMethod1("int", SELF, INT_VALUE, INT,
                (frame, hRandom, hMax, iReturn) -> invokeInt(frame, hRandom, hMax, iReturn));
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
    public TypeConstant getCanonicalType() {
        return pool().ensureEcstasyTypeConstant("numbers.Random");
    }

    @Override
    public int invokeNativeN(Frame frame, MethodStructure method,
                             ObjectHandle hTarget, ObjectHandle[] ahArg, int iReturn) {
        switch (method.getName()) {
        case "bit":
            return frame.assignValue(iReturn, xBit.makeHandle(frame, rnd(hTarget).nextBoolean()));

        case "nibble":
            return frame.assignValue(iReturn, xNibble.makeHandle(frame, rnd(hTarget).nextInt()));

        case "int8":
            return frame.assignValue(iReturn,
                    frame.container().nativeTemplates().int8().makeJavaLong(
                            rnd(hTarget).nextInt()));

        case "int16":
            return frame.assignValue(iReturn,
                    frame.container().nativeTemplates().int16().makeJavaLong(
                            rnd(hTarget).nextInt()));

        case "int32":
            return frame.assignValue(iReturn,
                    frame.container().nativeTemplates().int32().makeJavaLong(
                            rnd(hTarget).nextInt()));

        case "int64":
            return frame.assignValue(iReturn,
                    frame.container().nativeTemplates().int64().makeJavaLong(
                            rnd(hTarget).nextLong()));

        case "uint8":
            return frame.assignValue(iReturn,
                    frame.container().nativeTemplates().uint8().makeJavaLong(
                            rnd(hTarget).nextInt()));

        case "uint16":
            return frame.assignValue(iReturn,
                    frame.container().nativeTemplates().uint16().makeJavaLong(
                            rnd(hTarget).nextInt()));

        case "uint32":
            return frame.assignValue(iReturn,
                    frame.container().nativeTemplates().uint32().makeJavaLong(
                            rnd(hTarget).nextInt()));

        case "uint64":
            return frame.assignValue(iReturn,
                    frame.container().nativeTemplates().uint64().makeJavaLong(
                            rnd(hTarget).nextLong()));

        case "dec64":
            // Float64 has more precision than Dec64, so this should work fine, although there
            // won't be as solid of a guarantee on a perfect distribution of random values
            return frame.assignValue(iReturn,
                    frame.container().nativeTemplates().dec64().makeHandle(
                            rnd(hTarget).nextDouble()));

        case "float32":
            return frame.assignValue(iReturn,
                    frame.container().nativeTemplates().float32().makeHandle(
                            rnd(hTarget).nextFloat()));

        case "float64":
            return frame.assignValue(iReturn,
                    frame.container().nativeTemplates().float64().makeHandle(
                            rnd(hTarget).nextDouble()));
        }

        return super.invokeNativeN(frame, method, hTarget, ahArg, iReturn);
    }

    /**
     * Injection support.
     */
    public ObjectHandle ensureDefaultRandom(Frame frame, ObjectHandle hOpts) {
        long lSeed = hOpts instanceof JavaLong   hInt  ? hInt.getValue() :
                     hOpts instanceof IntNHandle hIntN ? hIntN.getValue().getLong() :
                     0;
        if (lSeed != 0) {
            return createRandomHandle(f_container.createServiceContext("Random"),
                    getCanonicalClass(), getCanonicalType(), lSeed);
        }

        ObjectHandle hRnd = m_hRandom;
        if (hRnd == null) {
            // shared native template + non-idempotent synchronous creation (registers a
            // service context): DCL over the volatile field so racing first injections
            // cannot create duplicate Random services
            synchronized (this) {
                hRnd = m_hRandom;
                if (hRnd == null) {
                    hRnd = createRandomHandle(
                            f_container.createServiceContext("Random"),
                            getCanonicalClass(), getCanonicalType(), 0L);
                    m_hRandom = hRnd;
                }
            }
        }

        return hRnd;
    }

    /**
     * Native implementation of "Int&nbsp;int(Int max)".
     */
    /** Native {@code Bit[] bits(Int size)}. */
    private int bits(Frame frame, RandomHandle hRandom, JavaLong hSize, int iReturn) {
        long cBits = hSize.getValue();
        if (cBits < 0) {
            return frame.raiseException(xException.illegalArgument(frame,
                    "size must be >= 0: " + cBits));
        }

        if (cBits > 2_000_000_000L) {
            return frame.raiseException(xException.illegalArgument(frame,
                    "size limit (2 billion bits) exceeded: " + cBits));
        }

        byte[] ab = new byte[(int) ((cBits+7)>>>3)];
        rnd(hRandom).nextBytes(ab);
        return frame.assignValue(iReturn,
                xBitArray.makeBitArrayHandle(frame.container(), ab, (int) cBits,
                        Mutability.Constant));
    }

    /** Native {@code Byte[] bytes(Int size)}. */
    private int bytes(Frame frame, RandomHandle hRandom, JavaLong hSize, int iReturn) {
        long cBytes = hSize.getValue();
        if (cBytes < 0) {
            return frame.raiseException(xException.illegalArgument(frame,
                    "array size must be >= 0: " + cBytes));
        }

        if (cBytes > 2_000_000_000L) {
            return frame.raiseException(xException.illegalArgument(frame,
                    "array size limit (2 billion bits) exceeded: " + cBytes));
        }

        byte[] ab = new byte[(int) cBytes];
        rnd(hRandom).nextBytes(ab);
        return frame.assignValue(iReturn,
                xByteArray.makeByteArrayHandle(frame.container(), ab, Mutability.Constant));
    }

    /**
     * Native {@code Int int(Int max)}.
     *
     * <p>The bound is declared {@code Int}, which has two Java representations, so this used to ask
     * which one arrived - {@code instanceof JavaLong}, else cast to {@code LongLongHandle} - and
     * carried a {@code fSmall} flag that both branches set and nothing ever read. Declaring the
     * parameter as {@link IntegralValue} lets it ask the value a question instead.</p>
     */
    private int invokeInt(Frame frame, RandomHandle hRandom, IntegralValue hMax, int iReturn) {
        if (!hMax.fitsLong(true)) {
            return frame.raiseException(xException.illegalArgument(frame,
                    "Exclusive maximum exceeds 64 bits"));
        }

        long lMax = hMax.longValue();
        if (lMax <= 0) {
            return frame.raiseException(xException.illegalArgument(frame,
                    "Illegal exclusive maximum (" + lMax +"); maximum must be > 0"));
        }

        return frame.assignValue(iReturn,
                xInt64.makeHandle(frame, computeRandom(rnd(hRandom), lMax)));
    }

    /**
     * @return a random positive long value that is lesser than the specified max
     */
    private long computeRandom(Random rnd, long lMax) {
        assert lMax > 0;

        if (lMax <= Integer.MAX_VALUE) {
            // it's a 32-bit random, so take a fast path in Java that handles 32-bit values
            return rnd.nextInt((int) lMax);
        } else if ((lMax & (lMax-1)) == 0) {
            // it's a power of 2, so avoid the 64-bit modulo
            return rnd.nextLong() & (lMax - 1);
        } else {
            // this works in theory, but has a slightly weaker guarantee on a perfect distribution
            // of random values
            return (rnd.nextLong() % lMax) & ~Long.MIN_VALUE;
        }
    }


    // ----- handle --------------------------------------------------------------------------------

    public ServiceHandle createRandomHandle(ServiceContext context,
                                            ClassComposition clz, TypeConstant typeMask, long lSeed) {
        RandomHandle hService = new RandomHandle(clz.maskAs(typeMask), context,
                                        lSeed == 0 ? null : new Random(lSeed));
        context.setService(hService);
        return hService;
    }

    public static class RandomHandle
            extends ServiceHandle {
        public final Random f_random;

        public RandomHandle(TypeComposition clazz, ServiceContext context, Random random) {
            super(clazz, context);

            f_random = random;
        }
    }


    // ----- internal ------------------------------------------------------------------------------

    /**
     * @return the Random to use
     */
    private Random rnd(ObjectHandle hTarget) {
        return rnd((RandomHandle) hTarget);
    }

    private Random rnd(RandomHandle hRandom) {
        Random random = hRandom.f_random;

        return random == null ? ThreadLocalRandom.current() : random;
    }

    /**
     * Cached Random handle.
     */
    private volatile ObjectHandle m_hRandom;
}
