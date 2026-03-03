package org.xtclang.ecstasy.numbers;

import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;

import org.xtclang.ecstasy.OutOfBounds;
import org.xtclang.ecstasy.eBoolean;
import org.xtclang.ecstasy.nEnum;

import org.xtclang.ecstasy.reflect.Enumeration;

import org.xtclang.ecstasy.text.String;

import org.xvm.asm.ConstantPool;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.javajit.Ctx;

/**
 * Native FPNumber wrapper.
 */
public abstract class FPNumber extends Number {

    // ----- JIT methods ---------------------------------------------------------------------------

    /**
     * @return true iff the sign bit is set
     */
    public boolean $isSigned() {
        throw new UnsupportedOperationException();
    }

    /**
     * Convert this decimal to a Java {@link BigInteger}.
     * <p>
     * If the {@code direction} parameter is {@code null}, a default direction value of
     * {@link Rounding.TowardZero} will be used. This is
     *
     * @param direction  an optional {@link Rounding} direction to use
     *
     * @return this decimal converted to a Java {@link BigInteger}
     */
    public BigInteger $toBigInteger(Rounding direction) {
        if (direction == null) {
            direction = Rounding.TowardZero.$INSTANCE;
        }
        return $toBigDecimal().setScale(0, direction.$roundingMode()).toBigInteger();
    }

    // ----- conversion ----------------------------------------------------------------------------

    /**
     * Implementation of the Dec32 method
     * <pre>
     * toInt8(Boolean checkBounds = False, Rounding direction = TowardZero)
     * </pre>
     *
     * @param ctx              the build context
     * @param checkBounds      the check bounds flag
     * @param dfltCheckBounds  if {@code true} ignore the checkBounds parameter and use the
     *                         default value (in this case False)
     * @param direction        an optional direction for rounding, if not specified, the default
     *                         direction of TowardZero will be used
     *
     * @return this Dec32 value converted to an Int8 value inside a Java {@code int}
     */
    public int toInt8$p(Ctx ctx, boolean checkBounds, boolean dfltCheckBounds, Rounding direction) {
        BigInteger bi   = $toBigInteger(direction);
        int        bits = bi.bitLength();
        if (!dfltCheckBounds && checkBounds && (bits >= Byte.SIZE)) {
            OutOfBounds oob = new OutOfBounds(ctx);
            throw oob.$init(ctx, "Dec32 value " + $toBigDecimal().toEngineeringString()
                    + " is not a valid Int8 value");
        }
        return bi.byteValue();
    }

    /**
     * Implementation of the Dec32 method
     * <pre>
     * toInt16(Boolean checkBounds = False, Rounding direction = TowardZero)
     * </pre>
     *
     * @param ctx              the build context
     * @param checkBounds      the check bounds flag
     * @param dfltCheckBounds  if {@code true} ignore the checkBounds parameter and use the
     *                         default value (in this case False)
     * @param direction        an optional direction for rounding, if not specified, the default
     *                         direction of TowardZero will be used
     *
     * @return this Dec32 value converted to an Int16 value inside a Java {@code int}
     */
    public int toInt16$p(Ctx ctx, boolean checkBounds, boolean dfltCheckBounds, Rounding direction) {
        BigInteger bi   = $toBigInteger(direction);
        int        bits = bi.bitLength();
        if (!dfltCheckBounds && checkBounds && (bits >= Short.SIZE)) {
            OutOfBounds oob = new OutOfBounds(ctx);
            throw oob.$init(ctx, "Dec32 value " + $toBigDecimal().toEngineeringString()
                    + " is not a valid Int16 value");
        }
        return bi.shortValue();
    }

    /**
     * Implementation of the Dec32 method
     * <pre>
     * toInt32(Boolean checkBounds = False, Rounding direction = TowardZero)
     * </pre>
     *
     * @param ctx              the build context
     * @param checkBounds      the check bounds flag
     * @param dfltCheckBounds  if {@code true} ignore the checkBounds parameter and use the
     *                         default value (in this case False)
     * @param direction        an optional direction for rounding, if not specified, the default
     *                         direction of TowardZero will be used
     *
     * @return this Dec32 value converted to an Int32 value inside a Java {@code int}
     */
    public int toInt32$p(Ctx ctx, boolean checkBounds, boolean dfltCheckBounds, Rounding direction) {
        BigInteger bi   = $toBigInteger(direction);
        int        bits = bi.bitLength();
        if (!dfltCheckBounds && checkBounds && (bits >= Integer.SIZE)) {
            OutOfBounds oob = new OutOfBounds(ctx);
            throw oob.$init(ctx, "Dec32 value " + $toBigDecimal().toEngineeringString()
                    + " is not a valid Int32 value");
        }
        return bi.intValue();
    }

