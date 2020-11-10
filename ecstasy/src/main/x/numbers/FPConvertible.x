/**
 * Represents a value that can be converted to a floating point numeric value.
 */
interface FPConvertible
    {
    // ----- conversions ---------------------------------------------------------------------------

    /**
     * Convert the value to an `FPLiteral` that represents the same value.
     *
     * @return a floating point literal
     */
    FPLiteral toFPLiteral();

    /**
     * Convert the value to a 16-bit radix-2 (binary) "brain" floating point number.
     *
     * @return a 16-bit "brain" floating point number
     */
    BFloat16 toBFloat16()
        {
        return toFloatN().toBFloat16();
        }

    /**
     * Convert the value to a 16-bit radix-2 (binary) floating point number.
     *
     * @return a 16-bit binary floating point number
     */
    Float16 toFloat16()
        {
        return toFloatN().toFloat16();
        }

    /**
     * Convert the value to a 32-bit radix-2 (binary) floating point number.
     *
     * @return a 32-bit binary floating point number
     */
    Float32 toFloat32()
        {
        return toFloatN().toFloat32();
        }

    /**
     * Convert the value to a 64-bit radix-2 (binary) floating point number.
     *
     * @return a 64-bit binary floating point number
     */
    Float64 toFloat64()
        {
        return toFloatN().toFloat64();
        }

    /**
     * Convert the value to a 128-bit radix-2 (binary) floating point number.
     *
     * @return a 128-bit binary floating point number
     */
    Float128 toFloat128()
        {
        return toFloatN().toFloat128();
        }

    /**
     * Convert the value to a variable-length binary radix floating point number.
     *
     * @return a binary floating point of variable length
     */
    FloatN toFloatN();

    /**
     * Convert the value to a 32-bit radix-10 (decimal) floating point number.
     *
     * @return a 32-bit decimal
     *
     * @throws OutOfBounds  if the resulting value is out of the 32-bit decimal range
     */
    Dec32 toDec32()
        {
        return toDecN().toDec32();
        }

    /**
     * Convert the value to a 64-bit radix-10 (decimal) floating point number.
     *
     * @return a 64-bit decimal
     *
     * @throws OutOfBounds  if the resulting value is out of the 64-bit decimal range
     */
    Dec64 toDec64()
        {
        return toDecN().toDec64();
        }

    /**
     * Convert the value to a 128-bit radix-10 (decimal) floating point number.
     *
     * @return a 128-bit decimal
     *
     * @throws OutOfBounds  if the resulting value is out of the 128-bit decimal range
     */
    Dec128 toDec128()
        {
        return toDecN().toDec128();
        }

    /**
     * Convert the value to a variable-length decimal radix floating point number.
     *
     * @return a decimal of variable length
     */
    DecN toDecN();

    // REVIEW GG
    Int8    roundToInt8(   FPNumber.Rounding direction = TowardZero);
    Int16   roundToInt16(  FPNumber.Rounding direction = TowardZero);
    Int32   roundToInt32(  FPNumber.Rounding direction = TowardZero);
    Int64   roundToInt64(  FPNumber.Rounding direction = TowardZero);
    Int128  roundToInt128( FPNumber.Rounding direction = TowardZero);
    IntN    roundToIntN(   FPNumber.Rounding direction = TowardZero);
    UInt8   roundToUInt8(  FPNumber.Rounding direction = TowardZero);
    UInt16  roundToUInt16( FPNumber.Rounding direction = TowardZero);
    UInt32  roundToUInt32( FPNumber.Rounding direction = TowardZero);
    UInt64  roundToUInt64( FPNumber.Rounding direction = TowardZero);
    UInt128 roundToUInt128(FPNumber.Rounding direction = TowardZero);
    UIntN   roundToUIntN(  FPNumber.Rounding direction = TowardZero);
    }
