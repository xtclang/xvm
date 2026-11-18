package org.xtclang.ecstasy.numbers;

import org.xtclang.ecstasy.AppenderᐸCharᐳ;
import org.xtclang.ecstasy.nConst;

import org.xtclang.ecstasy.text.String;

import org.xvm.javajit.Ctx;

/**
 * Native Bit wrapper.
 */
public class Bit extends nConst {
    /**
     * Construct an Ecstasy Bit object.
     *
     * @param value  the 1-bit value (0 or 1)
     */
    private Bit(int value) {
        super(null);
        $value = value;
    }

    private static final Bit[] CACHE = new Bit[2];

    public final int $value;

    public String toString(Ctx ctx, boolean async) {
        return String.of(ctx, false, Integer.toString($value));
    }

    public static String toString$p(int thi$, Ctx ctx, boolean async) {
        return String.of(ctx, false, Integer.toString(thi$));
    }

    public static long estimateStringLength$p(int thi$, Ctx ctx, boolean async) {
        return 1;
    }

    /**
     * The standard native implementation of the "private IntLiteral literal" property getter.
     */
    public IntLiteral literal$get(Ctx ctx, boolean async) {
        return literal$get$p($value, ctx, false);
    }

    /**
     * The optimized native implementation of the "private IntLiteral literal" property getter.
     */
    public static IntLiteral literal$get$p(int thi$, Ctx ctx, boolean async) {
        // TODO: implement the explicit IntLiteral conversion and reflection based access
        return null;
    }

    /**
     * The standard native implementation of "IntLiteral toIntLiteral()".
     */
    public IntLiteral toIntLiteral(Ctx ctx, boolean async) {
        return literal$get(ctx, false);
    }

    /**
     * The optimized native implementation of "IntLiteral toIntLiteral()".
     */
    public static IntLiteral toIntLiteral$p(int thi$, Ctx ctx, boolean async) {
        return literal$get$p(thi$, ctx, false);
    }

    /**
     * The optimized native implementation of "Boolean toBoolean()".
     */
    public static boolean toBoolean$p(int thi$, Ctx ctx, boolean async) {
        return thi$ != 0;
    }

    /**
     * The optimized native implementation of "Bit and(Bit! that)".
     */
    public static int and$p(int thi$, Ctx ctx, boolean async, int that) {
        return thi$ & that;
    }

    /**
     * The optimized native implementation of "Bit or(Bit! that)".
     */
    public static int or$p(int thi$, Ctx ctx, boolean async, int that) {
        return thi$ | that;
    }

    /**
     * The optimized native implementation of "Bit xor(Bit! that)".
     */
    public static int xor$p(int thi$, Ctx ctx, boolean async, int that) {
        return thi$ ^ that;
    }

    /**
     * The optimized native implementation of "Bit not()".
     */
    public static int not$p(int thi$, Ctx ctx, boolean async) {
        return thi$ == 0 ? 1 : 0;
    }

    /**
     * The optimized native implementation of {@code Appender<Char> appendTo(Appender<Char> buf)}.
     */
    public static AppenderᐸCharᐳ appendTo$p(int thi$, Ctx ctx, boolean async,
            AppenderᐸCharᐳ appender) {
        char c = thi$ == 0 ? '0' : '1';
        return appender.add$p(ctx, false, c);
    }

    /**
     * Obtain a Bit for a 1-bit "primitive" int (a Java "int" value).
     */
    public static Bit $box(int value) {
        Bit ref = CACHE[value = value & 0x1];
        if (ref == null) {
            CACHE[value] = ref = new Bit(value);
        }
        return ref;
    }

    // ----- conversion ----------------------------------------------------------------------------

    /**
     * The primitive implementation of Bit toBit()
     *
     * @param ctx              the build context
     * @param checkBounds      the check bounds flag
     * @param dfltCheckBounds  if {@code true} ignore the checkBounds parameter and use the
     *                         default value (in this case False)
     *
     * @return this Bit value as a Java {@code int}
     */
    public static int toBit$p(int thi$, Ctx ctx, boolean async, boolean checkBounds,
            boolean dfltCheckBounds) {
        return thi$;
    }

    /**
     * The primitive implementation of Byte toByte()
     *
     * @param ctx  the build context
     *
     * @return this Bit value as a Byte (a Java {@code int})
     */
    public static int toByte$p(int thi$, Ctx ctx, boolean async) {
        return thi$;
    }

    // ----- debugging support ---------------------------------------------------------------------

    @Override public java.lang.String toString() {
        return "Bit:" + $value;
    }
}