    /**
     * Implementation of the Dec32 method
     * <pre>
     * toInt64(Boolean checkBounds = False, Rounding direction = TowardZero)
     * </pre>
     *
     * @param ctx              the build context
     * @param checkBounds      the check bounds flag
     * @param dfltCheckBounds  if {@code true} ignore the checkBounds parameter and use the
     *                         default value (in this case False)
     * @param direction        an optional direction for rounding, if not specified, the default
     *                         direction of TowardZero will be used
     *
     * @return this Dec32 value converted to an Int64 value inside a Java {@code long}
     */
    public long toInt64$p(Ctx ctx, boolean checkBounds, boolean dfltCheckBounds,
                          Rounding direction) {
        BigInteger bi   = $toBigInteger(direction);
        int        bits = bi.bitLength();
        if (!dfltCheckBounds && checkBounds && (bits >= Long.SIZE)) {
            OutOfBounds oob = new OutOfBounds(ctx);
            throw oob.$init(ctx, "Dec32 value " + $toBigDecimal().toEngineeringString()
                    + " is not a valid Int64 value");
        }
        return bi.longValue();
    }

    /**
     * Implementation of the Dec32 method
     * <pre>
     * toInt128(Boolean checkBounds = False, Rounding direction = TowardZero)
     * </pre>
     * The high 64-bits of the Int128 will be stored in the {@link Ctx#i0} field, and the low
     * 64-bits will be returned inside a Java {@code long}.
     *
     * @param ctx              the build context
     * @param checkBounds      the check bounds flag
     * @param dfltCheckBounds  if {@code true} ignore the checkBounds parameter and use the
     *                         default value (in this case False)
     * @param direction        an optional direction for rounding, if not specified, the default
     *                         direction of TowardZero will be used
     *
     * @return the low 64-bits of this Dec32 value converted to an Int128 value inside a
     *         Java {@code long}
     */
    public long toInt128$p(Ctx      ctx,
                           boolean  checkBounds,
                           boolean  dfltCheckBounds,
                           Rounding direction) {

        BigInteger bi   = $toBigInteger(direction);
        int        bits = bi.bitLength();
        if (!dfltCheckBounds && checkBounds && (bits >= 128)) {
            OutOfBounds oob = new OutOfBounds(ctx);
            throw oob.$init(ctx, "Dec32 value " + $toBigDecimal().toEngineeringString()
                    + " is not a valid Int128 value");
        }
        return Int128.$fromBigInteger(ctx, bi);
    }

    /**
     * Implementation of the Dec32 method
     * <pre>
     * toUInt8(Boolean checkBounds = False, Rounding direction = TowardZero)
     * </pre>
     *
     * @param ctx              the build context
     * @param checkBounds      the check bounds flag
     * @param dfltCheckBounds  if {@code true} ignore the checkBounds parameter and use the
     *                         default value (in this case False)
     * @param direction        an optional direction for rounding, if not specified, the default
     *                         direction of TowardZero will be used
     *
     * @return this Dec32 value converted to an UInt8 value inside a Java {@code int}
     */
    public int toUInt8$p(Ctx      ctx,
                         boolean  checkBounds,
                         boolean  dfltCheckBounds,
                         Rounding direction) {
        if ($isSigned()) {
            OutOfBounds oob = new OutOfBounds(ctx);
            throw oob.$init(ctx, "Dec32 value " + $toBigDecimal().toEngineeringString()
                    + " is not a valid UInt8 value");
        }
        BigInteger bi   = $toBigInteger(direction);
        int        bits = bi.bitLength();
        if (!dfltCheckBounds && checkBounds && (bits > Byte.SIZE)) {
            OutOfBounds oob = new OutOfBounds(ctx);
            throw oob.$init(ctx, "Dec32 value " + $toBigDecimal().toEngineeringString()
                    + " is not a valid UInt8 value");
        }
        return bi.intValue() & 0xFF;
    }

