/**
 * Represents a value that can be converted to an integer or floating point numeric value.
 */
interface IntConvertible {
    // ----- conversions ---------------------------------------------------------------------------

    /**
     * Convert the value to a signed 8-bit integer.
     *
     * @return a signed 8-bit integer
     *
     * @throws OutOfBounds  iff the resulting value is out of the signed 8-bit integer range
     */
    Int8 toInt8() = toInt64().toInt8(True);

    /**
     * Convert the value to a signed 16-bit integer.
     *
     * @return a signed 16-bit integer
     *
     * @throws OutOfBounds  iff the resulting value is out of the signed 16-bit integer range
     */
    Int16 toInt16() = toInt64().toInt16(True);

    /**
     * Convert the value to a signed 32-bit integer.
     *
     * @return a signed 32-bit integer
     *
     * @throws OutOfBounds  iff the resulting value is out of the signed 32-bit integer range
     */
    Int32 toInt32() = toInt64().toInt32(True);

    /**
     * Convert the value to a signed 64-bit integer.
     *
     * @return a signed 64-bit integer
     *
     * @throws OutOfBounds  iff the resulting value is out of the signed 64-bit integer range
     */
    Int64 toInt64() = toIntN().toInt64(True);

    /**
     * Convert the value to a signed 128-bit integer.
     *
     * @return a signed 128-bit integer
     *
     * @throws OutOfBounds  iff the resulting value is out of the signed 128-bit integer range
     */
    Int128 toInt128() = toIntN().toInt128(True);

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
     * @return an unsigned 8-bit integer
     *
     * @throws OutOfBounds  iff the resulting value is out of the unsigned 8-bit integer range
     */
    UInt8 toUInt8() = toUInt64().toUInt8(True);

    /**
     * Convert the value to an unsigned 16-bit integer.
     *
     * @return an unsigned 16-bit integer
     *
     * @throws OutOfBounds  iff the resulting value is out of the unsigned 16-bit integer range
     */
    UInt16 toUInt16() = toUInt64().toUInt16(True);

    /**
     * Convert the value to an unsigned 32-bit integer.
     *
     * @return an unsigned 32-bit integer
     *
     * @throws OutOfBounds  iff the resulting value is out of the unsigned 32-bit integer range
     */
    UInt32 toUInt32() = toUInt64().toUInt32(True);

    /**
     * Convert the value to an unsigned 64-bit integer.
     *
     * @return an unsigned 64-bit integer
     *
     * @throws OutOfBounds  iff the resulting value is out of the unsigned 64-bit integer range
     */
    UInt64 toUInt64() = toUIntN().toUInt64(True);

    /**
     * Convert the value to an unsigned 128-bit integer.
     *
     * @return an unsigned 128-bit integer
     *
     * @throws OutOfBounds  iff the resulting value is out of the unsigned 128-bit integer range
     */
    UInt128 toUInt128() = toUIntN().toUInt128(True);

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
     * @return an unsigned 8-bit integer
     *
     * @throws OutOfBounds  iff the resulting value is out of the unsigned 8-bit integer range
     */
    Byte toByte() = toUInt8();

    /**
     * Convert the value to a signed 64-bit integer.
     *
     * This is a second name for the [toInt64] method, to assist with readability.
     *
     * @return a signed 64-bit integer
     *
     * @throws OutOfBounds  iff the resulting value is out of the signed 64-bit integer range
     */
    Int toInt() = toInt64();

    /**
     * Convert the value to an unsigned 64-bit integer.
     *
     * This is a second name for the [toUInt64] method, to assist with readability.
     *
     * @return an unsigned 64-bit integer
     *
     * @throws OutOfBounds  iff the resulting value is out of the unsigned 64-bit integer range
     */
    UInt toUInt() = toUInt64();

    /**
     * Convert the value to an `IntLiteral` that represents the same integer value.
     *
     * @return an integer literal
     */
    IntLiteral toIntLiteral() = toIntN().toIntLiteral();
}