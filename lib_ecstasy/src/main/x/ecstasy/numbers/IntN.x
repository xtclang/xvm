/**
 * A signed integer (using twos-complement) with a variable number of bytes.
 */
const IntN
        extends IntNumber
        default(0) {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a variable-length signed integer number from its bitwise machine representation.
     *
     * @param bits  an array of bit values that represent this number, ordered from left-to-right,
     *              Most Significant Bit (MSB) to Least Significant Bit (LSB)
     */
    @Override
    construct(Bit[] bits) {
        if (bits.size & 0b111 != 0) {
            // force the bit array to be a size that can be divided into bytes
            bits = bits.toIntN().bits;
        }
        super(bits);
    }

    /**
     * Construct a variable-length signed integer number from its network-portable representation.
     *
     * @param bytes  an array of byte values that represent this number, ordered from left-to-right,
     *               as they would appear on the wire or in a file
     */
    @Override
    construct(Byte[] bytes) {
        assert bytes.size >= 1;
        super(bytes);
    }

    /**
     * Construct a variable-sized signed integer number from its `String` representation.
     *
     * @param text  an integer number, in text format
     */
    @Override
    construct(String text) {
        construct IntN(new IntLiteral(text).toIntN().bits);
    }


    // ----- Numeric funky interface ---------------------------------------------------------------

    @Override
    static IntN zero() {
        return 0;
    }

    @Override
    static IntN one() {
        return 1;
    }


    // ----- properties ----------------------------------------------------------------------------

    @Override
    Signum sign.get() {
        return switch (this <=> 0) {
            case Lesser : Negative;
            case Equal  : Zero;
            case Greater: Positive;
        };
    }

    @Override
    UIntN magnitude.get() {
        return abs().toUIntN();
    }


    // ----- operations ----------------------------------------------------------------------------

    @Override
    @Op("-#")
    IntN neg() {
        return ~this + 1;
    }

    @Override
    @Op("+")
    IntN add(IntN! n) {
        return this + n;
    }

    @Override
    @Op("-")
    IntN sub(IntN! n) {
        return this + ~n + 1;
    }

    @Override
    @Op("*")
    IntN mul(IntN! n) {
        return this * n;
    }

    @Override
    @Op("/")
    IntN div(IntN! n) {
        return this / n;
    }

    @Override
    @Op("%")
    IntN mod(IntN! n) {
        return this % n;
    }

    @Override
    IntN abs() {
        return this < 0 ? -this : this;
    }

    @Override
    IntN pow(IntN! n) {
        IntN result = 1;

        while (n-- > 0) {
            result *= this;
        }

        return result;
    }


    // ----- Sequential interface ------------------------------------------------------------------

    @Override
    conditional IntN next() {
        return True, this + 1;
    }

    @Override
    conditional IntN prev() {
        return True, this - 1;
    }


    // ----- conversions ---------------------------------------------------------------------------

    /**
     * Sign-extended least significant bits.
     */
    private Bit[] signedLSBs(Int desired) {
        Bit[] bits   = this.bits;
        Int   actual = bits.size;
        Int   short  = desired - actual;
        return switch (short.sign) {
            case Negative: bits[actual-desired ..< actual];
            case Zero:     bits;
            case Positive: new Bit[desired](i -> i < short ? bits[0] : bits[i-short]);
        };
    }

    @Override
    Int8 toInt8(Boolean checkBounds = False) {
        assert:bounds !checkBounds || this >= Int8.MinValue && this <= Int8.MaxValue;
        return new Int8(signedLSBs(8));
    }

    @Override
    Int16 toInt16(Boolean checkBounds = False) {
        assert:bounds !checkBounds || this >= Int16.MinValue && this <= Int16.MaxValue;
        return new Int16(signedLSBs(16));
    }

    @Override
    Int32 toInt32(Boolean checkBounds = False) {
        assert:bounds !checkBounds || this >= Int32.MinValue && this <= Int32.MaxValue;
        return new Int32(signedLSBs(32));
    }

    @Override
    Int64 toInt64(Boolean checkBounds = False) {
        assert:bounds !checkBounds || this >= Int64.MinValue && this <= Int64.MaxValue;
        return new Int64(signedLSBs(64));
    }

    @Override
    Int128 toInt128(Boolean checkBounds = False) {
        assert:bounds !checkBounds || this >= Int128.MinValue && this <= Int128.MaxValue;
        return new Int128(signedLSBs(128));
    }

    @Override
    IntN toIntN() = this;

    /**
     * Zero-extended least significant bits.
     */
    private Bit[] unsignedLSBs(Int desired) {
        Bit[] bits   = this.bits;
        Int   actual = bits.size;
        Int   short  = desired - actual;
        return switch (short.sign) {
            case Negative: bits[actual-desired ..< actual];
            case Zero:     bits;
            case Positive: new Bit[desired](i -> i < short ? 0 : bits[i-short]);
        };
    }

    @Override
    UInt8 toUInt8(Boolean checkBounds = False) {
        assert:bounds !checkBounds || this >= UInt8.MinValue && this <= UInt8.MaxValue;
        return new UInt8(unsignedLSBs(8));
    }

    @Override
    UInt16 toUInt16(Boolean checkBounds = False) {
        assert:bounds !checkBounds || this >= UInt16.MinValue && this <= UInt16.MaxValue;
        return new UInt16(unsignedLSBs(16));
    }

    @Override
    UInt32 toUInt32(Boolean checkBounds = False) {
        assert:bounds !checkBounds || this >= UInt32.MinValue && this <= UInt32.MaxValue;
        return new UInt32(unsignedLSBs(32));
    }

    @Override
    UInt64 toUInt64(Boolean checkBounds = False) {
        assert:bounds !checkBounds || this >= UInt64.MinValue && this <= UInt64.MaxValue;
        return new UInt64(unsignedLSBs(64));
    }

    @Override
    UInt128 toUInt128(Boolean checkBounds = False) {
        assert:bounds !checkBounds || this >= UInt128.MinValue && this <= UInt128.MaxValue;
        return new UInt128(unsignedLSBs(128));
    }
}