    /**
     * Implementation of the Dec32 method
     * <pre>
     * toUInt16(Boolean checkBounds = False, Rounding direction = TowardZero)
     * </pre>
     *
     * @param ctx              the build context
     * @param checkBounds      the check bounds flag
     * @param dfltCheckBounds  if {@code true} ignore the checkBounds parameter and use the
     *                         default value (in this case False)
     * @param direction        an optional direction for rounding, if not specified, the default
     *                         direction of TowardZero will be used
     *
     * @return this Dec32 value converted to an UInt16 value inside a Java {@code int}
     */
    public int toUInt16$p(Ctx      ctx,
                          boolean  checkBounds,
                          boolean  dfltCheckBounds,
                          Rounding direction) {
        if ($isSigned()) {
            OutOfBounds oob = new OutOfBounds(ctx);
            throw oob.$init(ctx, "Dec32 value " + $toBigDecimal().toEngineeringString()
                    + " is not a valid UInt8 value");
        }
        BigInteger bi   = $toBigInteger(direction);
        int        bits = bi.bitLength();
        if (!dfltCheckBounds && checkBounds && (bits > Short.SIZE)) {
            OutOfBounds oob = new OutOfBounds(ctx);
            throw oob.$init(ctx, "Dec32 value " + $toBigDecimal().toEngineeringString()
                    + " is not a valid UInt16 value");
        }
        return bi.shortValue() & 0xFFFF;
    }

    /**
     * Implementation of the Dec32 method
     * <pre>
     * toUInt32(Boolean checkBounds = False, Rounding direction = TowardZero)
     * </pre>
     *
     * @param ctx              the build context
     * @param checkBounds      the check bounds flag
     * @param dfltCheckBounds  if {@code true} ignore the checkBounds parameter and use the
     *                         default value (in this case False)
     * @param direction        an optional direction for rounding, if not specified, the default
     *                         direction of TowardZero will be used
     *
     * @return this Dec32 value converted to an UInt32 value inside a Java {@code int}
     */
    public int toUInt32$p(Ctx      ctx,
                          boolean  checkBounds,
                          boolean  dfltCheckBounds,
                          Rounding direction) {
        if ($isSigned()) {
            OutOfBounds oob = new OutOfBounds(ctx);
            throw oob.$init(ctx, "Dec32 value " + $toBigDecimal().toEngineeringString()
                    + " is not a valid UInt32 value");
        }
        BigInteger bi   = $toBigInteger(direction);
        int        bits = bi.bitLength();
        if (!dfltCheckBounds && checkBounds && (bits > Integer.SIZE)) {
            OutOfBounds oob = new OutOfBounds(ctx);
            throw oob.$init(ctx, "Dec32 value " + $toBigDecimal().toEngineeringString()
                    + " is not a valid UInt32 value");
        }
        return bi.intValue();
    }

    /**
     * Implementation of the Dec32 method
     * <pre>
     * toUInt64(Boolean checkBounds = False, Rounding direction = TowardZero)
     * </pre>
     *
     * @param ctx              the build context
     * @param checkBounds      the check bounds flag
     * @param dfltCheckBounds  if {@code true} ignore the checkBounds parameter and use the
     *                         default value (in this case False)
     * @param direction        an optional direction for rounding, if not specified, the default
     *                         direction of TowardZero will be used
     *
     * @return this Dec32 value converted to an UInt64 value inside a Java {@code long}
     */
    public long toUInt64$p(Ctx      ctx,
                           boolean  checkBounds,
                           boolean  dfltCheckBounds,
                           Rounding direction) {
        if ($isSigned()) {
            OutOfBounds oob = new OutOfBounds(ctx);
            throw oob.$init(ctx, "Dec32 value " + $toBigDecimal().toEngineeringString()
                    + " is not a valid UInt64 value");
        }
        BigInteger bi   = $toBigInteger(direction);
        int        bits = bi.bitLength();
        if (!dfltCheckBounds && checkBounds && (bits > Long.SIZE)) {
            OutOfBounds oob = new OutOfBounds(ctx);
            throw oob.$init(ctx, "Dec32 value " + $toBigDecimal().toEngineeringString()
                    + " is not a valid UInt64 value");
        }
        return bi.longValue();
    }

    /**
     * Implementation of the Dec32 method
     * <pre>
     * toUInt128(Boolean checkBounds = False, Rounding direction = TowardZero)
     * </pre>
     * The high 64-bits of the UInt128 will be stored in the {@link Ctx#i0} field, and the low
     * 64-bits will be returned inside a Java {@code long}.
     *
     * @param ctx              the build context
     * @param checkBounds      the check bounds flag
     * @param dfltCheckBounds  if {@code true} ignore the checkBounds parameter and use the
     *                         default value (in this case False)
     * @param direction        an optional direction for rounding, if not specified, the default
     *                         direction of TowardZero will be used
     *
     * @return the low 64-bits of this Dec32 value converted to an UInt128 value inside a
     *         Java {@code long}
     */
    public long toUInt128$p(Ctx      ctx,
                            boolean  checkBounds,
                            boolean  dfltCheckBounds,
                            Rounding direction) {
        if ($isSigned()) {
            OutOfBounds oob = new OutOfBounds(ctx);
            throw oob.$init(ctx, "Dec32 value " + $toBigDecimal().toEngineeringString()
                    + " is not a valid UInt128 value");
        }

        BigInteger bi   = $toBigInteger(direction);
        int        bits = bi.bitLength();
        if (!dfltCheckBounds && checkBounds && (bits > 128)) {
            OutOfBounds oob = new OutOfBounds(ctx);
            throw oob.$init(ctx, "Dec32 value " + $toBigDecimal().toEngineeringString()
                    + " is not a valid UInt128 value");
        }
        return UInt128.$fromBigInteger(ctx, bi);
    }

