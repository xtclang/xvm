/**
 * Functionality specific to an array of nibbles.
 */
mixin NibbleArray<Element extends Nibble>
        into Array<Element>
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
                            return (nibbles[index/4].toInt64() & 1 << index%4 != 0).toBit();
                            }

                        @Override
                        void set(Bit v)
                            {
                            assert:bounds index < size;
                            Int    nibbleIndex   = index/4;
                            Int    oldValue      = nibbles[nibbleIndex];
                            Int    mask          = 1 << index%4;
                            Int    newValue      = v == 1 ? oldValue | mask : oldValue & ~mask;
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
                            return nibbles[offset..offset+2).toByte();
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
                Nibble[] newNibbles = nibbles.deleteAll([offset..offset+2));
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

    /**
     * A second name for the [toUInt8] method, to assist with readability. By using a property
     * to alias the method, instead of creating a second delegating method, this prevents the
     * potential for accidentally overriding the wrong method.
     */
    static Method<NibbleArray, <>, <Byte>> toByte = toUInt8;

    /**
     * Convert the nibble array to a signed 8-bit integer.
     *
     * @throws OutOfBounds  if the resulting value is out of the signed 8-bit integer range
     */
    Int8 toInt8()
        {
        return asBitArray().toInt8();
        }

    /**
     * Convert the nibble array to a signed 16-bit integer.
     *
     * @throws OutOfBounds  if the resulting value is out of the signed 16-bit integer range
     */
    Int16 toInt16()
        {
        return asBitArray().toInt16();
        }

    /**
     * Convert the nibble array to a signed 32-bit integer.
     *
     * @throws OutOfBounds  if the resulting value is out of the signed 32-bit integer range
     */
    Int32 toInt32()
        {
        return asBitArray().toInt32();
        }

    /**
     * Convert the nibble array to a signed 64-bit integer.
     *
     * @throws OutOfBounds  if the resulting value is out of the signed 64-bit integer range
     */
    Int64 toInt64()
        {
        return asBitArray().toInt64();
        }

    /**
     * Convert the nibble array to a signed 128-bit integer.
     *
     * @throws OutOfBounds  if the resulting value is out of the signed 128-bit integer range
     */
    Int128 toInt128()
        {
        return asBitArray().toInt128();
        }

    /**
     * Convert the nibble array to a variable-length signed integer.
     *
     * @return a variable-length signed integer value
     *
     * @throws OutOfBounds if the nibble array is not a supported size for the resulting numeric
     *         type
     */
    IntN toIntN()
        {
        return asBitArray().toIntN();
        }

    /**
     * Convert the nibble array to an unsigned 8-bit integer.
     *
     * @throws OutOfBounds  if the resulting value is out of the unsigned 8-bit integer range
     */
    UInt8 toUInt8()
        {
        return asBitArray().toUInt8();
        }

    /**
     * Convert the nibble array to an unsigned 16-bit integer.
     *
     * @throws OutOfBounds  if the resulting value is out of the unsigned 16-bit integer range
     */
    UInt16 toUInt16()
        {
        return asBitArray().toUInt16();
        }

    /**
     * Convert the nibble array to an unsigned 32-bit integer.
     *
     * @throws OutOfBounds  if the resulting value is out of the unsigned 32-bit integer range
     */
    UInt32 toUInt32()
        {
        return asBitArray().toUInt32();
        }

    /**
     * Convert the nibble array to an unsigned 64-bit integer.
     *
     * @throws OutOfBounds  if the resulting value is out of the unsigned 64-bit integer range
     */
    UInt64 toUInt64()
        {
        return asBitArray().toUInt64();
        }

    /**
     * Convert the nibble array to an unsigned 128-bit integer.
     *
     * @throws OutOfBounds  if the resulting value is out of the unsigned 128-bit integer range
     */
    UInt128 toUInt128()
        {
        return asBitArray().toUInt128();
        }

    /**
     * Convert the nibble array to a variable-length unsigned integer.
     *
     * @return a variable-length unsigned integer value
     *
     * @throws OutOfBounds if the nibble array is not a supported size for the resulting numeric type
     */
    UIntN toUIntN()
        {
        return asBitArray().toUIntN();
        }

    /**
     * Convert the nibble array to a 16-bit radix-2 (binary) "brain" floating point number.
     *
     * @return a 16-bit "brain" floating point value
     *
     * @throws OutOfBounds if the nibble array is not exactly the size of the resulting numeric type
     */
    BFloat16 toBFloat16()
        {
        return asBitArray().toBFloat16();
        }

    /**
     * Convert the nibble array to a 16-bit radix-2 (binary) floating point number.
     *
     * @return a 16-bit floating point value
     *
     * @throws OutOfBounds if the nibble array is not exactly the size of the resulting numeric type
     */
    Float16 toFloat16()
        {
        return asBitArray().toFloat16();
        }

    /**
     * Convert the nibble array to a 32-bit radix-2 (binary) floating point number.
     *
     * @return a 32-bit floating point value
     *
     * @throws OutOfBounds if the nibble array is not exactly the size of the resulting numeric type
     */
    Float32 toFloat32()
        {
        return asBitArray().toFloat32();
        }

    /**
     * Convert the nibble array to a 64-bit radix-2 (binary) floating point number.
     *
     * @return a 64-bit floating point value
     *
     * @throws OutOfBounds if the nibble array is not exactly the size of the resulting numeric type
     */
    Float64 toFloat64()
        {
        return asBitArray().toFloat64();
        }

    /**
     * Convert the nibble array to a 128-bit radix-2 (binary) floating point number.
     *
     * @return a 128-bit floating point value
     *
     * @throws OutOfBounds if the nibble array is not exactly the size of the resulting numeric type
     */
    Float128 toFloat128()
        {
        return asBitArray().toFloat128();
        }

    /**
     * Convert the nibble array to a variable-length binary radix floating point number.
     *
     * @return a variable-length floating point value
     *
     * @throws OutOfBounds if the nibble array is not a supported size for the resulting numeric type
     */
    FloatN toFloatN()
        {
        return asBitArray().toFloatN();
        }

    /**
     * Convert the nibble array to a 32-bit radix-10 (decimal) floating point number.
     *
     * @return a 32-bit decimal floating point value
     *
     * @throws OutOfBounds if the nibble array is not exactly the size of the resulting numeric type
     */
    Dec32 toDec32()
        {
        return asBitArray().toDec32();
        }

    /**
     * Convert the nibble array to a 64-bit radix-10 (decimal) floating point number.
     *
     * @return a 64-bit decimal floating point value
     *
     * @throws OutOfBounds if the nibble array is not exactly the size of the resulting numeric type
     */
    Dec64 toDec64()
        {
        return asBitArray().toDec64();
        }

    /**
     * Convert the nibble array to a 128-bit radix-10 (decimal) floating point number.
     *
     * @return a 128-bit decimal floating point value
     *
     * @throws OutOfBounds if the nibble array is not exactly the size of the resulting numeric type
     */
    Dec128 toDec128()
        {
        return asBitArray().toDec128();
        }

    /**
     * Convert the nibble array to a variable-length decimal radix floating point number.
     *
     * @return a variable-length decimal floating point value
     *
     * @throws OutOfBounds if the nibble array is not a supported size for the resulting numeric type
     */
    DecN toDecN()
        {
        return asBitArray().toDecN();
        }


    // ----- Stringable methods --------------------------------------------------------------------

    @Override
    Int estimateStringLength()
        {
        return 2 + size;
        }

    @Override
    Appender<Char> appendTo(Appender<Char> buf)
        {
        "0x".appendTo(buf);
        for (Nibble nibble : this)
            {
            nibble.appendTo(buf);
            }
        return buf;
        }
    }
