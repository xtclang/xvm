package org.xtclang.ecstasy;

import org.xtclang.ecstasy.text.String;

import org.xvm.asm.constants.TypeConstant;
import org.xvm.javajit.Ctx;

/**
 * All Ecstasy `enum` types must extend this class.
 *
 * <p>Some methods here are defined by hand, even though they could be generated. This is necessary
 * because the file name implies "no modification" by the augmenting builder.
 */
public abstract class nEnum
        extends nConst
        implements Object {
    public nEnum(Ctx ctx) {
        super(ctx);
    }

    @Override
    public TypeConstant $xvmType(Ctx ctx) {
        throw new UnsupportedOperationException("Must be generated");
    }

    abstract public String name$get(Ctx ctx);
    abstract public long ordinal$get$p(Ctx ctx);

    /**
     * Note: While this follows the general pattern of equals() functions, it does not exist in
     * the "Enum.x" source file. This implementation requires that the caller is certain that the
     * two enum values are members of the same enumeration.
     */
    static public boolean equals$p(Ctx ctx, nType CompileType, nEnum o1, nEnum o2) {
        return o1.ordinal$get$p(ctx) == o2.ordinal$get$p(ctx);
    }

    /**
     * Note: While this follows the general pattern of compare() functions, it does not exist in
     * the "Enum.x" source file. This implementation requires that the caller is certain that the
     * two enum values are members of the same enumeration.
     */
    static public long compare$p(Ctx ctx, nType CompileType, nEnum o1, nEnum o2) {
        return o1.ordinal$get$p(ctx) - o2.ordinal$get$p(ctx);
    }

    /**
     * Native implementation of Enum.x
     * <pre>{@code
     *     Int estimateStringLength() = name.size;
     * }</pre>
     */
    public long estimateStringLength$p(Ctx ctx) {
        return name$get(ctx).size$get$p(ctx);
    }

    /**
     * Native implementation of Enum.x
     * <pre>{@code
     *     Appender<Char> appendTo(Appender<Char> buf) = name.appendTo(buf);
     * }</pre>
     */
    public AppenderᐸCharᐳ appendTo(Ctx ctx, AppenderᐸCharᐳ appender) {
        return name$get(ctx).appendTo(ctx, appender);
    }

    @Override
    public java.lang.String toString() {
        return name$get(null).toString();
    }

    /**
     * A helper method for the Enum skip method for int JIT primitive enums.
     */
    public static int $skip(int thi$, Ctx ctx, long steps, int valueCount) {
        if (steps == 0) {
            return thi$;
        }
        long result = (long) thi$ + steps;
        if (result < 0L || result >= (long) valueCount) {
            throw Exception.$oob(ctx, "steps is out of bounds");
        }
        return (int) result;
    }

    /**
     * A helper method for the Enum skip method Enums based on the Int64 ordinal and also for long
     * JIT primitive enums.
     */
    public static long $skip(long thi$, Ctx ctx, long steps, int valueCount) {
        if (steps == 0) {
            return thi$;
        }
        long result = thi$ + steps;
        if (result < 0L || result >= (long) valueCount) {
            throw Exception.$oob(ctx, "steps is out of bounds");
        }
        return result;
    }
}
