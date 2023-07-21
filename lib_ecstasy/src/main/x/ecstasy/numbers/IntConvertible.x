import Number.Rounding;

/**
 * Represents a value that can be converted to an integer or floating point numeric value.
 */
interface IntConvertible {
    // ----- conversions ---------------------------------------------------------------------------

    /**
     * Convert the value to a signed 8-bit integer.
     *
     * @param checkBounds  pass `True` to bounds-check this value before conversion, or `False` to
     *                     blindly retain only the necessary number of least significant bits, which
     *                     may lose magnitude or change the sign of the result, and then sign extend
     *                     if additional bits are required
     *
     * @return a signed 8-bit integer
     *
     * @throws OutOfBounds  iff `checkBounds` is `True` and the resulting value is out of the signed
     *                      8-bit integer range
     */
    Int8 toInt8(Boolean checkBounds = False) = toInt64(checkBounds).toInt8(checkBounds);

    /**
     * Convert the value to a signed 16-bit integer.
     *
     * @param checkBounds  pass `True` to bounds-check this value before conversion, or `False` to
     *                     blindly retain only the necessary number of least significant bits, which
     *                     may lose magnitude or change the sign of the result, and then sign extend
     *                     if additional bits are required
     *
     * @return a signed 16-bit integer
     *
     * @throws OutOfBounds  iff `checkBounds` is `True` and the resulting value is out of the signed
     *                      16-bit integer range
     */
    Int16 toInt16(Boolean checkBounds = False) = toInt64(checkBounds).toInt16(checkBounds);

    /**
     * Convert the value to a signed 32-bit integer.
     *
     * @param checkBounds  pass `True` to bounds-check this value before conversion, or `False` to
     *                     blindly retain only the necessary number of least significant bits, which
     *                     may lose magnitude or change the sign of the result, and then sign extend
     *                     if additional bits are required
     *
     * @return a signed 32-bit integer
     *
     * @throws OutOfBounds  iff `checkBounds` is `True` and the resulting value is out of the signed
     *                      32-bit integer range
     */
    Int32 toInt32(Boolean checkBounds = False) = toInt64(checkBounds).toInt32(checkBounds);

    /**
     * Convert the value to a signed 64-bit integer.
     *
     * @param checkBounds  pass `True` to bounds-check this value before conversion, or `False` to
     *                     blindly retain only the necessary number of least significant bits, which
     *                     may lose magnitude or change the sign of the result, and then sign extend
     *                     if additional bits are required
     *
     * @return a signed 64-bit integer
     *
     * @throws OutOfBounds  iff `checkBounds` is `True` and the resulting value is out of the signed
     *                      64-bit integer range
     */
    Int64 toInt64(Boolean checkBounds = False) = toInt64(checkBounds);

    /**
     * Convert the value to a signed 128-bit integer.
     *
     * @param checkBounds  pass `True` to bounds-check this value before conversion, or `False` to
     *                     blindly retain only the necessary number of least significant bits, which
     *                     may lose magnitude or change the sign of the result, and then sign extend
     *                     if additional bits are required
     *
     * @return a signed 128-bit integer
     *
     * @throws OutOfBounds  iff `checkBounds` is `True` and the resulting value is out of the signed
     *                      128-bit integer range
     */
    Int128 toInt128(Boolean checkBounds = False) = toIntN().toInt128(checkBounds);

    /**
     * Convert the value to a variable-length signed integer.
     *
     * @return a signed integer of variable length
     *
     * @throws OutOfBounds  if the resulting value is out of the integer range supported by the
     *                      variable-length signed integer type
     */
    IntN toIntN();

    /**
     * Convert the value to an unsigned 8-bit integer.
     *
     * @param checkBounds  pass `True` to bounds-check this value before conversion, or `False` to
     *                     blindly retain only the necessary number of least significant bits, which
     *                     may lose magnitude or change the sign of the result, and then zero extend
     *                     if additional bits are required
     *
     * @return an unsigned 8-bit integer
     *
     * @throws OutOfBounds  iff `checkBounds` is `True` and the resulting value is out of the
     *                      unsigned 8-bit integer range
     */
    UInt8 toUInt8(Boolean checkBounds = False) = toUInt64(checkBounds).toUInt8(checkBounds);

    /**
     * Convert the value to an unsigned 16-bit integer.
     *
     * @param checkBounds  pass `True` to bounds-check this value before conversion, or `False` to
     *                     blindly retain only the necessary number of least significant bits, which
     *                     may lose magnitude or change the sign of the result, and then zero extend
     *                     if additional bits are required
     *
     * @return an unsigned 16-bit integer
     *
     * @throws OutOfBounds  iff `checkBounds` is `True` and the resulting value is out of the
     *                      unsigned 8-bit integer range
     */
    UInt16 toUInt16(Boolean checkBounds = False) = toUInt64(checkBounds).toUInt16(checkBounds);

