package org.xtclang.ecstasy.numbers;

import org.bouncycastle.util.Exceptions;

import org.xtclang.ecstasy.collections.Array;
import org.xtclang.ecstasy.collections.ArrayᐸNibbleᐳ;

import org.xtclang.ecstasy.text.String;

import org.xvm.javajit.Ctx;

/**
 * Native Nibble wrapper.
 */
public class Nibble extends UIntNumber {
    /**
     * Construct an Ecstasy Nibble object.
     *
     * @param value  the 4-bit value (0-15)
     */
    private Nibble(int value) {
        $value = value;
    }

    private static final Nibble[] CACHE = new Nibble[16];

    public final int $value;

    @Override
    public String toString(Ctx ctx) {
        return String.of(ctx, Integer.toUnsignedString($value));
    }

    public static String toString$p(int thi$, Ctx ctx) {
        return String.of(ctx, Integer.toUnsignedString(thi$));
    }

    /**
     * Obtain a Nibble for a 4-bit "primitive" int (a Java "int" value).
     *
     * @param value  a 4-bit "primitive" int
     *
     * @return a Nibble reference
     */
    public static Nibble $box(int value) {
        Nibble ref = CACHE[value = value & 0xF];
        if (ref == null) {
            CACHE[value] = ref = new Nibble(value);
        }
        return ref;
    }

    /**
     * The primitive implementation of:
     * <pre>
     *     static Nibble of(Int n)
     * </pre>
     */
    public static int of$p(Ctx ctx, long n) {
        if (n < 0 || n > 15) {
            throw Exceptions.illegalArgumentException("Int value " + n + " must be in the range 0..15", null);
        }
        return (int) n;
    }

    /**
     * The primitive implementation of:
     * <pre>
     *     static Nibble of(Char ch)
     * </pre>
     */
    public static int of$1$p(Ctx ctx, int ch) {
        if (ch >= '0' && ch <= '9') {
            return ch - '0';
        } else if (ch >= 'A' && ch <= 'F') {
            return ch - 'A' + 0xA;
        } else if (ch >= 'a' && ch <= 'f') {
            return ch - 'a' + 0xA;
        }
        java.lang.String msg = "Illegal character \"" + ch + "; the character value must be in " +
                               "the range \"0..9\", \"A..F\", or \"a..f\"";
        throw Exceptions.illegalArgumentException(msg, null);
    }

    /**
     * Native implementation of: "private static Nibble[] values = [0, ..., 15]"
     *
     * The naturally compiled initializer currently erases the array literal's element type to
     * Object, resulting in an invalid attempt to pass an optimized Nibble value to Array.add(Object).
     */
    public static ArrayᐸNibbleᐳ values$init(Ctx ctx) {
        return ArrayᐸNibbleᐳ.$fromLongs(ctx, Array.Mutability.Constant.$INSTANCE, 64,
                0x0123_4567_89AB_CDEFL);
    }

    @Override
    protected long[] $longValues() {
        return new long[]{(long) $value << 60};
    }

    @Override
    protected long bitLength$get$p() {
        return 4;
    }

    // ----- conversion ----------------------------------------------------------------------------

    /**
     * The primitive implementation of:
     * <pre>
     *     IntN toIntN()
     * </pre>
     */
    public static IntN toIntN$p(int thi$, Ctx ctx) {
        return IntN.$box(((long) thi$) & 0x0FL);
    }

    /**
     * The primitive implementation of:
     * <pre>
     *     IntN toIntN()
     * </pre>
     */
    public IntN toIntN(Ctx ctx) {
        return toIntN$p($value, ctx);
    }

    /**
     * The primitive implementation of:
     * <pre>
     *     UInt toUInt()
     * </pre>
     */
    public static UIntN toUIntN$p(int thi$, Ctx ctx) {
        return UIntN.$box(((long) thi$) & 0x0FL);
    }

    /**
     * The primitive implementation of:
     * <pre>
     *     UIntN toUIntN()
     * </pre>
     */
    public UIntN toUIntN(Ctx ctx) {
        return toUIntN$p($value, ctx);
    }

    // ----- debugging support ---------------------------------------------------------------------

    @Override public java.lang.String toString() {
        return "Nibble:" + Integer.toUnsignedString($value);
    }
}
