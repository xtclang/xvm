import numbers.IntConvertible;
import numbers.FPConvertible;


/**
 * Functionality specific to an array of nibbles.
 */
mixin NibbleArray<Element extends Nibble>
        into Array<Element>
        implements IntConvertible
        implements FPConvertible
    {
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
    Bit[] asBitArray()
        {
        static class Translator(Nibble[] nibbles)
                implements ArrayDelegate<Bit>
            {
            @Override
            construct(Translator that)
                {
                this.nibbles = that.nibbles.duplicate();
                }

            private Nibble[] nibbles;

            @Override
            Mutability mutability.get()
                {
                Mutability mut = nibbles.mutability;
                return mut == Mutable ? Fixed : mut;
                }

            @Override
            Int capacity
                {
                @Override
                Int get()
                    {
                    return nibbles.capacity * 4;
                    }

                @Override
                void set(Int c)
                    {
                    nibbles.capacity = c / 4;
                    }
                }

            @Override
            Int size.get()
                {
                return nibbles.size * 4;
                }

            @Override
            Var<Bit> elementAt(Int index)
                {
                assert:bounds 0 <= index < size;

                return new Object()
                    {
                    Bit element
                        {
                        @Override
                        Boolean assigned.get()
                            {
                            return index < size;
                            }

                        @Override
                        Bit get()
                            {
                            assert:bounds index < size;
                            return (nibbles[index/4].toInt() & 1 << index%4 != 0).toBit();
                            }

                        @Override
                        void set(Bit v)
                            {
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
            Translator insert(Int index, Bit value)
                {
                throw new ReadOnly();
                }

            @Override
            Translator delete(Int index)
                {
                throw new ReadOnly();
                }

            @Override
            Bit[] reify(Mutability? mutability = Null)
                {
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
    Byte[] asByteArray()
        {
        assert:bounds size % 2 == 0;

        static class Translator(Nibble[] nibbles)
                implements ArrayDelegate<Byte>
            {
            @Override
            construct(Translator that)
                {
                this.nibbles = that.nibbles.duplicate();
                }

            private Nibble[] nibbles;

            @Override
            Mutability mutability.get()
                {
                return nibbles.mutability;
                }

            @Override
            Int capacity
                {
                @Override
                Int get()
                    {
                    return nibbles.capacity / 2;
                    }

                @Override
                void set(Int c)
                    {
                    nibbles.capacity = c * 2;
                    }
                }

            @Override
            Int size.get()
                {
                (Int bytes, Int remain) = nibbles.size /% 2;
                assert remain == 0;
                return bytes;
                }

            @Override
            Var<Byte> elementAt(Int index)
                {
                assert:bounds 0 <= index < size;

                return new Object()
                    {
                    Byte element
                        {
                        @Override
                        Boolean assigned.get()
                            {
                            return index < size;
                            }

                        @Override
                        Byte get()
                            {
                            assert:bounds assigned;
                            Int offset = index * 2;
                            return nibbles[offset ..< offset+2].toByte();
                            }

                        @Override
                        void set(Byte v)
                            {
                            if (v != get())
                                {
                                Int offset = index * 2;
                                nibbles[offset  ] = v.highNibble;
                                nibbles[offset+1] = v.lowNibble;
                                }
                            }
                        }
                    }.&element;
                }

            @Override
            Translator insert(Int index, Byte value)
                {
                Nibble[] newNibbles = nibbles.insertAll(index * 2, value.toNibbleArray());
                return &nibbles == &newNibbles ? this : new Translator(newNibbles);
                }

            @Override
            Translator delete(Int index)
                {
                Int offset = index * 2;
                Nibble[] newNibbles = nibbles.deleteAll(offset ..< offset+2);
                return &nibbles == &newNibbles ? this : new Translator(newNibbles);
                }

            @Override
            Byte[] reify(Mutability? mutability = Null)
                {
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
    Bit[] toBitArray(Mutability mutability = Constant)
        {
        return asBitArray().reify(mutability);
        }

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
    Byte[] toByteArray(Mutability mutability = Constant)
        {
        return asByteArray().reify(mutability);
        }

    @Override
    Int toInt(Boolean truncate = False, Rounding direction = TowardZero)
        {
        return asBitArray().toInt(truncate, direction);
        }

    @Override
    Int8 toInt8(Boolean truncate = False, Rounding direction = TowardZero)
        {
        return asBitArray().toInt8(truncate, direction);
        }

    @Override
    Int16 toInt16(Boolean truncate = False, Rounding direction = TowardZero)
        {
        return asBitArray().toInt16(truncate, direction);
        }

    @Override
    Int32 toInt32(Boolean truncate = False, Rounding direction = TowardZero)
        {
        return asBitArray().toInt32(truncate, direction);
        }

    @Override
    Int64 toInt64(Boolean truncate = False, Rounding direction = TowardZero)
        {
        return asBitArray().toInt64(truncate, direction);
        }

    @Override
    Int128 toInt128(Boolean truncate = False, Rounding direction = TowardZero)
        {
        return asBitArray().toInt128(truncate, direction);
        }

    @Override
    IntN toIntN(Rounding direction = TowardZero)
        {
        return asBitArray().toIntN(direction);
        }

    @Override
    UInt toUInt(Boolean truncate = False, Rounding direction = TowardZero)
        {
        return asBitArray().toUInt(truncate, direction);
        }

    @Override
    UInt8 toUInt8(Boolean truncate = False, Rounding direction = TowardZero)
        {
        return asBitArray().toUInt8(truncate, direction);
        }

    @Override
    UInt16 toUInt16(Boolean truncate = False, Rounding direction = TowardZero)
        {
        return asBitArray().toUInt16(truncate, direction);
        }

    @Override
    UInt32 toUInt32(Boolean truncate = False, Rounding direction = TowardZero)
        {
        return asBitArray().toUInt32(truncate, direction);
        }

    @Override
    UInt64 toUInt64(Boolean truncate = False, Rounding direction = TowardZero)
        {
        return asBitArray().toUInt64(truncate, direction);
        }

    @Override
    UInt128 toUInt128(Boolean truncate = False, Rounding direction = TowardZero)
        {
        return asBitArray().toUInt128(truncate, direction);
        }

    @Override
    UIntN toUIntN(Rounding direction = TowardZero)
        {
        return asBitArray().toUIntN(direction);
        }

    @Override
    Dec toDec()
        {
        return asBitArray().toDec();
        }

    @Override
    Dec32 toDec32()
        {
        return asBitArray().toDec32();
        }

    @Override
    Dec64 toDec64()
        {
        return asBitArray().toDec64();
        }

    @Override
    Dec128 toDec128()
        {
        return asBitArray().toDec128();
        }

    @Override
    DecN toDecN()
        {
        return asBitArray().toDecN();
        }

    @Override
    Float8e4 toFloat8e4()
        {
        return asBitArray().toFloat8e4();
        }

    @Override
    Float8e5 toFloat8e5()
        {
        return asBitArray().toFloat8e5();
        }

    @Override
    BFloat16 toBFloat16()
        {
        return asBitArray().toBFloat16();
        }

    @Override
    Float16 toFloat16()
        {
        return asBitArray().toFloat16();
        }

    @Override
    Float32 toFloat32()
        {
        return asBitArray().toFloat32();
        }

    @Override
    Float64 toFloat64()
        {
        return asBitArray().toFloat64();
        }

    @Override
    Float128 toFloat128()
        {
        return asBitArray().toFloat128();
        }

    @Override
    FloatN toFloatN()
        {
        return asBitArray().toFloatN();
        }


    // ----- Stringable methods --------------------------------------------------------------------

    @Override
    Int estimateStringLength(
            String                    sep    = "",
            String?                   pre    = "0x",
            String?                   post   = Null,
            Int?                      limit  = Null,
            String                    trunc  = "...",
            function String(Element)? render = Null)
        {
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
            function String(Element)? render = Null)
        {
        return super(buf, sep, pre, post, limit, trunc, render);
        }
    }