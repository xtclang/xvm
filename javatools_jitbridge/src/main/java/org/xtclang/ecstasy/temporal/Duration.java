package org.xtclang.ecstasy.temporal;

import org.xtclang.ecstasy.nConst;

import org.xtclang.ecstasy.numbers.Int128;

import org.xvm.javajit.Ctx;

/**
 * Native wrapper for the two-slot XVM primitive representation of {@code temporal.Duration}.
 */
public class Duration
        extends nConst {
    public Duration(Ctx ctx) {
        super(ctx);
    }

    private Duration(long lowPicos, long highPicos) {
        super(null);

        picoseconds$0 = lowPicos;
        picoseconds$1 = highPicos;
    }

    /**
     * The primitive slots backing the Ecstasy {@code picoseconds} property.
     */
    public long picoseconds$0;
    public long picoseconds$1;

    /**
     * Box the low and high halves of a primitive Duration value.
     */
    public static Duration $box(long lowPicos, long highPicos) {
        return new Duration(lowPicos, highPicos);
    }

    /**
     * Native implementation of the compact {@code Duration(Int128 picoseconds)} constructor.
     */
    public static Duration $new$0$p(Ctx ctx, long lowPicos, long highPicos) {
        return $box(lowPicos, highPicos);
    }

    public Int128 picoseconds$get(Ctx ctx) {
        return Int128.$box(picoseconds$0, picoseconds$1);
    }

    public static long picoseconds$get$p(long thi$Lo, long thi$Hi, Ctx ctx) {
        ctx.i0 = thi$Hi;
        return thi$Lo;
    }

    /**
     * Compare two signed 128-bit picosecond counts.
     */
    public static int $compare(long low1, long high1, long low2, long high2) {
        return Int128.$compare(low1, high1, low2, high2);
    }

    /**
     * Test two primitive Duration values for equality.
     */
    public static boolean $equals(long low1, long high1, long low2, long high2) {
        return high1 == high2 && low1 == low2;
    }

}
