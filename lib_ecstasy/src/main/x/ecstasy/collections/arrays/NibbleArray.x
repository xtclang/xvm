import numbers.IntConvertible;
import numbers.FPConvertible;


/**
 * Functionality specific to an array of nibbles.
 */
mixin NibbleArray<Element extends Nibble>
        into Array<Element>
        implements IntConvertible
        implements FPConvertible {
    // ----- view support --------------------------------------------------------------------------

    /**
     * Obtain a _view_ of this array-of-nibbles as an array-of-bits. The resulting array has the
     * same mutability as this array, except that the resulting array is FixedSize if this array is
     * Mutable. Conceptually, this is very similar to array delegation, except that the Element type
     * is different between the delegating array (the new Bit array) and the delegatee (this Nibble
     * array).
     *
     * @return an array of bits that acts as a read/write view of the contents of this nibble array
     */
    Bit[] asBitArray() {
        static class Translator(Nibble[] nibbles)
                implements ArrayDelegate<Bit> {
            @Override
            construct(Translator that) {
                this.nibbles = that.nibbles.duplicate();
            }

            private Nibble[] nibbles;

            @Override
            Mutability mutability.get() {
                Mutability mut = nibbles.mutability;
                return mut == Mutable ? Fixed : mut;
            }

            @Override
            Int capacity {
                @Override
                Int get() = nibbles.capacity * 4;

                @Override
                void set(Int c) {
                    nibbles.capacity = c / 4;
                }
            }

            @Override
            Int size.get() = nibbles.size * 4;

            @Override
            Var<Bit> elementAt(Int index) {
                assert:bounds 0 <= index < size;

                return new Object() {
                    Bit element {
                        @Override
                        Boolean assigned.get() = index < size;

                        @Override
                        Bit get() {
                            assert:bounds index < size;
                            return (nibbles[index/4].toInt() & 1 << index%4 != 0).toBit();
                        }

                        @Override
                        void set(Bit v) {
                            assert:bounds index < size;
                            Int nibbleIndex   = index/4;
                            Int oldValue      = nibbles[nibbleIndex];
                            Int mask          = 1 << index%4;
                            Int newValue      = v == 1 ? oldValue | mask : oldValue & ~mask;
                            nibbles[nibbleIndex] = newValue.toNibble();
                        }
                    }
                }.&element;
            }

            @Override
            Translator insert(Int index, Bit value) = throw new ReadOnly();

            @Override
            Translator delete(Int index) = throw new ReadOnly();

            @Override
            Bit[] reify(Mutability? mutability = Null) {
                mutability ?:= this.mutability;
                return new Bit[size](i -> elementAt(i).get()).toArray(mutability, inPlace=True);
            }
        }

        return new Array<Bit>(new Translator(this), mutability);
    }

    /**
     * Obtain a _view_ of this array-of-nibbles as an array-of-bytes. The resulting array has the
     * same mutability as this array. Conceptually, this is very similar to array delegation, except
     * that the Element type is different between the delegating array (the new Byte array) and the
     * delegatee (this Nibble array).
     *
     * @return an array of bytes that acts as a read/write view of the contents of this nibble array
     *
     * @throws OutOfBounds  if the nibble array size is not evenly divisible by 2
     */
    Byte[] asByteArray() {
        assert:bounds size % 2 == 0;

        static class Translator(Nibble[] nibbles)
                implements ArrayDelegate<Byte> {
            @Override
            construct(Translator that) {
                this.nibbles = that.nibbles.duplicate();
            }

            private Nibble[] nibbles;

            @Override
            Mutability mutability.get() = nibbles.mutability;

            @Override
            Int capacity {
                @Override
                Int get() = nibbles.capacity / 2;

                @Override
                void set(Int c) {
                    nibbles.capacity = c * 2;
                }
            }

            @Override
            Int size.get() {
                (Int bytes, Int remain) = nibbles.size /% 2;
                assert remain == 0;
                return bytes;
            }

            @Override
            Var<Byte> elementAt(Int index) {
                assert:bounds 0 <= index < size;

                return new Object() {
                    Byte element {
                        @Override
                        Boolean assigned.get() {
                            return index < size;
                        }

                        @Override
                        Byte get() {
                            assert:bounds assigned;
                            Int offset = index * 2;
                            return nibbles[offset ..< offset+2].toByte();
                        }

                        @Override
                        void set(Byte v) {
                            if (v != get()) {
                                Int offset = index * 2;
                                nibbles[offset  ] = v.highNibble;
                                nibbles[offset+1] = v.lowNibble;
                            }
                        }
                    }
                }.&element;
            }

            @Override
            Translator insert(Int index, Byte value) {
                Nibble[] newNibbles = nibbles.insertAll(index * 2, value.toNibbleArray());
                return &nibbles == &newNibbles ? this : new Translator(newNibbles);
            }

            @Override
            Translator delete(Int index) {
                Int offset = index * 2;
                Nibble[] newNibbles = nibbles.deleteAll(offset ..< offset+2);
                return &nibbles == &newNibbles ? this : new Translator(newNibbles);
            }

            @Override
            Byte[] reify(Mutability? mutability = Null) {
                mutability ?:= this.mutability;
                return new Byte[size](i -> elementAt(i).get()).toArray(mutability, inPlace=True);
            }
        }

        return new Array<Byte>(new Translator(this), mutability);
    }


    // ----- conversions ---------------------------------------------------------------------------

    /**
     * Obtain an immutable copy of this array's data that exposes the underlying data as Bit
     * values, each corresponding to a Bit extracted from a corresponding Nibble, resulting in an
     * array with 4x the elements as this Nibble array.
     *
     * @param mutability  the desired mutability for the resulting array, defaulting to constant
     *
     * @return an array of bits corresponding to the nibbles in this array
     */
    Bit[] toBitArray(Mutability mutability = Constant) = asBitArray().reify(mutability);

    /**
     * Obtain an immutable copy of this array's data that exposes the underlying data as Byte
     * values, each composed of 2 Nibble values.
     *
     * @param mutability  the desired mutability for the resulting array, defaulting to constant
     *
     * @return an array of bytes corresponding to the nibbles in this array
     *
     * @throws OutOfBounds  if the nibble array size is not evenly divisible by 2
     */
    Byte[] toByteArray(Mutability mutability = Constant) = asByteArray().reify(mutability);

    /**
     * @param checkBounds  pass `True` to bounds-check this value before conversion, or `False` to
     *                     blindly retain only the necessary number of least significant bits, which
     *                     may lose magnitude or change the sign of the result, and then sign extend
     *                     if additional bits are required
     *
     * @throws OutOfBounds  iff `checkBounds` is `True` and the resulting value is out of the signed
     *                      8-bit integer range
     */
    @Override
    Int8 toInt8(Boolean checkBounds = False) = asBitArray().toInt8(checkBounds);

    /**
     * @param checkBounds  pass `True` to bounds-check this value before conversion, or `False` to
     *                     blindly retain only the necessary number of least significant bits, which
     *                     may lose magnitude or change the sign of the result, and then sign extend
     *                     if additional bits are required
     *
     * @throws OutOfBounds  iff `checkBounds` is `True` and the resulting value is out of the signed
     *                      16-bit integer range
     */
    @Override
    Int16 toInt16(Boolean checkBounds = False) = asBitArray().toInt16(checkBounds);

    /**
     * @param checkBounds  pass `True` to bounds-check this value before conversion, or `False` to
     *                     blindly retain only the necessary number of least significant bits, which
     *                     may lose magnitude or change the sign of the result, and then sign extend
     *                     if additional bits are required
     *
     * @throws OutOfBounds  iff `checkBounds` is `True` and the resulting value is out of the signed
     *                      32-bit integer range
     */
    @Override
    Int32 toInt32(Boolean checkBounds = False) = asBitArray().toInt32(checkBounds);

    /**
     * @param checkBounds  pass `True` to bounds-check this value before conversion, or `False` to
     *                     blindly retain only the necessary number of least significant bits, which
     *                     may lose magnitude or change the sign of the result, and then sign extend
     *                     if additional bits are required
     *
     * @throws OutOfBounds  iff `checkBounds` is `True` and the resulting value is out of the signed
     *                      64-bit integer range
     */
    @Override
    Int64 toInt64(Boolean checkBounds = False) = asBitArray().toInt64(checkBounds);

    /**
     * @param checkBounds  pass `True` to bounds-check this value before conversion, or `False` to
     *                     blindly retain only the necessary number of least significant bits, which
     *                     may lose magnitude or change the sign of the result, and then sign extend
     *                     if additional bits are required
     *
     * @throws OutOfBounds  iff `checkBounds` is `True` and the resulting value is out of the signed
     *                      128-bit integer range
     */
    @Override
    Int128 toInt128(Boolean checkBounds = False) = asBitArray().toInt128(checkBounds);

    @Override
    IntN toIntN() = asBitArray().toIntN();

    /**
     * @param checkBounds  pass `True` to bounds-check this value before conversion, or `False` to
     *                     blindly retain only the necessary number of least significant bits, which
     *                     may lose magnitude or change the sign of the result, and then sign extend
     *                     if additional bits are required
     *
     * @throws OutOfBounds  iff `checkBounds` is `True` and the resulting value is out of the
     *                      unsigned 8-bit integer range
     */
    @Override
    UInt8 toUInt8(Boolean checkBounds = False) = asBitArray().toUInt8(checkBounds);

    /**
     * @param checkBounds  pass `True` to bounds-check this value before conversion, or `False` to
     *                     blindly retain only the necessary number of least significant bits, which
     *                     may lose magnitude or change the sign of the result, and then sign extend
     *                     if additional bits are required
     *
     * @throws OutOfBounds  iff `checkBounds` is `True` and the resulting value is out of the
     *                      unsigned 16-bit integer range
     */
    @Override
    UInt16 toUInt16(Boolean checkBounds = False) = asBitArray().toUInt16(checkBounds);

    /**
     * @param checkBounds  pass `True` to bounds-check this value before conversion, or `False` to
     *                     blindly retain only the necessary number of least significant bits, which
     *                     may lose magnitude or change the sign of the result, and then sign extend
     *                     if additional bits are required
     *
     * @throws OutOfBounds  iff `checkBounds` is `True` and the resulting value is out of the
     *                      unsigned 32-bit integer range
     */
    @Override
    UInt32 toUInt32(Boolean checkBounds = False) = asBitArray().toUInt32(checkBounds);

    /**
     * @param checkBounds  pass `True` to bounds-check this value before conversion, or `False` to
     *                     blindly retain only the necessary number of least significant bits, which
     *                     may lose magnitude or change the sign of the result, and then sign extend
     *                     if additional bits are required
     *
     * @throws OutOfBounds  iff `checkBounds` is `True` and the resulting value is out of the
     *                      unsigned 64-bit integer range
     */
    @Override
    UInt64 toUInt64(Boolean checkBounds = False) = asBitArray().toUInt64(checkBounds);

    /**
     * @param checkBounds  pass `True` to bounds-check this value before conversion, or `False` to
     *                     blindly retain only the necessary number of least significant bits, which
     *                     may lose magnitude or change the sign of the result, and then sign extend
     *                     if additional bits are required
     *
     * @throws OutOfBounds  iff `checkBounds` is `True` and the resulting value is out of the
     *                      unsigned 128-bit integer range
     */
    @Override
    UInt128 toUInt128(Boolean checkBounds = False) = asBitArray().toUInt128(checkBounds);

    @Override
    UIntN toUIntN() = asBitArray().toUIntN();

    /**
     * @param checkBounds  pass `True` to bounds-check, or `False` to blindly retain only the
     *                     necessary number of least significant integer bits, which may lose
     *                     magnitude or change the sign of the result
     *
     * @throws OutOfBounds  iff `checkBounds` is `True` and the resulting value is out of the
     *                      unsigned 8-bit integer range
     */
    @Override
    Byte toByte(Boolean checkBounds = False) = toUInt8(checkBounds);

    /**
     * @param checkBounds  pass `True` to bounds-check, or `False` to blindly retain only the
     *                     necessary number of least significant integer bits, which may lose
     *                     magnitude or change the sign of the result
     *
     * @throws OutOfBounds  iff `checkBounds` is `True` and the resulting value is out of the signed
     *                      64-bit integer range
     */
    @Override
    Int toInt(Boolean checkBounds = False) = toInt64(checkBounds);

    /**
     * @param checkBounds  pass `True` to bounds-check, or `False` to blindly retain only the
     *                     necessary number of least significant integer bits, which may lose
     *                     magnitude or change the sign of the result
     *
     * @throws OutOfBounds  iff `checkBounds` is `True` and the resulting value is out of the
     *                      unsigned 8-bit integer range
     */
    @Override
    UInt toUInt(Boolean checkBounds = False) = toUInt64(checkBounds);

    @Override
    Dec32 toDec32() = asBitArray().toDec32();

    @Override
    Dec64 toDec64() = asBitArray().toDec64();

    @Override
    Dec128 toDec128() = asBitArray().toDec128();

    @Override
    DecN toDecN() = asBitArray().toDecN();

    @Override
    Float8e4 toFloat8e4() = asBitArray().toFloat8e4();

    @Override
    Float8e5 toFloat8e5() = asBitArray().toFloat8e5();

    @Override
    BFloat16 toBFloat16() = asBitArray().toBFloat16();

    @Override
    Float16 toFloat16() = asBitArray().toFloat16();

    @Override
    Float32 toFloat32() = asBitArray().toFloat32();

    @Override
    Float64 toFloat64() = asBitArray().toFloat64();

    @Override
    Float128 toFloat128() = asBitArray().toFloat128();

    @Override
    FloatN toFloatN() = asBitArray().toFloatN();


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
            ? (pre?.size : 0) + size + (post?.size : 0)
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
        return super(buf, sep, pre, post, limit, trunc, render);
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