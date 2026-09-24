package org.xtclang._native.numbers;

import java.math.BigDecimal;
import java.math.MathContext;

import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

import org.xtclang.ecstasy.Exception;
import org.xtclang.ecstasy.nService;

import org.xtclang.ecstasy.collections.ArrayᐸBitᐳ;
import org.xtclang.ecstasy.collections.ArrayᐸUInt8ᐳ;

import org.xtclang.ecstasy.numbers.Dec64;
import org.xtclang.ecstasy.numbers.Int64;
import org.xtclang.ecstasy.numbers.IntLiteral;

import org.xvm.javajit.Ctx;

/**
 * Native implementation for RTRandom.
 */
public class RTRandom extends nService {
    public RTRandom(Random random) {
        super(null);
        $random = random;
    }

    private final Random $random; // if null, the ThreadLocalRandom is used

    /**
     * TODO: native arrays store a 30-bit size alongside their mutability; raise this limit when
     *       huge-array storage is implemented.
     */
    private static final long $MAX_ARRAY_SIZE = (1L << 30) - 1;

    /**
     * @return the Random to use
     */
    private Random rnd() {
        return $random == null ? ThreadLocalRandom.current() : $random;
    }

    // ------ Random API ---------------------------------------------------------------------------

    /**
     * An optimized implementation of "Bit&nbsp;bit()"
     */
    public int bit$p(Ctx ctx) {
        return rnd().nextBoolean() ? 1 : 0;
    }

    /**
     * An optimized implementation of "immutable Bit[] bits(Int size)"
     */
    public ArrayᐸBitᐳ bits$p(Ctx ctx, long size) {
        if (size < 0) {
            throw Exception.$illegalArg(ctx, "size must be >= 0: " + size);
        }

        if (size > $MAX_ARRAY_SIZE) {
            throw Exception.$illegalArg(ctx,
                    "size limit (" + $MAX_ARRAY_SIZE + " bits) exceeded: " + size);
        }

        return new ArrayᐸBitᐳ(ctx, ctx.pool().typeBitArray(),
                $randomLongs((int) ((size + 7) >>> 3)), size);
    }

    /**
     * Native implementation of: "immutable Byte[] bytes(Int size)"
     */
    public ArrayᐸUInt8ᐳ bytes$p(Ctx ctx, long size) {
        if (size < 0) {
            throw Exception.$illegalArg(ctx, "array size must be >= 0: " + size);
        }

        if (size > $MAX_ARRAY_SIZE) {
            throw Exception.$illegalArg(ctx,
                    "array size limit (" + $MAX_ARRAY_SIZE + " bytes) exceeded: " + size);
        }

        return new ArrayᐸUInt8ᐳ(ctx, ctx.pool().typeByteArray(), $randomLongs((int) size), size);
    }

    /**
     * Native implementation of "Int&nbsp;int(Int max)".
     *
     * Copied from xRTRandom.java.
     */
    public long int$p(Ctx ctx, long max) {
        Random rnd = rnd();

        if (max <= 0) {
            throw Exception.$illegalArg(ctx,
                    "Illegal exclusive maximum (" + max + "); maximum must be > 0");
        }

        if (max <= Integer.MAX_VALUE) {
            // it's a 32-bit random, so take a fast path in Java that handles 32-bit values
            return rnd.nextInt((int) max);
        } else if ((max & (max-1)) == 0) {
            // it's a power of 2, so avoid the 64-bit modulo
            return rnd.nextLong() & (max - 1);
        } else {
            return rnd.nextLong(max);
        }
    }

    /**
     * Native implementation of "Int8&nbsp;int8()".
     */
    public int int8$p(Ctx ctx) {
        return (byte) rnd().nextInt();
    }

    /**
     * Native implementation of: "Int16&nbsp;int16()"
     */
    public int int16$p(Ctx ctx) {
        return (short) rnd().nextInt();
    }

    /**
     * Native implementation of: "Int32&nbsp;int32()"
     */
    public int int32$p(Ctx ctx) {
        return rnd().nextInt();
    }

    /**
     * Native implementation of: "Int64&nbsp;int64()"
     */
    public long int64$p(Ctx ctx) {
        return rnd().nextLong();
    }

    /**
     * Native implementation of "UInt8&nbsp;uint8()".
     */
    public int uint8$p(Ctx ctx) {
        return rnd().nextInt() & 0xFF;
    }

    /**
     * Native implementation of: "UInt16&nbsp;uint16()"
     */
    public int uint16$p(Ctx ctx) {
        return rnd().nextInt() & 0xFFFF;
    }

    /**
     * Native implementation of: "UInt32&nbsp;uint32()"
     */
    public int uint32$p(Ctx ctx) {
        return rnd().nextInt();
    }

    /**
     * Native implementation of: "UInt64&nbsp;uint64()"
     */
    public long uint64$p(Ctx ctx) {
        return rnd().nextLong();
    }

    /**
     * Native implementation of: "Dec64&nbsp;dec64()"
     */
    public long dec64$p(Ctx ctx) {
        return Dec64.$toLongBits(ctx, new BigDecimal(rnd().nextDouble(), MathContext.DECIMAL64));
    }

    /**
     * Native implementation of: "Float32&nbsp;float32()"
     */
    public float float32$p(Ctx ctx) {
        return rnd().nextFloat();
    }

    /**
     * Native implementation of: "Float64&nbsp;float64()"
     */
    public double float64$p(Ctx ctx) {
        return rnd().nextDouble();
    }

    // ------ helpers ------------------------------------------------------------------------------

    /**
     * Generate random bytes packed into longs, used by {@link #bits$p} and {@link #bytes$p}.
     */
    private long[] $randomLongs(int byteCount) {
        byte[] bytes = new byte[byteCount];
        rnd().nextBytes(bytes);
        long[] longs = new long[(byteCount + 7) >>> 3];

        // bit arrays are stored most-significant-bit first, including a partial final word
        for (int i = 0; i < bytes.length; i++) {
            longs[i >>> 3] |= (bytes[i] & 0xFFL) << (56 - ((i & 7) << 3));
        }
        return longs;
    }

    // ------ injection support --------------------------------------------------------------------

    /**
     * Create an RTRandom.
     */
    public static RTRandom $create(java.lang.Object opts) {
        if (opts instanceof Int64 int64) {
            return new RTRandom(new Random(int64.$value));
        }

        if (opts instanceof IntLiteral lit) {
            return new RTRandom(new Random(lit.magnitude.toInt64$p(null, false, false)));
        }

        RTRandom random = THREAD_LOCAL;
        if (random != null) {
            return random;
        }

        synchronized (RTRandom.class) {
            random = THREAD_LOCAL;
            if (random == null) {
                random = THREAD_LOCAL = new RTRandom(null);
            }
            return random;
        }
    }

    static private RTRandom THREAD_LOCAL;
}
