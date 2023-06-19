import numbers.IntConvertible;
import numbers.FPConvertible;


/**
 * Functionality specific to arrays of numbers.
 */
mixin NumberArray<Element extends Number>
        into Array<Element>
        implements IntConvertible
        implements FPConvertible {
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
    NumberArray negVector(Boolean inPlace = False) {
        if (inPlace && this.inPlace) {
            for (Int i : 0 ..< size) {
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
    NumberArray addScalar(Element scalar, Boolean inPlace = False) {
        if (inPlace && this.inPlace) {
            for (Int i : 0 ..< size) {
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
    NumberArray addVector(NumberArray that, Boolean inPlace = False) {
        assert:bounds this.size == that.size;

        if (inPlace && this.inPlace) {
            for (Int i : 0 ..< size) {
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
    NumberArray subScalar(Element scalar, Boolean inPlace = False) {
        if (inPlace && this.inPlace) {
            for (Int i : 0 ..< size) {
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
    NumberArray subVector(NumberArray that, Boolean inPlace = False) {
        assert:bounds this.size == that.size;

        if (inPlace && this.inPlace) {
            for (Int i : 0 ..< size) {
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
    NumberArray mulScalar(Element scalar, Boolean inPlace = False) {
        if (inPlace && this.inPlace) {
            for (Int i : 0 ..< size) {
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
    NumberArray mulVector(NumberArray that, Boolean inPlace = False) {
        assert:bounds this.size == that.size;

        if (inPlace && this.inPlace) {
            for (Int i : 0 ..< size) {
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
    NumberArray divScalar(Element scalar, Boolean inPlace = False) {
        if (inPlace && this.inPlace) {
            for (Int i : 0 ..< size) {
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
    NumberArray divVector(NumberArray that, Boolean inPlace = False) {
        assert:bounds this.size == that.size;

        if (inPlace && this.inPlace) {
            for (Int i : 0 ..< size) {
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
    NumberArray modScalar(Element scalar, Boolean inPlace = False) {
        if (inPlace && this.inPlace) {
            for (Int i : 0 ..< size) {
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
    NumberArray modVector(NumberArray that, Boolean inPlace = False) {
        assert:bounds this.size == that.size;

        if (inPlace && this.inPlace) {
            for (Int i : 0 ..< size) {
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
    NumberArray remScalar(Element scalar, Boolean inPlace = False) {
        if (inPlace && this.inPlace) {
            for (Int i : 0 ..< size) {
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
    NumberArray remVector(NumberArray that, Boolean inPlace = False) {
        assert:bounds this.size == that.size;

        if (inPlace && this.inPlace) {
            for (Int i : 0 ..< size) {
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
    (NumberArray quotients, NumberArray remainders) divremScalar(Element scalar, Boolean inPlace = False) {
        Element[] quotients  = inPlace && this.inPlace ? this : new Element[size](Element.zero());
        Element[] remainders = new Element[size](Element.zero());

        for (Int i : 0 ..< size) {
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
    (NumberArray quotients, NumberArray remainders) divremVector(NumberArray that, Boolean inPlace = False) {
        assert:bounds this.size == that.size;

        Element[] quotients  = inPlace && this.inPlace ? this : new Element[size](Element.zero());
        Element[] remainders = new Element[size](Element.zero());

        for (Int i : 0 ..< size) {
            (quotients[i], remainders[i]) = this[i] /% that[i];
        }

        return quotients, remainders;
    }


    // ----- aggregations --------------------------------------------------------------------------

    /**
     * Compute the minimal value in this array.
     *
     * @return True iff the array is not empty
     * @return (optional) the minimum element value
     */
    conditional Element min() {
        if ((Element min, _) := range()) {
            return True, min;
        }
        return False;
    }

    /**
     * Compute the maximal value in this array.
     *
     * @return True iff the array is not empty
     * @return (optional) the maximum element value
     */
    conditional Element max() {
        if ((_, Element max) := range()) {
            return True, max;
        }
        return False;
    }

    /**
     * Compute the range of values in this array.
     *
     * @return True iff the array is not empty
     * @return (optional) the minimum value
     * @return (optional) the maximum value
     */
    conditional (Element min, Element max) range() {
        if (empty) {
            return False;
        }

        Element min = this[0];
        Element max = min;
        for (Int i : 1 ..< size) {
            Element next = this[i];
            if (next < min) {
                min = next;
            } else if (next > max) {
                max = next;
            }
        }
        return True, min, max;
    }

    /**
     * Compute the sum of values in this array.
     *
     * @return True iff the array is not empty
     * @return (optional) the sum of element values
     */
    conditional Element sum() {
        if (empty) {
            return False;
        }

        Element sum = this[0];
        for (Int i : 1 ..< size) {
            sum += this[i];
        }
        return True, sum;
    }

    /**
     * Compute the median of values in this array using the [Quickselect algorithm]
     * (https://en.wikipedia.org/wiki/Quickselect).
     *
     * @return True iff the array is not empty
     * @return (optional) the median of element values
     */
    conditional Element median() {
        Int n = size;

        if (n == 0) {
            return False;
        }

        // this algorrithm partially sorts the array, so we need a mutable copy
        NumberArray array = toArray(Mutable, inPlace=False);
        (Element? a, Element? b) = array.median(Null, Null, 0, n - 1, n / 2);

        if (n % 2 == 0) {
            // even case
            Element median = (a? + b?) : assert;
            Element one    = Element.one();
            return True, median / (one + one);
        } else {
            // odd case
            return True, (b? : assert);
        }
    }

    /**
     * The implementation of the [Quickselect algorithm] (https://en.wikipedia.org/wiki/Quickselect)
     * computing the k-th smallest element of the array.
     */
    protected (Element? a, Element? b) median(Element? a, Element? b, Int l, Int r, Int k) {
        if (l <= r) {
            Int partitionIndex = partition(l, r);

            if (partitionIndex == k) {
                // we found the median of an odd number of elements
                b = this[partitionIndex];
                if (a != Null) {
                    return a, b;
                }
            } else if (partitionIndex == k - 1) {
                // we get a & b as middle element of the array
                a = this[partitionIndex];
                if (b != Null) {
                    return a, b;
                }
            }

            if (partitionIndex >= k) {
                // find the index in first half
                return median(a, b, l, partitionIndex - 1, k);
            } else {
                return median(a, b, partitionIndex + 1, r, k);
            }
        }
        return a, b;

        Int partition(Int l, Int r) {
            Element pivot = this[r];
            Int i = l;
            Int j = l;
            while (j < r) {
                if (this[j] < pivot) {
                    swap(i, j);
                    i++;
                }
                j++;
            }
            swap(i, r);
            return i;
        }
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
    Bit[] asBitArray() {
        assert Element.fixedBitLength();

        static class Translator<NumType extends Number>(NumType[] nums)
                implements ArrayDelegate<Bit> {

            construct(NumType[] nums) {
                this.nums = nums;
                assert this.bitsPerNum := NumType.fixedBitLength();
            }

            @Override
            construct(Translator that) {
                // this does not duplicate any underling content of the array; it's just duplicating
                // the translator itself
                this.nums       = that.nums;
                this.bitsPerNum = that.bitsPerNum;
            }

            private NumType[] nums;
            private Int bitsPerNum;

            @Override
            Translator duplicate() {
                return this;
            }

            @Override
            Mutability mutability.get() {
                Mutability mut = nums.mutability;
                return mut == Mutable ? Fixed : mut;
            }

            @Override
            Int capacity {
                @Override
                Int get() {
                    return nums.capacity * bitsPerNum;
                }

                @Override
                void set(Int c) {
                    nums.capacity = c / bitsPerNum;
                }
            }

            @Override
            Int size.get() {
                return nums.size * bitsPerNum;
            }

            @Override
            Var<Bit> elementAt(Int index) {
                assert:bounds 0 <= index < size;

                return new Object() {
                    Bit element {
                        @Override
                        Boolean assigned.get() {
                            return index < size;
                        }

                        @Override
                        Bit get() {
                            assert:bounds index < size;
                            return nums[index/bitsPerNum].toBitArray()[index%bitsPerNum];
                        }

                        @Override
                        void set(Bit v) {
                            assert:bounds index < size;
                            Int     numIndex   = index/bitsPerNum;
                            NumType oldValue   = nums[numIndex];
                            Bit[]   bits       = oldValue.toBitArray();
                            Int     bitIndex   = index%bitsPerNum;
                            if (bits[bitIndex] != v) {
                                nums[numIndex] = new NumType(bits.replace(bitIndex, v));
                            }
                        }
                    }
                }.&element;
            }

            @Override
            Translator insert(Int index, Bit value) {
                throw new ReadOnly();
            }

            @Override
            Translator delete(Int index) {
                throw new ReadOnly();
            }

            @Override
            Bit[] reify(Mutability? mutability = Null) {
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
    Nibble[] asNibbleArray() {
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
    Byte[] asByteArray() {
        return asBitArray().asByteArray();
    }


    // ----- conversions ---------------------------------------------------------------------------

    @Override
    Int toInt(Boolean truncate = False, Rounding direction = TowardZero) {
        return asBitArray().toInt(truncate, direction);
    }

    @Override
    Int8 toInt8(Boolean truncate = False, Rounding direction = TowardZero) {
        return asBitArray().toInt8(truncate, direction);
    }

    @Override
    Int16 toInt16(Boolean truncate = False, Rounding direction = TowardZero) {
        return asBitArray().toInt16(truncate, direction);
    }

    @Override
    Int32 toInt32(Boolean truncate = False, Rounding direction = TowardZero) {
        return asBitArray().toInt32(truncate, direction);
    }

    @Override
    Int64 toInt64(Boolean truncate = False, Rounding direction = TowardZero) {
        return asBitArray().toInt64(truncate, direction);
    }

    @Override
    Int128 toInt128(Boolean truncate = False, Rounding direction = TowardZero) {
        return asBitArray().toInt128(truncate, direction);
    }

    @Override
    IntN toIntN(Rounding direction = TowardZero) {
        return asBitArray().toIntN(direction);
    }

    @Override
    UInt toUInt(Boolean truncate = False, Rounding direction = TowardZero) {
        return asBitArray().toUInt(truncate, direction);
    }

    @Override
    UInt8 toUInt8(Boolean truncate = False, Rounding direction = TowardZero) {
        return asBitArray().toUInt8(truncate, direction);
    }

    @Override
    UInt16 toUInt16(Boolean truncate = False, Rounding direction = TowardZero) {
        return asBitArray().toUInt16(truncate, direction);
    }

    @Override
    UInt32 toUInt32(Boolean truncate = False, Rounding direction = TowardZero) {
        return asBitArray().toUInt32(truncate, direction);
    }

    @Override
    UInt64 toUInt64(Boolean truncate = False, Rounding direction = TowardZero) {
        return asBitArray().toUInt64(truncate, direction);
    }

    @Override
    UInt128 toUInt128(Boolean truncate = False, Rounding direction = TowardZero) {
        return asBitArray().toUInt128(truncate, direction);
    }

    @Override
    UIntN toUIntN(Rounding direction = TowardZero) {
        return asBitArray().toUIntN(direction);
    }

    @Override
    Dec toDec() {
        Bit[] bits = asBitArray();
        Int   len  = bits.size;
        assert len == 28 || len == 32 || len == 60 || len == 64 || len == 128;

        return bits.toDec();
    }

    @Override
    Dec32 toDec32() {
        assert:bounds asBitArray().size == 32;
        return asBitArray().toDec32();
    }

    @Override
    Dec64 toDec64() {
        assert:bounds asBitArray().size == 64;
        return asBitArray().toDec64();
    }

    @Override
    Dec128 toDec128() {
        assert:bounds asBitArray().size == 128;
        return asBitArray().toDec128();
    }

    @Override
    DecN toDecN() {
        return asBitArray().toDecN();
    }

    @Override
    Float8e4 toFloat8e4() {
        assert:bounds asBitArray().size == 8;
        return asBitArray().toFloat8e4();
    }

    @Override
    Float8e5 toFloat8e5() {
        assert:bounds asBitArray().size == 8;
        return asBitArray().toFloat8e5();
    }

    @Override
    BFloat16 toBFloat16() {
        assert:bounds asBitArray().size == 16;
        return asBitArray().toBFloat16();
    }

    @Override
    Float16 toFloat16() {
        assert:bounds asBitArray().size == 16;
        return asBitArray().toFloat16();
    }

    @Override
    Float32 toFloat32() {
        assert:bounds asBitArray().size == 32;
        return asBitArray().toFloat32();
    }

    @Override
    Float64 toFloat64() {
        assert:bounds asBitArray().size == 64;
        return asBitArray().toFloat64();
    }

    @Override
    Float128 toFloat128() {
        assert:bounds asBitArray().size == 128;
        return asBitArray().toFloat128();
    }

    @Override
    FloatN toFloatN() {
        return asBitArray().toFloatN();
    }

    /**
     * Convert the array of numbers to an array of bits.
     *
     * @param mutability  the desired mutability for the resulting array, defaulting to constant
     *
     * @return an array of bits corresponding to the numbers in this array
     */
    Bit[] toBitArray(Mutability mutability = Constant) {
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
    Nibble[] toNibbleArray(Mutability mutability = Constant) {
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
    Byte[] toByteArray(Mutability mutability = Constant) {
        return asByteArray().reify(mutability);
    }
}