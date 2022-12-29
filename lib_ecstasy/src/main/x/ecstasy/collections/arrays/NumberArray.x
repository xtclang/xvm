/**
 * Functionality specific to arrays of numbers.
 */
mixin NumberArray<Element extends Number>
        into Array<Element>
        // TODO implements IntConvertible, FPConvertible (e.g. see BitArray)
    {
    /**
     * Calculate the negatives for each number in this array.
     *
     * @param inPlace (optional) pass True to specify that the operation should occur using `this`
     *                to hold the result, if possible
     *
     * @return the array containing the negatives of each number in this array
     *
     * @throws IllegalMath  if the requested operation cannot be performed for any reason
     * @throws OutOfBounds  if no corresponding negated value is possible to express with this type
     */
    NumberArray negVector(Boolean inPlace = False)
        {
        if (inPlace && this.inPlace)
            {
            for (Int i : 0 ..< size)
                {
                this[i] = -this[i];
                }
            return this;
            }

        return new Element[size](i -> -this[i]).toArray(mutability, inPlace=True);
        }

    /**
     * Addition: Add the passed number to each number in this array, returning the resulting array.
     *
     * @param scalar  the scalar value to add to the numbers in this array
     * @param inPlace (optional) pass True to specify that the operation should occur using `this`
     *                to hold the result, if possible
     *
     * @return the array containing the resulting sums
     *
     * @throws IllegalMath  if the requested operation cannot be performed for any reason
     * @throws OutOfBounds  if a resulting value is out of range for this type
     */
    NumberArray addScalar(Element scalar, Boolean inPlace = False)
        {
        if (inPlace && this.inPlace)
            {
            for (Int i : 0 ..< size)
                {
                this[i] += scalar;
                }
            return this;
            }

        return new Element[size](i -> this[i] + scalar).toArray(mutability, inPlace=True);
        }

    /**
     * Addition: Add each number in the passed array to the corresponding number in this array,
     * returning the resulting array.
     *
     * @param that    the array of numbers to add to the numbers in this array
     * @param inPlace (optional) pass True to specify that the operation should occur using `this`
     *                to hold the result, if possible
     *
     * @return the array containing the resulting sums
     *
     * @throws IllegalMath  if the requested operation cannot be performed for any reason
     * @throws OutOfBounds  if a resulting value is out of range for this type
     */
    NumberArray addVector(NumberArray that, Boolean inPlace = False)
        {
        assert:bounds this.size == that.size;

        if (inPlace && this.inPlace)
            {
            for (Int i : 0 ..< size)
                {
                this[i] += that[i];
                }
            return this;
            }

        return new Element[size](i -> this[i] + that[i]).toArray(mutability, inPlace=True);
        }

    /**
     * Subtraction: Subtract the passed number from each number in this array, returning the
     * resulting array.
     *
     * @param scalar  the scalar value to subtract from the numbers in this array
     * @param inPlace (optional) pass True to specify that the operation should occur using `this`
     *                to hold the result, if possible
     *
     * @return the array containing the resulting differences
     *
     * @throws IllegalMath  if the requested operation cannot be performed for any reason
     * @throws OutOfBounds  if a resulting value is out of range for this type
     */
    NumberArray subScalar(Element scalar, Boolean inPlace = False)
        {
        if (inPlace && this.inPlace)
            {
            for (Int i : 0 ..< size)
                {
                this[i] -= scalar;
                }
            return this;
            }

        return new Element[size](i -> this[i] - scalar).toArray(mutability, inPlace=True);
        }

    /**
     * Subtraction: Subtract each number in the passed array from the corresponding number in this
     * array, returning the resulting array.
     *
     * @param that    the array of numbers to subtract from the numbers in this array
     * @param inPlace (optional) pass True to specify that the operation should occur using `this`
     *                to hold the result, if possible
     *
     * @return the array containing the resulting differences
     *
     * @throws IllegalMath  if the requested operation cannot be performed for any reason
     * @throws OutOfBounds  if a resulting value is out of range for this type
     */
    NumberArray subVector(NumberArray that, Boolean inPlace = False)
        {
        assert:bounds this.size == that.size;

        if (inPlace && this.inPlace)
            {
            for (Int i : 0 ..< size)
                {
                this[i] -= that[i];
                }
            return this;
            }

        return new Element[size](i -> this[i] - that[i]).toArray(mutability, inPlace=True);
        }

    /**
     * Multiplication: Multiply the passed number with each number in this array, returning the
     * resulting array.
     *
     * @param scalar  the scalar value to multiply with the numbers in this array
     * @param inPlace (optional) pass True to specify that the operation should occur using `this`
     *                to hold the result, if possible
     *
     * @return the array containing the resulting products
     *
     * @throws IllegalMath  if the requested operation cannot be performed for any reason
     * @throws OutOfBounds  if a resulting value is out of range for this type
     */
    NumberArray mulScalar(Element scalar, Boolean inPlace = False)
        {
        if (inPlace && this.inPlace)
            {
            for (Int i : 0 ..< size)
                {
                this[i] *= scalar;
                }
            return this;
            }

        return new Element[size](i -> this[i] * scalar).toArray(mutability, inPlace=True);
        }

    /**
     * Multiplication: Multiply each number in the passed array with the corresponding number in
     * this array, returning the resulting array.
     *
     * @param that    the array of numbers to multiply with the numbers in this array
     * @param inPlace (optional) pass True to specify that the operation should occur using `this`
     *                to hold the result, if possible
     *
     * @return the array containing the resulting products
     *
     * @throws IllegalMath  if the requested operation cannot be performed for any reason
     * @throws OutOfBounds  if a resulting value is out of range for this type
     */
    NumberArray mulVector(NumberArray that, Boolean inPlace = False)
        {
        assert:bounds this.size == that.size;

        if (inPlace && this.inPlace)
            {
            for (Int i : 0 ..< size)
                {
                this[i] *= that[i];
                }
            return this;
            }

        return new Element[size](i -> this[i] * that[i]).toArray(mutability, inPlace=True);
        }

    /**
     * Division: Divide each number in this array by the passed number, returning the resulting
     * array.
     *
     * @param scalar  the scalar divisor
     * @param inPlace (optional) pass True to specify that the operation should occur using `this`
     *                to hold the result, if possible
     *
     * @return the array containing the resulting quotients
     *
     * @throws IllegalMath  if the requested operation cannot be performed for any reason
     * @throws OutOfBounds  if a resulting value is out of range for this type
     */
    NumberArray divScalar(Element scalar, Boolean inPlace = False)
        {
        if (inPlace && this.inPlace)
            {
            for (Int i : 0 ..< size)
                {
                this[i] /= scalar;
                }
            return this;
            }

        return new Element[size](i -> this[i] / scalar).toArray(mutability, inPlace=True);
        }

    /**
     * Division: Divide each number in this array by the corresponding number in the passed array,
     * returning the resulting array.
     *
     * @param that    the array of divisors
     * @param inPlace (optional) pass True to specify that the operation should occur using `this`
     *                to hold the result, if possible
     *
     * @return the array containing the resulting quotients
     *
     * @throws IllegalMath  if the requested operation cannot be performed for any reason
     * @throws OutOfBounds  if a resulting value is out of range for this type
     */
    NumberArray divVector(NumberArray that, Boolean inPlace = False)
        {
        assert:bounds this.size == that.size;

        if (inPlace && this.inPlace)
            {
            for (Int i : 0 ..< size)
                {
                this[i] /= that[i];
                }
            return this;
            }

        return new Element[size](i -> this[i] / that[i]).toArray(mutability, inPlace=True);
        }

    /**
     * Modulo: Calculate the modulo of each number in this array divided by the passed divisor,
     * returning the modulos in the resulting array.
     *
     * @param scalar  the scalar divisor
     * @param inPlace (optional) pass True to specify that the operation should occur using `this`
     *                to hold the result, if possible
     *
     * @return the array containing the resulting modulos
     *
     * @throws IllegalMath  if the requested operation cannot be performed for any reason
     * @throws OutOfBounds  if a resulting value is out of range for this type
     */
    NumberArray modScalar(Element scalar, Boolean inPlace = False)
        {
        if (inPlace && this.inPlace)
            {
            for (Int i : 0 ..< size)
                {
                this[i] %= scalar;
                }
            return this;
            }

        return new Element[size](i -> this[i] % scalar).toArray(mutability, inPlace=True);
        }

    /**
     * Modulo: Calculate the modulo of each number in this array divided by the corresponding number
     * in the passed array, returning the modulos in the resulting array.
     *
     * @param that    the array of divisors
     * @param inPlace (optional) pass True to specify that the operation should occur using `this`
     *                to hold the result, if possible
     *
     * @return the array containing the resulting modulos
     *
     * @throws IllegalMath  if the requested operation cannot be performed for any reason
     * @throws OutOfBounds  if a resulting value is out of range for this type
     */
    NumberArray modVector(NumberArray that, Boolean inPlace = False)
        {
        assert:bounds this.size == that.size;

        if (inPlace && this.inPlace)
            {
            for (Int i : 0 ..< size)
                {
                this[i] %= that[i];
                }
            return this;
            }

        return new Element[size](i -> this[i] % that[i]).toArray(mutability, inPlace=True);
        }

    /**
     * Remainder: Calculate the remainder of each number in this array divided by the passed
     * divisor, returning the modulos in the resulting array.
     *
     * @param scalar  the scalar divisor
     * @param inPlace (optional) pass True to specify that the operation should occur using `this`
     *                to hold the result, if possible
     *
     * @return the array containing the resulting remainders
     *
     * @throws IllegalMath  if the requested operation cannot be performed for any reason
     * @throws OutOfBounds  if a resulting value is out of range for this type
     */
    NumberArray remScalar(Element scalar, Boolean inPlace = False)
        {
        if (inPlace && this.inPlace)
            {
            for (Int i : 0 ..< size)
                {
                this[i] %= scalar;
                }
            return this;
            }

        return new Element[size](i -> this[i] % scalar).toArray(mutability, inPlace=True);
        }

    /**
     * Remainder: Calculate the remainder of each number in this array divided by the corresponding
     * number in the passed array, returning the modulos in the resulting array.
     *
     * @param that    the array of divisors
     * @param inPlace (optional) pass True to specify that the operation should occur using `this`
     *                to hold the result, if possible
     *
     * @return the array containing the resulting remainders
     *
     * @throws IllegalMath  if the requested operation cannot be performed for any reason
     * @throws OutOfBounds  if a resulting value is out of range for this type
     */
    NumberArray remVector(NumberArray that, Boolean inPlace = False)
        {
        assert:bounds this.size == that.size;

        if (inPlace && this.inPlace)
            {
            for (Int i : 0 ..< size)
                {
                this[i] = this[i].remainder(that[i]);
                }
            return this;
            }

        return new Element[size](i -> this[i].remainder(that[i])).toArray(mutability, inPlace=True);
        }

    /**
     * Division and Remainder: Divide each number in this array by the passed number, returning the
     * resulting array of quotients, and a second array of remainders.
     *
     * @param scalar  the scalar divisor
     * @param inPlace (optional) pass True to specify that the operation should occur using `this`
     *                to hold the quotient results, if possible
     *
     * @return the array containing the resulting quotients
     * @return the array containing the resulting remainders
     *
     * @throws IllegalMath  if the requested operation cannot be performed for any reason
     * @throws OutOfBounds  if a resulting value is out of range for this type
     */
    (NumberArray quotients, NumberArray remainders) divremScalar(Element scalar, Boolean inPlace = False)
        {
        Element[] quotients  = inPlace && this.inPlace ? this : new Element[size](Element.zero());
        Element[] remainders = new Element[size](Element.zero());

        for (Int i : 0 ..< size)
            {
            (quotients[i], remainders[i]) = this[i] /% scalar;
            }

        return quotients, remainders;
        }

    /**
     * Division and Remainder: Divide each number in this array by the corresponding number in the
     * passed array, returning the resulting array of quotients, and a second array of remainders.
     *
     * @param that    the array of divisors
     * @param inPlace (optional) pass True to specify that the operation should occur using `this`
     *                to hold the quotient results, if possible
     *
     * @return the array containing the resulting quotients
     * @return the array containing the resulting remainders
     *
     * @throws IllegalMath  if the requested operation cannot be performed for any reason
     * @throws OutOfBounds  if a resulting value is out of range for this type
     */
    (NumberArray quotients, NumberArray remainders) divremVector(NumberArray that, Boolean inPlace = False)
        {
        assert:bounds this.size == that.size;

        Element[] quotients  = inPlace && this.inPlace ? this : new Element[size](Element.zero());
        Element[] remainders = new Element[size](Element.zero());

        for (Int i : 0 ..< size)
            {
            (quotients[i], remainders[i]) = this[i] /% that[i];
            }

        return quotients, remainders;
        }


    // ----- view support --------------------------------------------------------------------------

    /**
     * Obtain a _view_ of this array-of-numbers as an array-of-bits. The resulting array has the
     * same mutability as this array, except that the resulting array is FixedSize if this array is
     * Mutable. Conceptually, this is very similar to array delegation, except that the Element type
     * is different between the delegating array (the new Bit array) and the delegatee (this array
     * of some Number type).
     *
     * @return an array of bits that acts as a read/write view of the numeric contents of this array
     */
    Bit[] asBitArray()
        {
        assert Element.fixedBitLength();

        static class Translator<NumType extends Number>(NumType[] nums)
                implements ArrayDelegate<Bit>
            {
            construct(NumType[] nums)
                {
                this.nums = nums;
                assert this.bitsPerNum := NumType.fixedBitLength();
                }

            private NumType[] nums;
            private Int bitsPerNum;

            @Override
            Mutability mutability.get()
                {
                Mutability mut = nums.mutability;
                return mut == Mutable ? Fixed : mut;
                }

            @Override
            Int capacity
                {
                @Override
                Int get()
                    {
                    return nums.capacity * bitsPerNum;
                    }

                @Override
                void set(Int c)
                    {
                    nums.capacity = c / bitsPerNum;
                    }
                }

            @Override
            Int size.get()
                {
                return nums.size * bitsPerNum;
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
                            return nums[index/bitsPerNum].toBitArray()[index%bitsPerNum];
                            }

                        @Override
                        void set(Bit v)
                            {
                            assert:bounds index < size;
                            Int     numIndex   = index/bitsPerNum;
                            NumType oldValue   = nums[numIndex];
                            Bit[]   bits       = oldValue.toBitArray();
                            Int     bitIndex   = index%bitsPerNum;
                            if (bits[bitIndex] != v)
                                {
                                nums[numIndex] = new NumType(bits.replace(bitIndex, v));
                                }
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

        return new Array<Bit>(new Translator<Element>(this), mutability);
        }

    /**
     * Obtain a _view_ of this array-of-numbers as an array-of-nibbles. The resulting array has the
     * same mutability as this array, except that the resulting array is FixedSize if this array is
     * Mutable. Conceptually, this is very similar to array delegation, except that the Element type
     * is different between the delegating array (the new Nibble array) and the delegatee (this
     * array of some Number type).
     *
     * @return an array of nibbles that acts as a read/write view of the numeric contents of this
     *         array
     */
    Nibble[] asNibbleArray()
        {
        return asBitArray().asNibbleArray();
        }

    /**
     * Obtain a _view_ of this array-of-numbers as an array-of-bytes. The resulting array has the
     * same mutability as this array, except that the resulting array is FixedSize if this array is
     * Mutable. Conceptually, this is very similar to array delegation, except that the Element type
     * is different between the delegating array (the new Byte array) and the delegatee (this array
     * of some Number type).
     *
     * @return an array of bytes that acts as a read/write view of the numeric contents of this
     *         array
     */
    Byte[] asByteArray()
        {
        return asBitArray().asByteArray();
        }


    // ----- conversions ---------------------------------------------------------------------------

    /**
     * A second name for the [toUInt8] method, to assist with readability. By using a property
     * to alias the method, instead of creating a second delegating method, this prevents the
     * potential for accidentally overriding the wrong method.
     */
    static Method<ByteArray, <>, <Byte>> toByte = toUInt8;

    /**
     * Convert the array of numbers to a signed integer.
     *
     * @return the Int value corresponding the arrangement of bytes represented by this array
     *
     * @throws OutOfBounds  if the array of numbers does not correspond to a byte array whose size
     *                      exactly matches the size of the result
     */
    Int toInt()
        {
        return asBitArray().toInt();
        }

    /**
     * Convert the array of numbers to a signed 8-bit integer.
     *
     * @return the Int8 value corresponding the arrangement of bytes represented by this array
     *
     * @throws OutOfBounds  if the array of numbers does not correspond to a byte array whose size
     *                      exactly matches the size of the result
     */
    Int8 toInt8()
        {
        assert:bounds asBitArray().size == 8;
        return asBitArray().toInt8();
        }

    /**
     * Convert the array of numbers to a signed 16-bit integer.
     *
     * @return the Int16 value corresponding the arrangement of bytes represented by this array
     *
     * @throws OutOfBounds  if the array of numbers does not correspond to a byte array whose size
     *                      exactly matches the size of the result
     */
    Int16 toInt16()
        {
        assert:bounds asBitArray().size == 16;
        return asBitArray().toInt16();
        }

    /**
     * Convert the array of numbers to a signed 32-bit integer.
     *
     * @return the Int32 value corresponding the arrangement of bytes represented by this array
     *
     * @throws OutOfBounds  if the array of numbers does not correspond to a byte array whose size
     *                      exactly matches the size of the result
     */
    Int32 toInt32()
        {
        assert:bounds asBitArray().size == 32;
        return asBitArray().toInt32();
        }

    /**
     * Convert the array of numbers to a signed 64-bit integer.
     *
     * @return the Int64 value corresponding the arrangement of bytes represented by this array
     *
     * @throws OutOfBounds  if the array of numbers does not correspond to a byte array whose size
     *                      exactly matches the size of the result
     */
    Int64 toInt64()
        {
        assert:bounds asBitArray().size == 64;
        return asBitArray().toInt64();
        }

    /**
     * Convert the array of numbers to a signed 128-bit integer.
     *
     * @return the Int128 value corresponding the arrangement of bytes represented by this array
     *
     * @throws OutOfBounds  if the array of numbers does not correspond to a byte array whose size
     *                      exactly matches the size of the result
     */
    Int128 toInt128()
        {
        assert:bounds asBitArray().size == 128;
        return asBitArray().toInt128();
        }

    /**
     * Convert the array of numbers to a variable-length signed integer.
     *
     * @return the IntN value corresponding the arrangement of bytes represented by this array
     *
     * @throws OutOfBounds  if the array of numbers is too large to be converted to a
     *                      variable-length signed integer
     */
    IntN toIntN()
        {
        return asBitArray().toIntN();
        }

    /**
     * Convert the array of numbers to an unsigned integer.
     *
     * @return the UInt value corresponding the arrangement of bytes represented by this array
     *
     * @throws OutOfBounds  if the array of numbers does not correspond to a byte array whose size
     *                      exactly matches the size of the result
     */
    UInt toUInt()
        {
        return asBitArray().toUInt();
        }

    /**
     * Convert the array of numbers to an unsigned 8-bit integer.
     *
     * @return the UInt8 value corresponding the arrangement of bytes represented by this array
     *
     * @throws OutOfBounds  if the array of numbers does not correspond to a byte array whose size
     *                      exactly matches the size of the result
     */
    UInt8 toUInt8()
        {
        assert:bounds asBitArray().size == 8;
        return asBitArray().toUInt8();
        }

    /**
     * Convert the array of numbers to an unsigned 16-bit integer.
     *
     * @return the UInt16 value corresponding the arrangement of bytes represented by this array
     *
     * @throws OutOfBounds  if the array of numbers does not correspond to a byte array whose size
     *                      exactly matches the size of the result
     */
    UInt16 toUInt16()
        {
        assert:bounds asBitArray().size == 16;
        return asBitArray().toUInt16();
        }

    /**
     * Convert the array of numbers to an unsigned 32-bit integer.
     *
     * @return the UInt32 value corresponding the arrangement of bytes represented by this array
     *
     * @throws OutOfBounds  if the array of numbers does not correspond to a byte array whose size
     *                      exactly matches the size of the result
     */
    UInt32 toUInt32()
        {
        assert:bounds asBitArray().size == 32;
        return asBitArray().toUInt32();
        }

    /**
     * Convert the array of numbers to an unsigned 64-bit integer.
     *
     * @return the UInt64 value corresponding the arrangement of bytes represented by this array
     *
     * @throws OutOfBounds  if the array of numbers does not correspond to a byte array whose size
     *                      exactly matches the size of the result
     */
    UInt64 toUInt64()
        {
        assert:bounds asBitArray().size == 64;
        return asBitArray().toUInt64();
        }

    /**
     * Convert the array of numbers to an unsigned 128-bit integer.
     *
     * @return the UInt128 value corresponding the arrangement of bytes represented by this array
     *
     * @throws OutOfBounds  if the array of numbers does not correspond to a byte array whose size
     *                      exactly matches the size of the result
     */
    UInt128 toUInt128()
        {
        assert:bounds asBitArray().size == 128;
        return asBitArray().toUInt128();
        }

    /**
     * Convert the array of numbers to a variable-length unsigned integer.
     *
     * @return the UIntN value corresponding the arrangement of bytes represented by this array
     *
     * @throws OutOfBounds  if the array of numbers is too large to be converted to a
     *                      variable-length unsigned integer
     */
    UIntN toUIntN()
        {
        return asBitArray().toUIntN();
        }

    /**
     * Convert the array of numbers to a radix-10 (decimal) floating point number.
     *
     * @return the Dec value corresponding the arrangement of bytes represented by this array
     *
     * @throws OutOfBounds  if the array of numbers does not correspond to a byte array whose size
     *                      exactly matches the size of the result
     */
    Dec toDec()
        {
        Bit[] bits = asBitArray();
        Int   len  = bits.size;
        assert len == 28 || len == 32 || len == 60 || len == 64 || len == 128;

        return bits.toDec();
        }

    /**
     * Convert the array of numbers to a 32-bit radix-10 (decimal) floating point number.
     *
     * @return the Dec32 value corresponding the arrangement of bytes represented by this array
     *
     * @throws OutOfBounds  if the array of numbers does not correspond to a byte array whose size
     *                      exactly matches the size of the result
     */
    Dec32 toDec32()
        {
        assert:bounds asBitArray().size == 32;
        return asBitArray().toDec32();
        }

    /**
     * Convert the array of numbers to a 64-bit radix-10 (decimal) floating point number.
     *
     * @return the Dec64 value corresponding the arrangement of bytes represented by this array
     *
     * @throws OutOfBounds  if the array of numbers does not correspond to a byte array whose size
     *                      exactly matches the size of the result
     */
    Dec64 toDec64()
        {
        assert:bounds asBitArray().size == 64;
        return asBitArray().toDec64();
        }

    /**
     * Convert the array of numbers to a 128-bit radix-10 (decimal) floating point number.
     *
     * @return the Dec128 value corresponding the arrangement of bytes represented by this array
     *
     * @throws OutOfBounds  if the array of numbers does not correspond to a byte array whose size
     *                      exactly matches the size of the result
     */
    Dec128 toDec128()
        {
        assert:bounds asBitArray().size == 128;
        return asBitArray().toDec128();
        }

    /**
     * Convert the array of numbers to a variable-length decimal radix floating point number.
     *
     * @return the DecN value corresponding the arrangement of bytes represented by this array
     *
     * @throws OutOfBounds  if the array of numbers does not correspond to a byte array whose size
     *                      is a legal size for a decimal radix floating point number
     */
    DecN toDecN()
        {
        return asBitArray().toDecN();
        }

    /**
     * Convert the array of numbers to a 8-bit radix-2 (binary) "E4M3" floating point number.
     *
     * @return the Float8e4 value corresponding the arrangement of bytes represented by this array
     *
     * @throws OutOfBounds  if the array of numbers does not correspond to a byte array whose size
     *                      exactly matches the size of the result
     */
    Float8e4 toFloat8e4()
        {
        assert:bounds asBitArray().size == 8;
        return asBitArray().toFloat8e4();
        }

    /**
     * Convert the array of numbers to a 8-bit radix-2 (binary) "E5M2" floating point number.
     *
     * @return the Float8e5 value corresponding the arrangement of bytes represented by this array
     *
     * @throws OutOfBounds  if the array of numbers does not correspond to a byte array whose size
     *                      exactly matches the size of the result
     */
    Float8e5 toFloat8e5()
        {
        assert:bounds asBitArray().size == 8;
        return asBitArray().toFloat8e5();
        }

    /**
     * Convert the array of numbers to a 16-bit radix-2 (binary) "brain" floating point number.
     *
     * @return the BFloat16 value corresponding the arrangement of bytes represented by this array
     *
     * @throws OutOfBounds  if the array of numbers does not correspond to a byte array whose size
     *                      exactly matches the size of the result
     */
    BFloat16 toBFloat16()
        {
        assert:bounds asBitArray().size == 16;
        return asBitArray().toBFloat16();
        }

    /**
     * Convert the array of numbers to a 16-bit radix-2 (binary) floating point number.
     *
     * @return the Float16 value corresponding the arrangement of bytes represented by this array
     *
     * @throws OutOfBounds  if the array of numbers does not correspond to a byte array whose size
     *                      exactly matches the size of the result
     */
    Float16 toFloat16()
        {
        assert:bounds asBitArray().size == 16;
        return asBitArray().toFloat16();
        }

    /**
     * Convert the array of numbers to a 32-bit radix-2 (binary) floating point number.
     *
     * @return the Float32 value corresponding the arrangement of bytes represented by this array
     *
     * @throws OutOfBounds  if the array of numbers does not correspond to a byte array whose size
     *                      exactly matches the size of the result
     */
    Float32 toFloat32()
        {
        assert:bounds asBitArray().size == 32;
        return asBitArray().toFloat32();
        }

    /**
     * Convert the array of numbers to a 64-bit radix-2 (binary) floating point number.
     *
     * @return the Float64 value corresponding the arrangement of bytes represented by this array
     *
     * @throws OutOfBounds  if the array of numbers does not correspond to a byte array whose size
     *                      exactly matches the size of the result
     */
    Float64 toFloat64()
        {
        assert:bounds asBitArray().size == 64;
        return asBitArray().toFloat64();
        }

    /**
     * Convert the array of numbers to a 128-bit radix-2 (binary) floating point number.
     *
     * @return the Float128 value corresponding the arrangement of bytes represented by this array
     *
     * @throws OutOfBounds  if the array of numbers does not correspond to a byte array whose size
     *                      exactly matches the size of the result
     */
    Float128 toFloat128()
        {
        assert:bounds asBitArray().size == 128;
        return asBitArray().toFloat128();
        }

    /**
     * Convert the array of numbers to a variable-length binary radix floating point number.
     *
     * @return the FloatN value corresponding the arrangement of bytes represented by this array
     *
     * @throws OutOfBounds  if the array of numbers does not correspond to a byte array whose size
     *                      is a legal size for a binary radix floating point number
     */
    FloatN toFloatN()
        {
        return asBitArray().toFloatN();
        }

    /**
     * Convert the array of numbers to an array of bits.
     *
     * @param mutability  the desired mutability for the resulting array, defaulting to constant
     *
     * @return an array of bits corresponding to the numbers in this array
     */
    Bit[] toBitArray(Mutability mutability = Constant)
        {
        return asBitArray().reify(mutability);
        }

    /**
     * Obtain a copy of this array's data that exposes the underlying data as Nibble values, each
     * composed of 4 Bit values.
     *
     * @param mutability  the desired mutability for the resulting array, defaulting to constant
     *
     * @return an array of nibbles corresponding to the bits in this array
     */
    Nibble[] toNibbleArray(Mutability mutability = Constant)
        {
        return asNibbleArray().reify(mutability);
        }

    /**
     * Obtain a copy of this array's data that exposes the underlying data as Byte values, each
     * composed of 8 Bit values.
     *
     * @param mutability  the desired mutability for the resulting array, defaulting to constant
     *
     * @return an array of bytes corresponding to the bits in this array
     */
    Byte[] toByteArray(Mutability mutability = Constant)
        {
        return asByteArray().reify(mutability);
        }
    }