import Number.Rounding;

/**
 * Represents a value that can be converted to an integer or floating point numeric value.
 */
interface IntConvertible
    {
    // ----- conversions ---------------------------------------------------------------------------

    /**
     * Convert the value to an `IntLiteral` that represents the same value.
     *
     * @param direction  the [Rounding] direction to use if rounding to an integer is necessary
     *
     * @return an integer literal
     */
    IntLiteral toIntLiteral(Rounding direction = TowardZero);

    /**
     * Convert the value to a signed 8-bit integer.
     *
     * @param truncate   pass `True` to silently truncate the integer value if necessary
     * @param direction  the [Rounding] direction to use if rounding to an integer is necessary
     *
     * @return a signed 8-bit integer
     *
     * @throws OutOfBounds  if the resulting value is out of the signed 8-bit integer range
     *                      and `truncate` is not `True`
     */
    Int8 toInt8(Boolean truncate = False, Rounding direction = TowardZero)
        {
        return toIntN(direction).toInt8(truncate);
        }

    /**
     * Convert the value to a signed 16-bit integer.
     *
     * @param truncate   pass `True` to silently truncate the integer value if necessary
     * @param direction  the [Rounding] direction to use if rounding to an integer is necessary
     *
     * @return a signed 16-bit integer
     *
     * @throws OutOfBounds  if the resulting value is out of the signed 16-bit integer range
     *                      and `truncate` is not `True`
     */
    Int16 toInt16(Boolean truncate = False, Rounding direction = TowardZero)
        {
        return toIntN(direction).toInt16(truncate);
        }

    /**
     * Convert the value to a signed 32-bit integer.
     *
     * @param truncate   pass `True` to silently truncate the integer value if necessary
     * @param direction  the [Rounding] direction to use if rounding to an integer is necessary
     *
     * @return a signed 32-bit integer
     *
     * @throws OutOfBounds  if the resulting value is out of the signed 32-bit integer range
     *                      and `truncate` is not `True`
     */
    Int32 toInt32(Boolean truncate = False, Rounding direction = TowardZero)
        {
        return toIntN(direction).toInt32(truncate);
        }

    /**
     * Convert the value to a signed 64-bit integer.
     *
     * @param truncate   pass `True` to silently truncate the integer value if necessary
     * @param direction  the [Rounding] direction to use if rounding to an integer is necessary
     *
     * @return a signed 64-bit integer
     *
     * @throws OutOfBounds  if the resulting value is out of the signed 64-bit integer range
     *                      and `truncate` is not `True`
     */
    Int64 toInt64(Boolean truncate = False, Rounding direction = TowardZero)
        {
        return toIntN(direction).toInt64(truncate);
        }

    /**
     * Convert the value to a signed 128-bit integer.
     *
     * @param truncate   pass `True` to silently truncate the integer value if necessary
     * @param direction  the [Rounding] direction to use if rounding to an integer is necessary
     *
     * @return a signed 128-bit integer
     *
     * @throws OutOfBounds  if the resulting value is out of the signed 128-bit integer range
     *                      and `truncate` is not `True`
     */
    Int128 toInt128(Boolean truncate = False, Rounding direction = TowardZero)
        {
        return toIntN(direction).toInt128(truncate);
        }

    /**
     * Convert the value to a variable-length signed integer.
     *
     * @param truncate   pass `True` to silently truncate the integer value if necessary
     * @param direction  the [Rounding] direction to use if rounding to an integer is necessary
     *
     * @param direction  the [Rounding] direction to use if rounding to an integer is necessary
     *
     * @return a signed integer of variable length
     *
     * @throws OutOfBounds  if the resulting value is out of the integer range supported by the
     *         variable-length signed integer type
     */
    IntN toIntN(Rounding direction = TowardZero);

    /**
     * Convert the value to an unsigned 8-bit integer.
     *
     * @param truncate   pass `True` to silently truncate the integer value if necessary
     * @param direction  the [Rounding] direction to use if rounding to an integer is necessary
     *
     * @return an unsigned 8-bit integer
     *
     * @throws OutOfBounds  if the resulting value is out of the unsigned 8-bit integer range
     *                      and `truncate` is not `True`
     */
    UInt8 toUInt8(Boolean truncate = False, Rounding direction = TowardZero)
        {
        return toIntN(direction).toUInt8(truncate);
        }

    /**
     * A second name for the [toUInt8] method, to assist with readability.
     */
    Byte toByte(Boolean truncate = False, Rounding direction = TowardZero)
        {
        return toUInt8(truncate, direction);
        }

    /**
     * Convert the value to an unsigned 16-bit integer.
     *
     * @param truncate   pass `True` to silently truncate the integer value if necessary
     * @param direction  the [Rounding] direction to use if rounding to an integer is necessary
     *
     * @return an unsigned 16-bit integer
     *
     * @throws OutOfBounds  if the resulting value is out of the unsigned 16-bit integer range
     *                      and `truncate` is not `True`
     */
    UInt16 toUInt16(Boolean truncate = False, Rounding direction = TowardZero)
        {
        return toIntN(direction).toUInt16(truncate);
        }

    /**
     * Convert the value to an unsigned 32-bit integer.
     *
     * @param truncate   pass `True` to silently truncate the integer value if necessary
     * @param direction  the [Rounding] direction to use if rounding to an integer is necessary
     *
     * @return an unsigned 32-bit integer
     *
     * @throws OutOfBounds  if the resulting value is out of the unsigned 32-bit integer range
     *                      and `truncate` is not `True`
     */
    UInt32 toUInt32(Boolean truncate = False, Rounding direction = TowardZero)
        {
        return toIntN(direction).toUInt32(truncate);
        }

    /**
     * Convert the value to an unsigned 64-bit integer.
     *
     * @param truncate   pass `True` to silently truncate the integer value if necessary
     * @param direction  the [Rounding] direction to use if rounding to an integer is necessary
     *
     * @return an unsigned 64-bit integer
     *
     * @throws OutOfBounds  if the resulting value is out of the unsigned 64-bit integer range
     *                      and `truncate` is not `True`
     */
    UInt64 toUInt64(Boolean truncate = False, Rounding direction = TowardZero)
        {
        return toIntN(direction).toUInt64(truncate);
        }

    /**
     * Convert the value to an unsigned 128-bit integer.
     *
     * @param truncate   pass `True` to silently truncate the integer value if necessary
     * @param direction  the [Rounding] direction to use if rounding to an integer is necessary
     *
     * @return an unsigned 128-bit integer
     *
     * @throws OutOfBounds  if the resulting value is out of the unsigned 128-bit integer range
     *                      and `truncate` is not `True`
     */
    UInt128 toUInt128(Boolean truncate = False, Rounding direction = TowardZero)
        {
        return toIntN(direction).toUInt128(truncate);
        }

    /**
     * Convert the value to a variable-length unsigned integer.
     *
     * @param direction  the [Rounding] direction to use if rounding to an integer is necessary
     *
     * @return an unsigned integer of variable length
     *
     * @throws OutOfBounds  if the resulting value is out of the integer range supported by the
     *         variable-length unsigned integer type
     */
    UIntN toUIntN(Rounding direction = TowardZero)
        {
        return toIntN(direction).toUIntN();
        }
    }
