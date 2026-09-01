package org.xtclang.ecstasy.numbers;

import org.xtclang.ecstasy.AppenderᐸCharᐳ;
import org.xtclang.ecstasy.Exception;

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
        return String.of(ctx, Character.toString(toChar$p($value, ctx)));
    }

    /**
     * The primitive implementation of:
     *     String toString()
     */
    public static String toString$p(int thi$, Ctx ctx) {
        return String.of(ctx, Character.toString(toChar$p(thi$, ctx)));
    }

    /**
     * The primitive implementation of:
     *     Int estimateStringLength()
     */
    public static long estimateStringLength$p(int thi$, Ctx ctx) {
        return 1;
    }

    /**
     * The native implementation of:
     *     Appender<Char> appendTo(Appender<Char> buf)
     */
    public AppenderᐸCharᐳ appendTo(Ctx ctx, AppenderᐸCharᐳ appender) {
        return Nibble.appendTo$p($value, ctx, appender);
    }

    /**
     * The primitive implementation of:
     *     Appender<Char> appendTo(Appender<Char> buf)
     */
    public static AppenderᐸCharᐳ appendTo$p(int thi$, Ctx ctx, AppenderᐸCharᐳ appender) {
        return appender.add$p(ctx, toChar$p(thi$, ctx));
    }

    /**
     * The primitive implementation of:
     *     Char toChar()
     */
    public static int toChar$p(int thi$, Ctx ctx) {
        return thi$ <= 9 ? '0' + thi$ : 'A' + thi$ - 0xA;
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
     *     static Nibble of(Int n)
     */
    public static int of$p(Ctx ctx, long n) {
        if (n < 0 || n > 15) {
            throw Exception.$illegalArg(ctx, "\"0 <= n <= 0xF\": n=" + n);
        }
        return (int) n;
    }

    /**
     * The primitive implementation of:
     *     static Nibble of(Char ch)
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
        throw Exception.$illegalArg(ctx, msg);
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

    // ----- debugging support ---------------------------------------------------------------------

    @Override public java.lang.String toString() {
        return "Nibble:" + Integer.toUnsignedString($value);
    }
}
