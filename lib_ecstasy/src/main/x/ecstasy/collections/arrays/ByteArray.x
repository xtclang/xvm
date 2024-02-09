/**
 * Functionality specific to arrays of bytes.
 */
mixin ByteArray<Element extends Byte>
        into Array<Element>
        extends IntNumberArray<Element> {
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
    (Char ch, Int length) utf8Char(Int offset = 0) {
        Byte b = this[offset];
        if (b < 0x80) {
            // ASCII
            return b.toChar(), 1;
        }

        private UInt32 trailing(Int offset) {
            if (offset >= size) {
                throw new IllegalUTF($"missing trailing unicode byte at offset {offset}");
            }

            Byte b = this[offset];
            if (b & 0b11000000 != 0b10000000) {
                throw new IllegalUTF(
                    $"trailing unicode byte {b} at offset {offset} does not match 10xxxxxx");
            }

            return (b & 0b00111111).toUInt32();
        }

        UInt32 n = b.toUInt32();
        Int    len;
        switch ((~b).leftmostBit) {
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
        if (ch.requiresTrailingSurrogate()) {
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
    String unpackUtf8() {
        Int byteCount = this.size;
        if (byteCount == 0) {
            return "";
        }

        Int          offset = 0;
        StringBuffer buf    = new StringBuffer(byteCount);
        while (offset < byteCount) {
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
     * just the slice that contains the UTF-8 data, and use the no-parameter [unpackUtf8()]
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
    (String string, Int newIndex) unpackUtf8(Int index, Int charCount) {
        if (charCount == 0) {
            return "", 0;
        }

        Char[] chars = new Char[charCount];
        Int    len   = 0;
        for (Int i = 0; i < charCount; ++i) {
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
    (Int value, Int newIndex) unpackInt(Int index) {
        Byte b = this[index];
        if (b & 0xC0 != 0x80) {
            // small format: 1 byte value -64..127
            // (first convert the UInt8 to a signed Int8 to obtain automatic sign extension to an Int64)
            return b.toInt8(), index + 1;
        }

        if (b & 0x20 == 0) {
            // medium format: 13 bit int, combines 5 bits + next byte (and sign extend)
            return b.toInt64() << 8 | this[index+1] << 51 >> 51, index + 2;
        }

        // large format: trail mode: next x+1 (2-32) bytes
        Int byteCount = 1 + (b & 0x1F);
        ++index;
        if (byteCount == 1) {
            // huge format: the actual byte length comes next in the stream
            (byteCount, index) = unpackInt(index);
        }
        assert:bounds 0 < byteCount <= 8;

        Int nextIndex = index + byteCount;
        Int n         = this[index++].toInt8();   // signed Int8 will automatically sign-extend
        while (index < nextIndex) {
            n = n << 8 | this[index++];
        }
        return n, nextIndex;
    }


    // ----- view support --------------------------------------------------------------------------

    /**
     * Translator from an array of bytes to an array of any fixed-length numeric type.
     */
    private static class Translator<NumType extends Number>(ByteArray bytes)
            implements ArrayDelegate<NumType> {

        construct(ByteArray bytes) {
            assert Int bitsPerNum := NumType.fixedBitLength();

            this.bytes       = bytes;
            this.bytesPerNum = (bitsPerNum + 7) / 8;
        }

        @Override
        construct(Translator that) {
            this.bytes       = that.bytes.duplicate();
            this.bytesPerNum = that.bytesPerNum;
        }

        private Byte[] bytes;
        private Int bytesPerNum;

        @Override
        Mutability mutability.get() {
            return bytes.mutability;
        }

        @Override
        Int capacity {
            @Override
            Int get() {
                return bytes.capacity / bytesPerNum;
            }

            @Override
            void set(Int c) {
                bytes.capacity = c * bytesPerNum;
            }
        }

        @Override
        Int size.get() {
            (Int nums, Int remain) = bytes.size /% bytesPerNum;
            assert remain == 0;
            return nums;
        }

        @Override
        Var<NumType> elementAt(Int index) {
            assert:bounds 0 <= index < size;

            return new Object() {
                NumType element {
                    @Override
                    Boolean assigned.get() {
                        return index < size;
                    }

                    @Override
                    NumType get() {
                        assert:bounds index < size;
                        val offset = index * bytesPerNum;
                        return new NumType(bytes[offset ..< offset+bytesPerNum]);
                    }

                    @Override
                    void set(NumType num) {
                        if (num != get()) {
                            Byte[] newBytes = num.toByteArray();
                            Int    offset   = index * bytesPerNum;
                            for (Int i : 0 ..< bytesPerNum) {
                                bytes[offset+i] = newBytes[i];
                            }
                        }
                    }
                }
            }.&element;
        }

        @Override
        Translator insert(Int index, NumType value) {
            Byte[] newBytes = bytes.insertAll(index * bytesPerNum, value.toByteArray());
            return &bytes == &newBytes ? this : new Translator<NumType>(newBytes);
        }

        @Override
        Translator delete(Int index) {
            Int offset = index * bytesPerNum;
            Byte[] newBytes = bytes.deleteAll(offset ..< offset+bytesPerNum);
            return &bytes == &newBytes ? this : new Translator<NumType>(newBytes);
        }

        @Override
        NumType[] reify(Mutability? mutability = Null) {
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
    Int8[] asInt8Array() {
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
    Int16[] asInt16Array() {
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
    Int32[] asInt32Array() {
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
    Int64[] asInt64Array() {
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
    Int128[] asInt128Array() {
        assert size % 16 == 0;
        return new Array<Int128>(new Translator<Int128>(this), mutability);
    }

    @Override
    Byte[] asByteArray() {
        static class ReifiableArray
                extends Array<Byte> {
            construct(Byte[] bytes, Mutability mutability) {
                construct Array(bytes, mutability);
            }

            @Override
            construct(ReifiableArray that) {
                construct Array(that);
            }

            @Override
            Byte[] reify(Mutability? mutability = Null) {
                return new Array<Byte>(mutability ?: this.mutability, this);
            }
        }

        // it is frustrating to have to create a new Byte[] when this is already a Byte[], but if
        // we do not do so, then we cannot reify to guarantee a copy
        return new ReifiableArray(this, mutability);
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
    UInt16[] asUInt16Array() {
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
    UInt32[] asUInt32Array() {
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
    UInt64[] asUInt64Array() {
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
    UInt128[] asUInt128Array() {
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
    BFloat16[] asBFloat16Array() {
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
    Float16[] asFloat16Array() {
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
    Float32[] asFloat32Array() {
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
    Float64[] asFloat64Array() {
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
    Float128[] asFloat128Array() {
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
    Dec32[] asDec32Array() {
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
    Dec64[] asDec64Array() {
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
    Dec128[] asDec128Array() {
        assert size % 16 == 0;
        return new Array<Dec128>(new Translator<Dec128>(this), mutability);
    }


    // ----- conversions ---------------------------------------------------------------------------

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
    Int8[] toInt8Array(Mutability mutability = Constant) {
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
    Int16[] toInt16Array(Mutability mutability = Constant) {
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
    Int32[] toInt32Array(Mutability mutability = Constant) {
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
    Int64[] toInt64Array(Mutability mutability = Constant) {
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
    Int128[] toInt128Array(Mutability mutability = Constant) {
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
    UInt16[] toUInt16Array(Mutability mutability = Constant) {
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
    UInt32[] toUInt32Array(Mutability mutability = Constant) {
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
    UInt64[] toUInt64Array(Mutability mutability = Constant) {
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
    UInt128[] toUInt128Array(Mutability mutability = Constant) {
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
    BFloat16[] toBFloat16Array(Mutability mutability = Constant) {
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
    Float16[] toFloat16Array(Mutability mutability = Constant) {
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
    Float32[] toFloat32Array(Mutability mutability = Constant) {
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
    Float64[] toFloat64Array(Mutability mutability = Constant) {
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
    Float128[] toFloat128Array(Mutability mutability = Constant) {
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
    Dec32[] toDec32Array(Mutability mutability = Constant) {
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
    Dec64[] toDec64Array(Mutability mutability = Constant) {
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
    Dec128[] toDec128Array(Mutability mutability = Constant) {
        return asDec128Array().reify(mutability);
    }

    /**
     * Format the contents of this byte array as a "hex dump", useful for debugging.
     *
     * @param bytesPerLine  the number of bytes to show in each line of the hex dump
     *
     * @return a String containing the hex dump
     */
    String toHexDump(Int bytesPerLine = 0) {
        assert:arg bytesPerLine >= 0;
        if (bytesPerLine == 0) {
            bytesPerLine = size.notLessThan(4).notGreaterThan(32);
        }

        // calculate how many digits it will take to show the address
        Int addrLen = ((size.leftmostBit.trailingZeroCount) / 8 + 1) * 2;       // 2, 4, 6, ...

        // format is "12F0: 00 12 32 A0 ????\n"
        // line length is addrLen + 4*bytesPerLine + 3
        Int    charsPerLine = addrLen + bytesPerLine * 4 + 3;
        Char[] lineText     = new Char[charsPerLine](' ');

        lineText[addrLen       ] = ':';
        lineText[charsPerLine-1] = '\n';

        Int lines      = Int.maxOf(1, ((size + bytesPerLine - 1) / bytesPerLine));
        val buf        = new StringBuffer(lines * charsPerLine);
        Int byteOffset = 0;
        Int hexOffset  = addrLen + 2;
        Int charOffset = hexOffset + bytesPerLine * 3;

        for (Int line : 0 ..< lines) {
            // format the address
            Int addr       = byteOffset;
            Int addrOffset = addrLen - 1;
            while (addrOffset >= 0) {
                lineText[addrOffset--] = addr.toHexit();
                addr >>= 4;
            }

            for (Int index : 0 ..< bytesPerLine) {
                if (byteOffset < size) {
                    Byte b  = this[byteOffset];
                    Char ch = b.toChar();

                    lineText[hexOffset + index * 3    ] = (b >>> 4).toHexit();
                    lineText[hexOffset + index * 3 + 1] = b.toHexit();
                    lineText[charOffset + index       ] = ch.isEscaped() ? '.' : ch;
                } else {
                    lineText[hexOffset + index * 3    ] = ' ';
                    lineText[hexOffset + index * 3 + 1] = ' ';
                    lineText[charOffset + index       ] = ' ';
                }

                ++byteOffset;
            }

            buf.addAll(line < lines - 1 ? lineText : lineText[0 ..< charsPerLine]);
        }

        return buf.toString();
    }


    // ----- Stringable methods --------------------------------------------------------------------

    @Override
    Int estimateStringLength(
            String                    sep    = "",
            String?                   pre    = "0x",
            String?                   post   = Null,
            Int?                      limit  = Null,
            String                    trunc  = "...",
            function String(Element)? render = Null) {
        return sep == "" && limit == Null && render == Null
                ? (pre?.size : 0) + size*2 + (post?.size : 0)
                : super(sep, pre, post, limit, trunc, render);
    }

    @Override
    Appender<Char> appendTo(
            Appender<Char>            buf,
            String                    sep    = "",
            String?                   pre    = "0x",
            String?                   post   = Null,
            Int?                      limit  = Null,
            String                    trunc  = "...",
            function String(Element)? render = Null) {
        return sep == "" && limit == Null && render == Null
                ? asNibbleArray().appendTo(buf, sep, pre, post)
                : super(buf, sep, pre, post, limit, trunc, render);
    }

    @Override
    String toString(
            String                    sep    = "",
            String?                   pre    = "0x",
            String?                   post   = Null,
            Int?                      limit  = Null,
            String                    trunc  = "...",
            function String(Element)? render = Null) {
        return super(sep, pre, post, limit, trunc, render);
    }
}