package org.xtclang.ecstasy.numbers;

import org.xtclang.ecstasy.Exception;

import org.xtclang.ecstasy.collections.Array;
import org.xtclang.ecstasy.collections.ArrayᐸBitᐳ;
import org.xtclang.ecstasy.collections.ArrayᐸNibbleᐳ;
import org.xtclang.ecstasy.collections.ArrayᐸUInt8ᐳ;

import org.xtclang.ecstasy.nConst;

import org.xvm.javajit.Ctx;

/**
 * Native Number wrapper.
 */
public abstract class Number
        extends nConst
        implements FPConvertible {

    private ArrayᐸBitᐳ $bitArray = null;

    protected Number() {
        super(null);
    }

    /**
     * The native implementation of the Number.x bits property.
     * <pre>
     *     Bit[] bits;
     * </pre>
     */
    public ArrayᐸBitᐳ bits$get(Ctx ctx) {
        if ($bitArray == null) {
            long bits = bitLength$get$p();
            $bitArray = ArrayᐸBitᐳ.$fromLongs(ctx, Array.Mutability.Constant, bits, $longValues());
        }
        return $bitArray;
    }

    /**
     * The native implementation of the Number.x method:
     * <pre>
     *     Bit[] toBitArray(Array.Mutability mutability = Constant)
     * </pre>
     */
    public ArrayᐸBitᐳ toBitArray(Ctx ctx, Array.Mutability mutability) {
        long bits = bitLength$get$p();
        if (mutability == null || mutability == Array.Mutability.Constant) {
            return bits$get(ctx);
        }
        return ArrayᐸBitᐳ.$fromLongs(ctx, mutability, bits, $longValues());
    }

    /**
     * The native implementation of the Number.x method:
     * <pre>
     *     Bit[] toByteArray(Array.Mutability mutability = Constant)
     * </pre>
     */
    public ArrayᐸUInt8ᐳ toByteArray(Ctx ctx, Array.Mutability mutability) {
        long bits = bitLength$get$p();
        return ArrayᐸUInt8ᐳ.$fromLongs(ctx, mutability, bits, $longValues());
    }

    /**
     * The native implementation of the Number.x method:
     * <pre>
     *     Bit[] toNibbleArray(Array.Mutability mutability = Constant)
     * </pre>
     */
    public ArrayᐸNibbleᐳ toNibbleArray(Ctx ctx, Array.Mutability mutability) {
        long bits = bitLength$get$p();
        return ArrayᐸNibbleᐳ.$fromLongs(ctx, mutability, bits, $longValues());
    }

    /**
     * The native implementation of the Number.x property:
     * <pre>
     *     @RO Int bitLength.get()
     * </pre>
     */
    protected abstract long bitLength$get$p();

    /**
     * @return  the number as an array of longs.
     */
    protected abstract long[] $longValues();

    /**
     * Native support of IllegalMath exception.
     */
    public static class IllegalMath extends Exception {
        public IllegalMath(Ctx ctx) {
            super(ctx);
        }
    }

    /**
     * Native support of DivisionByZero exception.
     */
    public static class DivisionByZero extends IllegalMath {
        public DivisionByZero(Ctx ctx) {
            super(ctx);
        }
    }
}
