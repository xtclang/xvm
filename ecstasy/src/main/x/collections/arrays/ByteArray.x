/**
 * Functionality specific to arrays of bytes.
 */
mixin ByteArray<Element extends Byte>
        into Array<Element>
        extends IntNumberArray<Element>
    {
    // ----- UTF-8 operations ----------------------------------------------------------------------

    import io.IllegalUTF;

    /**
     * Translate a series of bytes from the UTF-8 format into a single Unicode character.
     *
     * @param offset  (optional) the byte offset of the UTF-8 data within the byte array
     *
     * @return the character represented by the UTF-8 bytes from this byte array at the
     *         specified offset
     * @return the number of bytes used to represent the UTF-8 formatted character value
     *
     * @throws IllegalUTF  if the UTF-8 data was not valid
     */
    (Char ch, Int length) utf8Char(Int offset = 0)
        {
        Byte b = this[offset];
        if (b < 0x80)
            {
            // ASCII
            return b.toChar(), 1;
            }

        private UInt32 trailing(Int offset)
            {
            if (offset >= size)
                {
                throw new IllegalUTF($"missing trailing unicode byte at offset {offset}");
                }

            Byte b = this[offset];
            if (b & 0b11000000 != 0b10000000)
                {
                throw new IllegalUTF(
                    $"trailing unicode byte {b} at offset {offset} does not match 10xxxxxx");
                }

            return (b & 0b00111111).toUInt32();
            }

        UInt32 n = b.toUInt32();
        Int    len;
        switch ((~b).leftmostBit)
            {
            case 0b00100000:
                return (n & 0b00011111 << 6 | trailing(offset+1)).toChar(), 2;

            case 0b00010000:
                n = n & 0b00001111 << 6
                    | trailing(offset+1) << 6
                    | trailing(offset+2);
                len = 3;
                break;

            case 0b00001000:
                n = n & 0b00000111 << 6
                    | trailing(offset+1) << 6
                    | trailing(offset+2) << 6
                    | trailing(offset+3);
                len = 4;
                break;

            case 0b00000100:
                n = n & 0b00000011 << 6
                    | trailing(offset+1) << 6
                    | trailing(offset+2) << 6
                    | trailing(offset+3) << 6
                    | trailing(offset+4);
                len = 5;
                break;

            case 0b00000010:
                n = n & 0b00000001 << 6
                    | trailing(offset+1) << 6
                    | trailing(offset+2) << 6
                    | trailing(offset+3) << 6
                    | trailing(offset+4) << 6
                    | trailing(offset+5);
                len = 6;
                break;

            default:
                throw new IllegalUTF($"initial byte: {b}");
            }

        Char ch = n.toChar();
        if (ch.requiresTrailingSurrogate())
            {
            (Char ch2, Int len2) = utf8Char(offset + len);
            return ch.addTrailingSurrogate(ch2), len + len2;
            }

        return ch, len;
        }

    /**
     * Translate the byte array from the UTF-8 format into a String.
     *
     * @return the string represented by the UTF-8 bytes from this byte array
     *
     * @throws IllegalUTF  if the UTF-8 data was not valid
     */
    String unpackString()
        {
        Int byteCount = this.size;
        if (byteCount == 0)
            {
            return "";
            }

        Int          offset = 0;
        StringBuffer buf    = new StringBuffer(byteCount);
        while (offset < byteCount)
            {
            (Char ch, Int chLen) = utf8Char(offset);
            buf.add(ch);
            offset += chLen;
            }

        return buf.toString();
        }

    /**
     * Translate a series of bytes from the UTF-8 format into a String.
     *
     * Note that this requires an exact count of **characters** to be passed. If the number of
     * bytes is known, but not the number of characters, then slice this byte array to obtain
     * just the slice that contains the UTF-8 data, and use the no-parameter [unpackString()]
     * method to convert it to a String.
     *
     * @param index      the byte offset of the UTF-8 data within the byte array
     * @param charCount  the number of **characters** in the string
     *
     * @return the string represented by the UTF-8 bytes from this byte array at the
     *         specified offset
     * @return the offset immediately following the UTF-8 formatted string value
     *
     * @throws IllegalUTF  if the UTF-8 data was not valid
     */
    (String string, Int newIndex) unpackString(Int index, Int charCount)
        {
        if (charCount == 0)
            {
            return "", 0;
            }

        Char[] chars = new Char[charCount];
        Int    len   = 0;
        for (Int i = 0; i < charCount; ++i)
            {
            (Char ch, Int chLen) = utf8Char(index + len);
            chars[i] = ch;
            len     += chLen;
            }

        return new String(chars.freeze()), index + len;
        }


    // ----- packed integer operations -------------------------------------------------------------

    /**
     * Read a packed integer value from within the byte array at the specified offset, and
     * return the integer value and the offset immediately following the integer value.
     *
     * @param index  the index of the packed integer value
     *
     * @return the integer value
     * @return the index immediately following the packed integer value
     */
    (Int value, Int newIndex) unpackInt(Int index)
        {
        // use a signed byte to get auto sign-extension when converting to an int
        Int8 b = new Int8(this[index].toBitArray());

        // Tiny format: the first bit of the first byte is used to indicate a single byte format,
        // in which the entire value is contained in the 7 MSBs
        if (b & 0x01 != 0)
            {
            return (b >> 1), index + 1;
            }

        // Small and Medium formats are indicated by the second bit (and differentiated by the
        // third bit). Small format: bits 3..7 of the first byte are bits 8..12 of the result,
        // and the next byte provides bits 0..7 of the result. Medium format: bits 3..7 of the
        // first byte are bits 16..20 of the result, and the next byte provides bits 8..15 of
        // the result, and the next byte provides bits 0..7 of the result
        if (b & 0x02 != 0)
            {
            Int n = (b >> 3).toInt64() << 8 | this[index+1];

            // the third bit is used to indicate Medium format (a second trailing byte)
            return b & 0x04 != 0
                    ? (n << 8 | this[index+2], index + 3)
                    : (n, index + 2);
            }

        // Large format: the first two bits of the first byte are 0, so bits 2..7 of the
        // first byte are the trailing number of bytes minus 1
        Int byteCount = 1 + (b >>> 2);
        assert:bounds byteCount <= 8;

        Int  curOffset  = index + 1;
        Int  nextOffset = curOffset + byteCount;
        Int  n          = new Int8(this[curOffset++].toBitArray());     // sign-extend
        while (curOffset < nextOffset)
            {
            n = n << 8 | this[curOffset++];
            }
        return n, nextOffset;
        }


    // ----- view support --------------------------------------------------------------------------

    /**
     * Translator from an array of bytes to an array of any fixed-length numeric type.
     */
    private static class Translator<NumType extends Number>(ByteArray bytes)
            implements ArrayDelegate<NumType>
        {
        construct(ByteArray bytes)
            {
            this.bytes        = bytes;
            this.bytesPerNum := NumType.fixedByteLength();
            }

        private Byte[] bytes;
        private Int bytesPerNum;

        @Override
        Mutability mutability.get()
            {
            return bytes.mutability;
            }

        @Override
        Int capacity
            {
            @Override
            Int get()
                {
                return bytes.capacity / bytesPerNum;
                }

            @Override
            void set(Int c)
                {
                bytes.capacity = c * bytesPerNum;
                }
            }

        @Override
        Int size.get()
            {
            (Int nums, Int remain) = bytes.size /% bytesPerNum;
            assert remain == 0;
            return nums;
            }

        @Override
        Var<NumType> elementAt(Int index)
            {
            assert:bounds 0 <= index < size;

            return new Object()
                {
                NumType element
                    {
                    @Override
                    Boolean assigned.get()
                        {
                        return index < size;
                        }

                    @Override
                    NumType get()
                        {
                        assert:bounds index < size;
                        val offset = index * bytesPerNum;
                        return NumType.new(bytes[offset..offset+bytesPerNum));
                        }

                    @Override
                    void set(NumType num)
                        {
                        if (num != get())
                            {
                            Byte[] newBytes = num.toByteArray();
                            Int    offset   = index * bytesPerNum;
                            for (Int i : [0..bytesPerNum))
                                {
                                bytes[offset+i] = newBytes[i];
                                }
                            }
                        }
                    }
                }.&element;
            }

        @Override
        Translator insert(Int index, NumType value)
            {
            Byte[] newBytes = bytes.insertAll(index * bytesPerNum, value.toByteArray());
            return &bytes == &newBytes ? this : new Translator<NumType>(newBytes);
            }

        @Override
        Translator delete(Int index)
            {
            Int offset = index * bytesPerNum;
            Byte[] newBytes = bytes.deleteAll([offset..offset+bytesPerNum));
            return &bytes == &newBytes ? this : new Translator<NumType>(newBytes);
            }

        @Override
        NumType[] reify(Mutability? mutability = Null)
            {
            mutability ?:= this.mutability;
            return new NumType[size](i -> elementAt(i).get()).toArray(mutability, inPlace=True);
            }
        }

    /**
     * Obtain a _view_ of this array-of-bytes as an array-of-Int8 values. The resulting array has
     * the same mutability as this array. Conceptually, this is very similar to array delegation,
     * except that the Element type is different between the delegating array (the new Int8 array)
     * and the delegatee (this Byte array).
     *
     * @return an array of Int8 that acts as a read/write view of the contents of this byte array
     *
     * @throws OutOfBounds  if the byte array size is not evenly divisible by the size of an Int8
     */
    Int8[] asInt8Array()
        {
        return new Array<Int8>(new Translator<Int8>(this), mutability);
        }

    /**
     * Obtain a _view_ of this array-of-bytes as an array-of-Int16 values. The resulting array has
     * the same mutability as this array. Conceptually, this is very similar to array delegation,
     * except that the Element type is different between the delegating array (the new Int16 array)
     * and the delegatee (this Byte array).
     *
     * @return an array of Int16 that acts as a read/write view of the contents of this byte array
     *
     * @throws OutOfBounds  if the byte array size is not evenly divisible by the size of an Int16
     */
    Int16[] asInt16Array()
        {
        assert size % 2 == 0;
        return new Array<Int16>(new Translator<Int16>(this), mutability);
        }

    /**
     * Obtain a _view_ of this array-of-bytes as an array-of-Int32 values. The resulting array has
     * the same mutability as this array. Conceptually, this is very similar to array delegation,
     * except that the Element type is different between the delegating array (the new Int32 array)
     * and the delegatee (this Byte array).
     *
     * @return an array of Int32 that acts as a read/write view of the contents of this byte array
     *
     * @throws OutOfBounds  if the byte array size is not evenly divisible by the size of an Int32
     */
    Int32[] asInt32Array()
        {
        assert size % 4 == 0;
        return new Array<Int32>(new Translator<Int32>(this), mutability);
        }

    /**
     * Obtain a _view_ of this array-of-bytes as an array-of-Int64 values. The resulting array has
     * the same mutability as this array. Conceptually, this is very similar to array delegation,
     * except that the Element type is different between the delegating array (the new Int64 array)
     * and the delegatee (this Byte array).
     *
     * @return an array of Int64 that acts as a read/write view of the contents of this byte array
     *
     * @throws OutOfBounds  if the byte array size is not evenly divisible by the size of an Int64
     */
    Int64[] asInt64Array()
        {
        assert size % 8 == 0;
        return new Array<Int64>(new Translator<Int64>(this), mutability);
        }

    /**
     * Obtain a _view_ of this array-of-bytes as an array-of-Int128 values. The resulting array has
     * the same mutability as this array. Conceptually, this is very similar to array delegation,
     * except that the Element type is different between the delegating array (the new Int128 array)
     * and the delegatee (this Byte array).
     *
     * @return an array of Int128 that acts as a read/write view of the contents of this byte array
     *
     * @throws OutOfBounds  if the byte array size is not evenly divisible by the size of an Int128
     */
    Int128[] asInt128Array()
        {
        assert size % 16 == 0;
        return new Array<Int128>(new Translator<Int128>(this), mutability);
        }

    @Override
    Byte[] asByteArray()
        {
        // it is frustrating to have to create a new Byte[] when this is already a Byte[], but if
        // we do not do so, then we cannot reify to guarantee a copy
        return new Array<Byte>(this, mutability)
            {
            @Override
            Byte[] reify(Mutability? mutability = Null)
                {
                return new Array<Byte>(mutability ?: this.mutability, this);
                }
            };
        }

    /**
     * A second name for the [asByteArray] method, to assist with readability and uniformity.
     */
    static Method<ByteArray, <>, <Array<Byte>>> asUInt8Array = asByteArray;

    /**
     * Obtain a _view_ of this array-of-bytes as an array-of-UInt16 values. The resulting array has
     * the same mutability as this array. Conceptually, this is very similar to array delegation,
     * except that the Element type is different between the delegating array (the new UInt16 array)
     * and the delegatee (this Byte array).
     *
     * @return an array of UInt16 that acts as a read/write view of the contents of this byte array
     *
     * @throws OutOfBounds  if the byte array size is not evenly divisible by the size of a UInt16
     */
    UInt16[] asUInt16Array()
        {
        assert size % 2 == 0;
        return new Array<UInt16>(new Translator<UInt16>(this), mutability);
        }

    /**
     * Obtain a _view_ of this array-of-bytes as an array-of-UInt32 values. The resulting array has
     * the same mutability as this array. Conceptually, this is very similar to array delegation,
     * except that the Element type is different between the delegating array (the new UInt32 array)
     * and the delegatee (this Byte array).
     *
     * @return an array of UInt32 that acts as a read/write view of the contents of this byte array
     *
     * @throws OutOfBounds  if the byte array size is not evenly divisible by the size of a UInt32
     */
    UInt32[] asUInt32Array()
        {
        assert size % 4 == 0;
        return new Array<UInt32>(new Translator<UInt32>(this), mutability);
        }

    /**
     * Obtain a _view_ of this array-of-bytes as an array-of-UInt64 values. The resulting array has
     * the same mutability as this array. Conceptually, this is very similar to array delegation,
     * except that the Element type is different between the delegating array (the new UInt64 array)
     * and the delegatee (this Byte array).
     *
     * @return an array of UInt64 that acts as a read/write view of the contents of this byte array
     *
     * @throws OutOfBounds  if the byte array size is not evenly divisible by the size of a UInt64
     */
    UInt64[] asUInt64Array()
        {
        assert size % 8 == 0;
        return new Array<UInt64>(new Translator<UInt64>(this), mutability);
        }

    /**
     * Obtain a _view_ of this array-of-bytes as an array-of-UInt128 values. The resulting array has
     * the same mutability as this array. Conceptually, this is very similar to array delegation,
     * except that the Element type is different between the delegating array (the new UInt128
     * array) and the delegatee (this Byte array).
     *
     * @return an array of UInt128 that acts as a read/write view of the contents of this byte array
     *
     * @throws OutOfBounds  if the byte array size is not evenly divisible by the size of a UInt128
     */
    UInt128[] asUInt128Array()
        {
        assert size % 16 == 0;
        return new Array<UInt128>(new Translator<UInt128>(this), mutability);
        }

    /**
     * Obtain a _view_ of this array-of-bytes as an array-of-BFloat16 values. The resulting array
     * has the same mutability as this array. Conceptually, this is very similar to array
     * delegation, except that the Element type is different between the delegating array (the new
     * BFloat16 array) and the delegatee (this Byte array).
     *
     * @return an array of BFloat16 that acts as a read/write view of the contents of this byte
     *         array
     *
     * @throws OutOfBounds  if the byte array size is not evenly divisible by the size of a BFloat16
     */
    BFloat16[] asBFloat16Array()
        {
        assert size % 2 == 0;
        return new Array<BFloat16>(new Translator<BFloat16>(this), mutability);
        }

    /**
     * Obtain a _view_ of this array-of-bytes as an array-of-Float16 values. The resulting array has
     * the same mutability as this array. Conceptually, this is very similar to array delegation,
     * except that the Element type is different between the delegating array (the new Float16
     * array) and the delegatee (this Byte array).
     *
     * @return an array of Float16 that acts as a read/write view of the contents of this byte array
     *
     * @throws OutOfBounds  if the byte array size is not evenly divisible by the size of a Float16
     */
    Float16[] asFloat16Array()
        {
        assert size % 2 == 0;
        return new Array<Float16>(new Translator<Float16>(this), mutability);
        }

    /**
     * Obtain a _view_ of this array-of-bytes as an array-of-Float32 values. The resulting array has
     * the same mutability as this array. Conceptually, this is very similar to array delegation,
     * except that the Element type is different between the delegating array (the new Float32
     * array) and the delegatee (this Byte array).
     *
     * @return an array of Float32 that acts as a read/write view of the contents of this byte array
     *
     * @throws OutOfBounds  if the byte array size is not evenly divisible by the size of a Float32
     */
    Float32[] asFloat32Array()
        {
        assert size % 4 == 0;
        return new Array<Float32>(new Translator<Float32>(this), mutability);
        }

    /**
     * Obtain a _view_ of this array-of-bytes as an array-of-Float64 values. The resulting array has
     * the same mutability as this array. Conceptually, this is very similar to array delegation,
     * except that the Element type is different between the delegating array (the new Float64
     * array) and the delegatee (this Byte array).
     *
     * @return an array of Float64 that acts as a read/write view of the contents of this byte array
     *
     * @throws OutOfBounds  if the byte array size is not evenly divisible by the size of a Float64
     */
    Float64[] asFloat64Array()
        {
        assert size % 8 == 0;
        return new Array<Float64>(new Translator<Float64>(this), mutability);
        }

    /**
     * Obtain a _view_ of this array-of-bytes as an array-of-Float128 values. The resulting array
     * has the same mutability as this array. Conceptually, this is very similar to array
     * delegation, except that the Element type is different between the delegating array (the new
     * Float128 array) and the delegatee (this Byte array).
     *
     * @return an array of Float128 that acts as a read/write view of the contents of this byte
     *         array
     *
     * @throws OutOfBounds  if the byte array size is not evenly divisible by the size of a Float128
     */
    Float128[] asFloat128Array()
        {
        assert size % 16 == 0;
        return new Array<Float128>(new Translator<Float128>(this), mutability);
        }

    /**
     * Obtain a _view_ of this array-of-bytes as an array-of-Dec32 values. The resulting array has
     * the same mutability as this array. Conceptually, this is very similar to array delegation,
     * except that the Element type is different between the delegating array (the new Dec32
     * array) and the delegatee (this Byte array).
     *
     * @return an array of Dec32 that acts as a read/write view of the contents of this byte array
     *
     * @throws OutOfBounds  if the byte array size is not evenly divisible by the size of a Dec32
     */
    Dec32[] asDec32Array()
        {
        assert size % 4 == 0;
        return new Array<Dec32>(new Translator<Dec32>(this), mutability);
        }

    /**
     * Obtain a _view_ of this array-of-bytes as an array-of-Dec64 values. The resulting array has
     * the same mutability as this array. Conceptually, this is very similar to array delegation,
     * except that the Element type is different between the delegating array (the new Dec64
     * array) and the delegatee (this Byte array).
     *
     * @return an array of Dec64 that acts as a read/write view of the contents of this byte array
     *
     * @throws OutOfBounds  if the byte array size is not evenly divisible by the size of a Dec64
     */
    Dec64[] asDec64Array()
        {
        assert size % 8 == 0;
        return new Array<Dec64>(new Translator<Dec64>(this), mutability);
        }

    /**
     * Obtain a _view_ of this array-of-bytes as an array-of-Dec128 values. The resulting array has
     * the same mutability as this array. Conceptually, this is very similar to array delegation,
     * except that the Element type is different between the delegating array (the new Dec128
     * array) and the delegatee (this Byte array).
     *
     * @return an array of Dec128 that acts as a read/write view of the contents of this byte array
     *
     * @throws OutOfBounds  if the byte array size is not evenly divisible by the size of a Dec128
     */
    Dec128[] asDec128Array()
        {
        assert size % 16 == 0;
        return new Array<Dec128>(new Translator<Dec128>(this), mutability);
        }


    // ----- conversions ---------------------------------------------------------------------------

    @Override
    Int8 toInt8()
        {
        // note: skips the assertion on the exact integer length that is performed by NumberArray
        return asBitArray().toInt8();
        }

    @Override
    Int16 toInt16()
        {
        // note: skips the assertion on the exact integer length that is performed by NumberArray
        return asBitArray().toInt16();
        }

    @Override
    Int32 toInt32()
        {
        // note: skips the assertion on the exact integer length that is performed by NumberArray
        return asBitArray().toInt32();
        }

    @Override
    Int64 toInt64()
        {
        // note: skips the assertion on the exact integer length that is performed by NumberArray
        return asBitArray().toInt64();
        }

    @Override
    Int128 toInt128()
        {
        // note: skips the assertion on the exact integer length that is performed by NumberArray
        return asBitArray().toInt128();
        }

    @Override
    UInt8 toUInt8()
        {
        // note: skips the assertion on the exact integer length that is performed by NumberArray
        return asBitArray().toUInt8();
        }

    @Override
    UInt16 toUInt16()
        {
        // note: skips the assertion on the exact integer length that is performed by NumberArray
        return asBitArray().toUInt16();
        }

    @Override
    UInt32 toUInt32()
        {
        // note: skips the assertion on the exact integer length that is performed by NumberArray
        return asBitArray().toUInt32();
        }

    @Override
    UInt64 toUInt64()
        {
        // note: skips the assertion on the exact integer length that is performed by NumberArray
        return asBitArray().toUInt64();
        }

    @Override
    UInt128 toUInt128()
        {
        // note: skips the assertion on the exact integer length that is performed by NumberArray
        return asBitArray().toUInt128();
        }

    /**
     * Obtain an immutable copy of this array's byte data that exposes the underlying data as Int8
     * values, each composed of 1 Byte value.
     *
     * @param mutability  the desired mutability for the resulting array, defaulting to constant
     *
     * @return an array of Int8 composed of the bytes in this array
     *
     * @throws OutOfBounds  if the byte array size is not evenly divisible by the size of an Int8
     */
    Int8[] toInt8Array(Mutability mutability = Constant)
        {
        return asInt8Array().reify(mutability);
        }

    /**
     * Obtain an immutable copy of this array's byte data that exposes the underlying data as Int16
     * values, each composed of 2 Byte values.
     *
     * @param mutability  the desired mutability for the resulting array, defaulting to constant
     *
     * @return an array of Int16 composed of the bytes in this array
     *
     * @throws OutOfBounds  if the byte array size is not evenly divisible by the size of an Int16
     */
    Int16[] toInt16Array(Mutability mutability = Constant)
        {
        return asInt16Array().reify(mutability);
        }

    /**
     * Obtain an immutable copy of this array's byte data that exposes the underlying data as Int32
     * values, each composed of 4 Byte values.
     *
     * @param mutability  the desired mutability for the resulting array, defaulting to constant
     *
     * @return an array of Int32 composed of the bytes in this array
     *
     * @throws OutOfBounds  if the byte array size is not evenly divisible by the size of an Int32
     */
    Int32[] toInt32Array(Mutability mutability = Constant)
        {
        return asInt32Array().reify(mutability);
        }

    /**
     * Obtain an immutable copy of this array's byte data that exposes the underlying data as Int64
     * values, each composed of 8 Byte values.
     *
     * @param mutability  the desired mutability for the resulting array, defaulting to constant
     *
     * @return an array of Int64 composed of the bytes in this array
     *
     * @throws OutOfBounds  if the byte array size is not evenly divisible by the size of an Int64
     */
    Int64[] toInt64Array(Mutability mutability = Constant)
        {
        return asInt64Array().reify(mutability);
        }

    /**
     * Obtain an immutable copy of this array's byte data that exposes the underlying data as Int128
     * values, each composed of 16 Byte values.
     *
     * @param mutability  the desired mutability for the resulting array, defaulting to constant
     *
     * @return an array of Int128 composed of the bytes in this array
     *
     * @throws OutOfBounds  if the byte array size is not evenly divisible by the size of an Int128
     */
    Int128[] toInt128Array(Mutability mutability = Constant)
        {
        return asInt128Array().reify(mutability);
        }

    /**
     * A second name for the [toByteArray] method, to assist with readability and uniformity.
     */
    static Method<NumberArray, <Mutability>, <Array<Byte>>> toUInt8Array = toByteArray;

    /**
     * Obtain an immutable copy of this array's byte data that exposes the underlying data as UInt16
     * values, each composed of 2 Byte values.
     *
     * @param mutability  the desired mutability for the resulting array, defaulting to constant
     *
     * @return an array of UInt16 composed of the bytes in this array
     *
     * @throws OutOfBounds  if the byte array size is not evenly divisible by the size of a UInt16
     */
    UInt16[] toUInt16Array(Mutability mutability = Constant)
        {
        return asUInt16Array().reify(mutability);
        }

    /**
     * Obtain an immutable copy of this array's byte data that exposes the underlying data as UInt32
     * values, each composed of 4 Byte values.
     *
     * @param mutability  the desired mutability for the resulting array, defaulting to constant
     *
     * @return an array of UInt32 composed of the bytes in this array
     *
     * @throws OutOfBounds  if the byte array size is not evenly divisible by the size of a UInt32
     */
    UInt32[] toUInt32Array(Mutability mutability = Constant)
        {
        return asUInt32Array().reify(mutability);
        }

    /**
     * Obtain an immutable copy of this array's byte data that exposes the underlying data as UInt64
     * values, each composed of 8 Byte values.
     *
     * @param mutability  the desired mutability for the resulting array, defaulting to constant
     *
     * @return an array of UInt64 composed of the bytes in this array
     *
     * @throws OutOfBounds  if the byte array size is not evenly divisible by the size of a UInt64
     */
    UInt64[] toUInt64Array(Mutability mutability = Constant)
        {
        return asUInt64Array().reify(mutability);
        }

    /**
     * Obtain an immutable copy of this array's byte data that exposes the underlying data as
     * UInt128 values, each composed of 16 Byte values.
     *
     * @param mutability  the desired mutability for the resulting array, defaulting to constant
     *
     * @return an array of UInt128 composed of the bytes in this array
     *
     * @throws OutOfBounds  if the byte array size is not evenly divisible by the size of a UInt128
     */
    UInt128[] toUInt128Array(Mutability mutability = Constant)
        {
        return asUInt128Array().reify(mutability);
        }

    /**
     * Obtain an immutable copy of this array's byte data that exposes the underlying data as
     * BFloat16 values, each composed of 2 Byte values.
     *
     * @param mutability  the desired mutability for the resulting array, defaulting to constant
     *
     * @return an array of BFloat16 composed of the bytes in this array
     *
     * @throws OutOfBounds  if the byte array size is not evenly divisible by the size of a BFloat16
     */
    BFloat16[] toBFloat16Array(Mutability mutability = Constant)
        {
        return asBFloat16Array().reify(mutability);
        }

    /**
     * Obtain an immutable copy of this array's byte data that exposes the underlying data as
     * Float16 values, each composed of 2 Byte values.
     *
     * @param mutability  the desired mutability for the resulting array, defaulting to constant
     *
     * @return an array of Float16 composed of the bytes in this array
     *
     * @throws OutOfBounds  if the byte array size is not evenly divisible by the size of a Float16
     */
    Float16[] toFloat16Array(Mutability mutability = Constant)
        {
        return asFloat16Array().reify(mutability);
        }

    /**
     * Obtain an immutable copy of this array's byte data that exposes the underlying data as
     * Float32 values, each composed of 4 Byte values.
     *
     * @param mutability  the desired mutability for the resulting array, defaulting to constant
     *
     * @return an array of Float32 composed of the bytes in this array
     *
     * @throws OutOfBounds  if the byte array size is not evenly divisible by the size of a Float32
     */
    Float32[] toFloat32Array(Mutability mutability = Constant)
        {
        return asFloat32Array().reify(mutability);
        }

    /**
     * Obtain an immutable copy of this array's byte data that exposes the underlying data as
     * Float64 values, each composed of 8 Byte values.
     *
     * @param mutability  the desired mutability for the resulting array, defaulting to constant
     *
     * @return an array of Float64 composed of the bytes in this array
     *
     * @throws OutOfBounds  if the byte array size is not evenly divisible by the size of a Float64
     */
    Float64[] toFloat64Array(Mutability mutability = Constant)
        {
        return asFloat64Array().reify(mutability);
        }

    /**
     * Obtain an immutable copy of this array's byte data that exposes the underlying data as
     * Float128 values, each composed of 16 Byte values.
     *
     * @param mutability  the desired mutability for the resulting array, defaulting to constant
     *
     * @return an array of Float128 composed of the bytes in this array
     *
     * @throws OutOfBounds  if the byte array size is not evenly divisible by the size of a Float128
     */
    Float128[] toFloat128Array(Mutability mutability = Constant)
        {
        return asFloat128Array().reify(mutability);
        }

    /**
     * Obtain an immutable copy of this array's byte data that exposes the underlying data as Dec32
     * values, each composed of 4 Byte values.
     *
     * @param mutability  the desired mutability for the resulting array, defaulting to constant
     *
     * @return an array of Dec32 composed of the bytes in this array
     *
     * @throws OutOfBounds  if the byte array size is not evenly divisible by the size of a Dec32
     */
    Dec32[] toDec32Array(Mutability mutability = Constant)
        {
        return asDec32Array().reify(mutability);
        }

    /**
     * Obtain an immutable copy of this array's byte data that exposes the underlying data as Dec64
     * values, each composed of 8 Byte values.
     *
     * @param mutability  the desired mutability for the resulting array, defaulting to constant
     *
     * @return an array of Dec64 composed of the bytes in this array
     *
     * @throws OutOfBounds  if the byte array size is not evenly divisible by the size of a Dec64
     */
    Dec64[] toDec64Array(Mutability mutability = Constant)
        {
        return asDec64Array().reify(mutability);
        }

    /**
     * Obtain an immutable copy of this array's byte data that exposes the underlying data as Dec128
     * values, each composed of 16 Byte values.
     *
     * @param mutability  the desired mutability for the resulting array, defaulting to constant
     *
     * @return an array of Dec128 composed of the bytes in this array
     *
     * @throws OutOfBounds  if the byte array size is not evenly divisible by the size of a Dec128
     */
    Dec128[] toDec128Array(Mutability mutability = Constant)
        {
        return asDec128Array().reify(mutability);
        }


    // ----- Stringable methods --------------------------------------------------------------------

    @Override
    Int estimateStringLength()
        {
        return 2 + size*2;
        }

    @Override
    Appender<Char> appendTo(Appender<Char> buf)
        {
        // TODO GG - make NibbleArray work
        // return asNibbleArray().appendTo(buf);
        return super(buf);
        }
    }
