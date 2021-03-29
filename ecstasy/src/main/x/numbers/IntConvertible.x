/**
 * Represents a value that can be converted to an integer or floating point numeric value.
 */
interface IntConvertible
        extends FPConvertible
    {
    // ----- conversions ---------------------------------------------------------------------------

    /**
     * Convert the value to an `IntLiteral` that represents the same value.
     *
     * @return an integer literal
     */
    IntLiteral toIntLiteral();

    /**
     * Convert the value to a signed 8-bit integer.
     *
     * @return a signed 8-bit integer
     *
     * @throws OutOfBounds  if the resulting value is out of the signed 8-bit integer range
     */
    Int8 toInt8()
        {
        return toIntN().toInt8();
        }

    /**
     * Convert the value to a signed 16-bit integer.
     *
     * @return a signed 16-bit integer
     *
     * @throws OutOfBounds  if the resulting value is out of the signed 16-bit integer range
     */
    Int16 toInt16()
        {
        return toIntN().toInt16();
        }

    /**
     * Convert the value to a signed 32-bit integer.
     *
     * @return a signed 32-bit integer
     *
     * @throws OutOfBounds  if the resulting value is out of the signed 32-bit integer range
     */
    Int32 toInt32()
        {
        return toIntN().toInt32();
        }

    /**
     * Convert the value to a signed 64-bit integer.
     *
     * @return a signed 64-bit integer
     *
     * @throws OutOfBounds  if the resulting value is out of the signed 64-bit integer range
     */
    Int64 toInt64()
        {
        return toIntN().toInt64();
        }

    /**
     * Convert the value to a signed 128-bit integer.
     *
     * @return a signed 128-bit integer
     *
     * @throws OutOfBounds  if the resulting value is out of the signed 128-bit integer range
     */
    Int128 toInt128()
        {
        return toIntN().toInt128();
        }

    /**
     * Convert the value to a variable-length signed integer.
     *
     * @return a signed integer of variable length
     */
    IntN toIntN();

    /**
     * Convert the value to an unsigned 8-bit integer.
     *
     * @return an unsigned 8-bit integer
     *
     * @throws OutOfBounds  if the resulting value is out of the unsigned 8-bit integer range
     */
    UInt8 toUInt8()
        {
        return toIntN().toUInt8();
        }

    /**
     * Convert the value to an unsigned 16-bit integer.
     *
     * @return an unsigned 16-bit integer
     *
     * @throws OutOfBounds  if the resulting value is out of the unsigned 16-bit integer range
     */
    UInt16 toUInt16()
        {
        return toIntN().toUInt16();
        }

    /**
     * Convert the value to an unsigned 32-bit integer.
     *
     * @return an unsigned 32-bit integer
     *
     * @throws OutOfBounds  if the resulting value is out of the unsigned 32-bit integer range
     */
    UInt32 toUInt32()
        {
        return toIntN().toUInt32();
        }

    /**
     * Convert the value to an unsigned 64-bit integer.
     *
     * @return an unsigned 64-bit integer
     *
     * @throws OutOfBounds  if the resulting value is out of the unsigned 64-bit integer range
     */
    UInt64 toUInt64()
        {
        return toIntN().toUInt64();
        }

    /**
     * Convert the value to an unsigned 128-bit integer.
     *
     * @return an unsigned 128-bit integer
     *
     * @throws OutOfBounds  if the resulting value is out of the unsigned 128-bit integer range
     */
    UInt128 toUInt128()
        {
        return toIntN().toUInt128();
        }

    /**
     * Convert the value to a variable-length unsigned integer.
     *
     * @return an unsigned integer of variable length
     */
    UIntN toUIntN()
        {
        return toIntN().toUIntN();
        }


    // ----- "slicing" -----------------------------------------------------------------------------

    /**
     * Obtain the least significant 8 bits of the integer value as a signed 8-bit integer.
     *
     * @return a signed 8-bit integer
     */
    Int8 sliceInt8()
        {
        Byte[] bytes = toIntN().toByteArray();
        return new Int8(bytes[bytes.size-1].toBitArray());
        }

    /**
     * Obtain the least significant 16 bits of the integer value as a signed 16-bit integer.
     *
     * @return a signed 16-bit integer
     */
    Int16 sliceInt16()
        {
        Byte[] bytes = toIntN().toByteArray();
        Int    max   = 2;
        Int    len   = bytes.size;
        Int    fill  = max - len;
        return new Int16(switch (fill.sign)
            {
            case Negative: bytes[len-max..len);
            case Zero    : bytes;
            case Positive: new Byte[max](i -> i < fill ? bytes[0].signExtend : bytes[i-fill]);
            });
        }

    /**
     * Obtain the least significant 32 bits of the integer value as a signed 32-bit integer.
     *
     * @return a signed 32-bit integer
     */
    Int32 sliceInt32()
        {
        Byte[] bytes = toIntN().toByteArray();
        Int    max   = 4;
        Int    len   = bytes.size;
        Int    fill  = max - len;
        return new Int32(switch (fill.sign)
            {
            case Negative: bytes[len-max..len);
            case Zero    : bytes;
            case Positive: new Byte[max](i -> i < fill ? bytes[0].signExtend : bytes[i-fill]);
            });
        }

    /**
     * Obtain the least significant 64 bits of the integer value as a signed 64-bit integer.
     *
     * @return a signed 64-bit integer
     */
    Int64 sliceInt64()
        {
        Byte[] bytes = toIntN().toByteArray();
        Int    max   = 8;
        Int    len   = bytes.size;
        Int    fill  = max - len;
        return new Int64(switch (fill.sign)
            {
            case Negative: bytes[len-max..len);
            case Zero    : bytes;
            case Positive: new Byte[max](i -> i < fill ? bytes[0].signExtend : bytes[i-fill]);
            });
        }

    /**
     * Obtain the least significant 128 bits of the integer value as a signed 128-bit integer.
     *
     * @return a signed 128-bit integer
     */
    Int128 sliceInt128()
        {
        Byte[] bytes = toIntN().toByteArray();
        Int    max   = 16;
        Int    len   = bytes.size;
        Int    fill  = max - len;
        return new Int128(switch (fill.sign)
            {
            case Negative: bytes[len-max..len);
            case Zero    : bytes;
            case Positive: new Byte[max](i -> i < fill ? bytes[0].signExtend : bytes[i-fill]);
            });
        }

    /**
     * Obtain the least significant 8 bits of the integer value as an unsigned 8-bit integer.
     *
     * @return an unsigned 8-bit integer
     */
    UInt8 sliceUInt8()
        {
        Byte[] bytes = toIntN().toByteArray();
        return bytes[bytes.size-1];
        }

    /**
     * Obtain the least significant 16 bits of the integer value as an unsigned 16-bit integer.
     *
     * @return an unsigned 16-bit integer
     */
    UInt16 sliceUInt16()
        {
        Byte[] bytes = toUIntN().toByteArray();
        Int    max   = 2;
        Int    len   = bytes.size;
        Int    fill  = max - len;
        return new UInt16(switch (fill.sign)
            {
            case Negative: bytes[len-max..len);
            case Zero    : bytes;
            case Positive: new Byte[max](i -> i < fill ? 0 : bytes[i-fill]);
            });
        }

    /**
     * Obtain the least significant 32 bits of the integer value as an unsigned 32-bit integer.
     *
     * @return an unsigned 32-bit integer
     */
    UInt32 sliceUInt32()
        {
        Byte[] bytes = toUIntN().toByteArray();
        Int    max   = 4;
        Int    len   = bytes.size;
        Int    fill  = max - len;
        return new UInt32(switch (fill.sign)
            {
            case Negative: bytes[len-max..len);
            case Zero    : bytes;
            case Positive: new Byte[max](i -> i < fill ? 0 : bytes[i-fill]);
            });
        }

    /**
     * Obtain the least significant 64 bits of the integer value as an unsigned 64-bit integer.
     *
     * @return an unsigned 64-bit integer
     */
    UInt64 sliceUInt64()
        {
        Byte[] bytes = toUIntN().toByteArray();
        Int    max   = 8;
        Int    len   = bytes.size;
        Int    fill  = max - len;
        return new UInt64(switch (fill.sign)
            {
            case Negative: bytes[len-max..len);
            case Zero    : bytes;
            case Positive: new Byte[max](i -> i < fill ? 0 : bytes[i-fill]);
            });
        }

    /**
     * Obtain the least significant 128 bits of the integer value as an unsigned 128-bit integer.
     *
     * @return an unsigned 128-bit integer
     */
    UInt128 sliceUInt128()
        {
        Byte[] bytes = toUIntN().toByteArray();
        Int    max   = 16;
        Int    len   = bytes.size;
        Int    fill  = max - len;
        return new UInt128(switch (fill.sign)
            {
            case Negative: bytes[len-max..len);
            case Zero    : bytes;
            case Positive: new Byte[max](i -> i < fill ? 0 : bytes[i-fill]);
            });
        }
    }
