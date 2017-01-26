/**
 * The Number interface represents the properties and operations available on every
 * numeric type included in Ecstasy.
 */
interface Number
    {
    // ----- properties

    /**
     * The number of bits that the number uses.
     */
    @ro Int bitLength;

    /**
     * The number of bytes that the number uses.
     */
    @ro Int byteLength;

    /**
     * The Sign of the number.
     */
    @ro Signum sign;

    // ----- operations

    /**
     * Addition: Add another number to this number, and return the result.
     */
    @op Number add(Number n);
    /**
     * Subtraction: Subtract another number from this number, and return the result.
     */
    @op Number sub(Number n);
    /**
     * Multiplication: Multiply this number by another number, and return the result.
     */
    @op Number mul(Number n);
    /**
     * Division: Divide this number by another number, and return the result.
     */
    @op Number div(Number n);
    /**
     * Modulo: Return the modulo that would result from dividing this number by another number.
     */
    @op Number mod(Number n);

    /**
     * Division and Modulo: Divide this number by another number, and return both the
     * quotient and the modulo.
     */
    @op (Number quotient, Number modulo) divmod(Number n)
        {
        return (this / n, this % n);
        }

    /**
     * Remainder: Return the remainder that would result from dividing this number by another
     * number. Note that the remainder is the same as the modulo for unsigned dividend values
     * and for signed dividend values that are zero or positive, but for signed dividend values
     * that are negative, the remainder will be zero or negative.
     */
    Number rem(Number n)
        {
        return this - (this / n * n);
        }

    /**
     * The absolute value of this number.
     */
    Number abs();
    /**
     * The negative of this number.
     */
    @op Number neg();
    /**
     * This number raised to the specified power.
     */
    Number pow(Number n);
    /**
     * The smaller of this number and the passed number.
     */
    Number min(Number n);
    /**
     * The larger of this number and the passed number.
     */
    Number max(Number n);

    // ----- conversions

    /**
     * Obtain the number as an array of bits.
     */
    Boolean[] to<Boolean[]>();

    /**
     * Obtain the number as an array of bits.
     */
    Bit[] to<Bit[]>();

    /**
     * Obtain the number as an array of bits.
     */
    Nibble[] to<Nibble[]>();

    /**
     * Obtain the number as an array of bytes.
     */
    Byte[] to<Byte[]>();

    /**
     * Convert the number to a variable-length signed integer.
     */
    VarInt to<VarInt>();

    /**
     * Convert the number to a signed 8-bit integer.
     * Any additional magnitude is discarded; any fractional value is discarded.
     */
    Int8 to<Int8>()
        {
        return to<VarInt>().to<Int8>();
        }

    /**
     * Convert the number to a signed 16-bit integer.
     * Any additional magnitude is discarded; any fractional value is discarded.
     */
    Int16 to<Int16>();
        {
        return to<VarInt>().to<Int16>();
        }

    /**
     * Convert the number to a signed 32-bit integer.
     * Any additional magnitude is discarded; any fractional value is discarded.
     */
    Int32 to<Int32>();
        {
        return to<VarInt>().to<Int32>();
        }

    /**
     * Convert the number to a signed 64-bit integer.
     * Any additional magnitude is discarded; any fractional value is discarded.
     */
    Int to<Int>();
        {
        return to<VarInt>().to<Int>();
        }

    /**
     * Convert the number to a signed 128-bit integer.
     * Any additional magnitude is discarded; any fractional value is discarded.
     */
    Int128 to<Int128>();
        {
        return to<VarInt>().to<Int128>();
        }

    /**
     * Convert the number to a variable-length unsigned integer.
     */
    VarUInt to<VarUInt>();

    /**
     * Convert the number to a unsigned 8-bit integer.
     * Any additional magnitude is discarded; any fractional value is discarded.
     */
    Byte to<Byte>()
        {
        return to<VarUInt>().to<Byte>();
        }

    /**
     * Convert the number to a unsigned 16-bit integer.
     * Any additional magnitude is discarded; any fractional value is discarded.
     */
    UInt16 to<UInt16>();
        {
        return to<VarUInt>().to<UInt16>();
        }

    /**
     * Convert the number to a unsigned 32-bit integer.
     * Any additional magnitude is discarded; any fractional value is discarded.
     */
    UInt32 to<UInt32>();
        {
        return to<VarUInt>().to<UInt32>();
        }

    /**
     * Convert the number to a unsigned 64-bit integer.
     * Any additional magnitude is discarded; any fractional value is discarded.
     */
    UInt to<UInt>();
        {
        return to<VarUInt>().to<UInt>();
        }

    /**
     * Convert the number to a unsigned 128-bit integer.
     * Any additional magnitude is discarded; any fractional value is discarded.
     */
    ULong to<ULong>();
        {
        return to<VarInt>().to<ULong>();
        }

    /**
     * Convert the number to a variable-length binary radix floating point number.
     */
    VarFloat to<VarFloat>();

    /**
     * Convert the number to a 16-bit radix-2 (binary) floating point number.
     */
    Float16 to<Float16>()
        {
        return to<VarFloat>().to<Float16>();
        }

    /**
     * Convert the number to a 32-bit radix-2 (binary) floating point number.
     */
    Float32 to<Float32>()
        {
        return to<VarFloat>().to<Float32>();
        }

    /**
     * Convert the number to a 64-bit radix-2 (binary) floating point number.
     */
    Float to<Float>()
        {
        return to<VarFloat>().to<Float>();
        }

    /**
     * Convert the number to a 128-bit radix-2 (binary) floating point number.
     */
    Double to<Double>()
        {
        return to<VarFloat>().to<Double>();
        }

    /**
     * Convert the number to a variable-length decimal radix floating point number.
     */
    VarDec to<VarDec>();

    /**
     * Convert the number to a 32-bit radix-10 (decimal) floating point number.
     */
    Dec32 to<Dec32>()
        {
        return to<VarDec>().to<Dec32>();
        }

    /**
     * Convert the number to a 64-bit radix-10 (decimal) floating point number.
     */
    Dec64 to<Dec64>()
        {
        return to<VarDec>().to<Dec64>();
        }

    /**
     * Convert the number to a 128-bit radix-10 (decimal) floating point number.
     */
    Dec128 to<Dec128>()
        {
        return to<VarDec>().to<Dec128>();
        }
    }