    /**
     * Convert the value to an unsigned 32-bit integer.
     *
     * @param checkBounds  pass `True` to bounds-check this value before conversion, or `False` to
     *                     blindly retain only the necessary number of least significant bits, which
     *                     may lose magnitude or change the sign of the result, and then zero extend
     *                     if additional bits are required
     *
     * @return an unsigned 32-bit integer
     *
     * @throws OutOfBounds  iff `checkBounds` is `True` and the resulting value is out of the
     *                      unsigned 8-bit integer range
     */
    UInt32 toUInt32(Boolean checkBounds = False) = toUInt64(checkBounds).toUInt32(checkBounds);

    /**
     * Convert the value to an unsigned 64-bit integer.
     *
     * @param checkBounds  pass `True` to bounds-check this value before conversion, or `False` to
     *                     blindly retain only the necessary number of least significant bits, which
     *                     may lose magnitude or change the sign of the result, and then zero extend
     *                     if additional bits are required
     *
     * @return an unsigned 64-bit integer
     *
     * @throws OutOfBounds  iff `checkBounds` is `True` and the resulting value is out of the
     *                      unsigned 8-bit integer range
     */
    UInt64 toUInt64(Boolean checkBounds = False) = toUIntN().toUInt64(checkBounds);

    /**
     * Convert the value to an unsigned 128-bit integer.
     *
     * @param checkBounds  pass `True` to bounds-check this value before conversion, or `False` to
     *                     blindly retain only the necessary number of least significant bits, which
     *                     may lose magnitude or change the sign of the result, and then zero extend
     *                     if additional bits are required
     *
     * @return an unsigned 128-bit integer
     *
     * @throws OutOfBounds  iff `checkBounds` is `True` and the resulting value is out of the
     *                      unsigned 8-bit integer range
     */
    UInt128 toUInt128(Boolean checkBounds = False) = toUIntN().toUInt128(checkBounds);

    /**
     * Convert the value to a variable-length unsigned integer.
     *
     * @return an unsigned integer of variable length
     *
     * @throws OutOfBounds  if the resulting value is out of the integer range supported by the
     *                      variable-length unsigned integer type
     */
    UIntN toUIntN() = toIntN().toUIntN();

    /**
     * Convert the value to an unsigned 8-bit integer.
     *
     * This is a second name for the [toUInt8] method, to assist with readability.
     *
     * @param checkBounds  pass `True` to bounds-check, or `False` to blindly retain only the
     *                     necessary number of least significant integer bits, which may lose
     *                     magnitude or change the sign of the result
     *
     * @return an unsigned 8-bit integer
     *
     * @throws OutOfBounds  iff `checkBounds` is `True` and the resulting value is out of the
     *                      unsigned 8-bit integer range
     */
    Byte toByte(Boolean checkBounds = False) = toUInt8(checkBounds);

    /**
     * Convert the value to a signed 64-bit integer.
     *
     * This is a second name for the [toInt64] method, to assist with readability.
     *
     * @param checkBounds  pass `True` to bounds-check, or `False` to blindly retain only the
     *                     necessary number of least significant integer bits, which may lose
     *                     magnitude or change the sign of the result
     *
     * @return a signed 64-bit integer
     *
     * @throws OutOfBounds  iff `checkBounds` is `True` and the resulting value is out of the signed
     *                      64-bit integer range
     */
    Int toInt(Boolean checkBounds = False) = toInt64(checkBounds);

    /**
     * Convert the value to an unsigned 64-bit integer.
     *
     * This is a second name for the [toUInt64] method, to assist with readability.
     *
     * @param checkBounds  pass `True` to bounds-check, or `False` to blindly retain only the
     *                     necessary number of least significant integer bits, which may lose
     *                     magnitude or change the sign of the result
     *
     * @return an unsigned 64-bit integer
     *
     * @throws OutOfBounds  iff `checkBounds` is `True` and the resulting value is out of the
     *                      unsigned 8-bit integer range
     */
    UInt toUInt(Boolean checkBounds = False) = toUInt64(checkBounds);

// TODO GG: toByte() toInt() toUInt() - will this work with optional arg?
//    /**
//     * Alias for the `toUInt8()` method, since the `UInt8` type is aliased as `Byte`.
//     */
//    static Method<IntConvertible, <Boolean>, <Byte>> toByte = toUInt8;
//
//    /**
//     * Alias for the `toInt64()` method, since the `Int64` type is aliased as `Int`.
//     */
//    static Method<IntConvertible, <Boolean>, <Int>> toInt = toInt64;
//
//    /**
//     * Alias for the `toUInt8()` method, since the `UInt64` type is aliased as `UInt`.
//     */
//    static Method<IntConvertible, <Boolean>, <UInt>> toUInt = toUInt64;

    /**
     * Convert the value to an `IntLiteral` that represents the same integer value.
     *
     * @return an integer literal
     */
    IntLiteral toIntLiteral() = toIntN().toIntLiteral();
}