    // ----- inner class Rounding ------------------------------------------------------------------

    /**
     * Native FPNumber.Rounding enumeration wrapper.
     */
    public static abstract class Rounding
            extends nEnum {

        public Rounding(long ordinal, String name, RoundingMode mode) {
            super(null);
            $ordinal      = ordinal;
            $name         = name;
            $roundingMode = mode;
        }

        public final long $ordinal;

        public final String $name;

        private final RoundingMode $roundingMode;

        @Override public TypeConstant $xvmType(Ctx ctx) {
            ConstantPool pool = $xvm().ecstasyPool;
            return switch ((int) $ordinal) {
                case 0  -> pool.valTiesToEven()    .getType();
                case 1  -> pool.valTiesToAway()    .getType();
                case 2  -> pool.valTowardPositive().getType();
                case 3  -> pool.valTowardZero()    .getType();
                case 4  -> pool.valTowardNegative().getType();
                default -> throw new IllegalStateException();
            };
        }

        /**
         * @return the {@link MathContext} to use for this Rounding enumeration value
         */
        RoundingMode $roundingMode() {
            return $roundingMode;
        }

        @Override
        public Enumeration enumeration$get(Ctx ctx) {
            return eBoolean.$INSTANCE;
        }

        @Override
        public String name$get(Ctx ctx) {
            return $name;
        }

        @Override
        public long ordinal$get$p(Ctx ctx) {
            return $ordinal;
        }

        public static class TiesToEven extends Rounding {
            private TiesToEven() {
                super(0, String.of(null, "TiesToEven"), RoundingMode.HALF_EVEN);
            }
            public static TiesToEven $INSTANCE = new TiesToEven();
        }

        public static class TiesToAway extends Rounding {
            private TiesToAway() {
                super(1, String.of(null, "TiesToAway"), RoundingMode.UP);
            }
            public static TiesToAway $INSTANCE = new TiesToAway();
        }

        public static class TowardPositive extends Rounding {
            private TowardPositive() {
                super(2, String.of(null, "TowardPositive"), RoundingMode.CEILING);
            }
            public static TowardPositive $INSTANCE = new TowardPositive();
        }

        public static class TowardZero extends Rounding {
            private TowardZero() {
                super(3, String.of(null, "TowardZero"), RoundingMode.DOWN);
            }
            public static TowardZero $INSTANCE = new TowardZero();
        }

        public static class TowardNegative extends Rounding {
            private TowardNegative() {
                super(4, String.of(null, "TowardNegative"), RoundingMode.FLOOR);
            }
            public static TowardNegative $INSTANCE = new TowardNegative();
        }
    }

    public static class eRounding extends Enumeration {
        private eRounding(Ctx ctx) {
            super(ctx);
        }

        public static final eRounding $INSTANCE = new eRounding(Ctx.get());

        public static final String[]  $names  = new String[] {
                Rounding.TiesToEven.$INSTANCE.$name,
                Rounding.TiesToAway.$INSTANCE.$name,
                Rounding.TowardPositive.$INSTANCE.$name,
                Rounding.TowardZero.$INSTANCE.$name,
                Rounding.TowardNegative.$INSTANCE.$name
        };

        public static final Rounding[] $values = new Rounding[] {
                Rounding.TiesToEven.$INSTANCE,
                Rounding.TiesToAway.$INSTANCE,
                Rounding.TowardPositive.$INSTANCE,
                Rounding.TowardZero.$INSTANCE,
                Rounding.TowardNegative.$INSTANCE
        };

        @Override public TypeConstant $xvmType(Ctx ctx) {
            ConstantPool pool = ctx.container.typeSystem.pool();
            return pool.ensureClassTypeConstant(pool.clzClass(), null, pool.typeRounding());
        }

        @Override
        public long count$get$p() {
            return 5;
        }

        @Override
        public String[] names$get() {
            return $names;
        }

        @Override
        public nEnum[] values$get() {
            return $values;
        }
    }
}
