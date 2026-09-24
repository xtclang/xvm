package org.xtclang._native.numbers;

import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

import org.xtclang.ecstasy.Exception;
import org.xtclang.ecstasy.nService;

import org.xtclang.ecstasy.collections.ArrayᐸBitᐳ;

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
        if (size <= 0) {
            throw Exception.$oob(ctx, "not positive");
        }

        if (size > 2_000_000_000L) {
            throw Exception.$oob(ctx, "size limit (2 billion bits) exceeded: " + size);
        }

        byte[] bytes = new byte[(int) ((size+7)>>>3)];
        rnd().nextBytes(bytes);
        long[] longs = new long[(int) ((size + 63) >>> 6)];

        // bit arrays are stored most-significant-bit first, including a partial final word
        for (int i = 0; i < bytes.length; i++) {
            longs[i >>> 3] |= (bytes[i] & 0xFFL) << (56 - ((i & 7) << 3));
        }
        return new ArrayᐸBitᐳ(ctx, ctx.pool().typeBitArray(), longs, size);
    }

    /**
     * Native implementation of "Int&nbsp;int(Int max)".
     *
     * Copied from xRTRandom.java.
     */
    public long int$p(Ctx ctx, long max) {
        Random rnd = rnd();

        if (max <= 0) {
            throw Exception.$oob(ctx, "not positive");
        }

        if (max <= Integer.MAX_VALUE) {
            // it's a 32-bit random, so take a fast path in Java that handles 32-bit values
            return rnd.nextInt((int) max);
        } else if ((max & (max-1)) == 0) {
            // it's a power of 2, so avoid the 64-bit modulo
            return rnd.nextLong() & (max - 1);
        } else {
            // this works in theory, but has a slightly weaker guarantee on a perfect distribution
            // of random values
            return (rnd.nextLong() % max) & ~Long.MIN_VALUE;
        }
    }

    /**
     * Native implementation of "Int8&nbsp;int8()".
     */
    public int int8$p(Ctx ctx) {
        return rnd().nextInt();
    }

    /**
     * Native implementation of "UInt8&nbsp;uint8()".
     */
    public int uint8$p(Ctx ctx) {
        return rnd().nextInt() & 0xFF;
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
