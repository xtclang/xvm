/**
 * Represents a value that can be converted to a floating point numeric value.
 */
interface FPConvertible {
    // ----- conversions ---------------------------------------------------------------------------

    /**
     * Convert the value to a 32-bit radix-10 (decimal) floating point number.
     *
     * @return a 32-bit decimal value
     *
     * @throws OutOfBounds  if the resulting value is out of the 32-bit decimal range
     */
    Dec32 toDec32() {
        return toDecN().toDec32();
    }

    /**
     * Convert the value to a 64-bit radix-10 (decimal) floating point number.
     *
     * @return a 64-bit decimal value
     *
     * @throws OutOfBounds  if the resulting value is out of the 64-bit decimal range
     */
    Dec64 toDec64() {
        return toDecN().toDec64();
    }

    /**
     * Convert the value to a 128-bit radix-10 (decimal) floating point number.
     *
     * @return a 128-bit decimal value
     *
     * @throws OutOfBounds  if the resulting value is out of the 128-bit decimal range
     */
    Dec128 toDec128() {
        return toDecN().toDec128();
    }

    /**
     * Convert the value to a variable-length decimal radix floating point number.
     *
     * @return a decimal value of variable length
     */
    DecN toDecN();

    /**
     * Convert the value to an 8-bit radix-2 (binary) "FP8 E4M3" floating point number.
     *
     * @return an 8-bit "E4M3" floating point number
     */
    Float8e4 toFloat8e4() {
        return toFloatN().toFloat8e4();
    }

    /**
     * Convert the value to an 8-bit radix-2 (binary) "FP8 E5M2" floating point number.
     *
     * @return an 8-bit "E5M2" floating point number
     */
    Float8e5 toFloat8e5() {
        return toFloatN().toFloat8e5();
    }

    /**
     * Convert the value to a 16-bit radix-2 (binary) "brain" floating point number.
     *
     * @return a 16-bit "brain" floating point number
     */
    BFloat16 toBFloat16() {
        return toFloatN().toBFloat16();
    }

    /**
     * Convert the value to a 16-bit radix-2 (binary) floating point number.
     *
     * @return a 16-bit binary floating point number
     */
    Float16 toFloat16() {
        return toFloatN().toFloat16();
    }

    /**
     * Convert the value to a 32-bit radix-2 (binary) floating point number.
     *
     * @return a 32-bit binary floating point number
     */
    Float32 toFloat32() {
        return toFloatN().toFloat32();
    }

    /**
     * Convert the value to a 64-bit radix-2 (binary) floating point number.
     *
     * @return a 64-bit binary floating point number
     */
    Float64 toFloat64() {
        return toFloatN().toFloat64();
    }

    /**
     * Convert the value to a 128-bit radix-2 (binary) floating point number.
     *
     * @return a 128-bit binary floating point number
     */
    Float128 toFloat128() {
        return toFloatN().toFloat128();
    }

    /**
     * Convert the value to a variable-length binary radix floating point number.
     *
     * @return a binary floating point of variable length
     */
    FloatN toFloatN();

    /**
     * Convert the value to an `FPLiteral` that represents the same value.
     *
     * @return a floating point literal
     */
    FPLiteral toFPLiteral() {
        return toDecN().toFPLiteral();
    }

    /**
     * Alias for the `toDec64()` method, since the `Dec64` type is aliased as `Dec`.
     */
    static Method<FPConvertible, <>, <Dec>> toDec = toDec64;

    /**
     * Alias for the `toFloat32()` method, since the `Float32` type is aliased as `Float`.
     */
    static Method<FPConvertible, <>, <Float>> toFloat = toFloat32;

    /**
     * Alias for the `toFloat64()` method, since the `Float64` type is aliased as `Double`.
     */
    static Method<FPConvertible, <>, <Double>> toDouble = toFloat64;
}
