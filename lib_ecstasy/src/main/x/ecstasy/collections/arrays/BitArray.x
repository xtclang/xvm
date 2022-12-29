import numbers.IntConvertible;
import numbers.FPConvertible;


/**
 * Functionality specific to an array of bits.
 */
mixin BitArray<Element extends Bit>
        into Array<Element>
        implements IntConvertible
        implements FPConvertible
    {
    construct()
        {
        assert Class<Element> clz := Element.fromClass(), Element dft := clz.defaultValue();
        Zero = dft;
        One  = ~Zero;
        }

    private Element Zero;
    private Element One;


    // ----- bitwise operations --------------------------------------------------------------------

    /**
     * Bitwise AND.
     *
     * @param that    an identically sized bit array
     * @param inPlace (optional) pass True to specify that the operation should occur using `this`
     *                to hold the result, if possible
     *
     * @return the resulting array, which _may_ be `this` iff inPlace is `True`
     *
     * @throws OutOfBounds  if the array sizes do not match
     */
    @Op("&")
    BitArray and(Bit![] that, Boolean inPlace = False)
        {
        assert:bounds this.size == that.size;

// TODO?
//        return inPlace && this.inPlace
//                ? this.mapIndexed((e, i) -> e & that[i], this)
//                : new Element[size](i -> this[i] & that[i]).toArray(mutability, inPlace=True);

        if (inPlace && this.inPlace)
            {
            for (Int i : 0 ..< size)
                {
                this[i] &= that[i];
                }
            return this;
            }

        return new Element[size](i -> this[i] & that[i]).toArray(mutability, inPlace=True);
        }

    /**
     * Bitwise OR.
     *
     * @param that    an identically sized bit array
     * @param inPlace (optional) pass True to specify that the operation should occur using `this`
     *                to hold the result, if possible
     *
     * @return the resulting array, which _may_ be `this` iff inPlace is `True`
     *
     * @throws OutOfBounds  if the array sizes do not match
     */
    @Op("|")
    BitArray or(Bit![] that, Boolean inPlace = False)
        {
        assert:bounds this.size == that.size;

        if (inPlace && this.inPlace)
            {
            for (Int i : 0 ..< size)
                {
                this[i] |= that[i];
                }
            return this;
            }

        return new Element[size](i -> this[i] | that[i]).toArray(mutability, inPlace=True);
        }

    /**
     * Bitwise XOR.
     *
     * @param that    an identically sized bit array
     * @param inPlace (optional) pass True to specify that the operation should occur using `this`
     *                to hold the result, if possible
     *
     * @return the resulting array, which _may_ be `this` iff inPlace is `True`
     *
     * @throws OutOfBounds  if the array sizes do not match
     */
    @Op("^")
    BitArray xor(Bit![] that, Boolean inPlace = False)
        {
        assert:bounds this.size == that.size;

        if (inPlace && this.inPlace)
            {
            for (Int i : 0 ..< size)
                {
                this[i] ^= that[i];
                }
            return this;
            }

        return new Element[size](i -> this[i] ^ that[i]).toArray(mutability, inPlace=True);
        }

    /**
     * Bitwise NOT.
     *
     * @param inPlace (optional) pass True to specify that the operation should occur using `this`
     *                to hold the result, if possible
     *
     * @return the resulting array, which _may_ be `this` iff inPlace is `True`
     */
    @Op("~")
    BitArray not(Boolean inPlace = False)
        {
        if (inPlace && this.inPlace)
            {
            for (Int i : 0 ..< size)
                {
                this[i] = ~this[i];
                }
            return this;
            }

        return new Element[size](i -> ~this[i]).toArray(mutability, inPlace=True);
        }

    /**
     * Shift bits left.
     *
     * For bit arrays representing both signed and unsigned integer values, this is both a logical
     * left shift and arithmetic left shift.
     *
     * @param count   the number of places to shift
     * @param inPlace (optional) pass True to specify that the operation should occur using `this`
     *                to hold the result, if possible
     *
     * @return the resulting array, which _may_ be `this` iff inPlace is `True`
     */
    @Op("<<")
    BitArray shiftLeft(Int count, Boolean inPlace = False)
        {
        if (count < 0)
            {
            // shift left fills the right side with zeros, so a negative shift does the same thing
            // in reverse
            return shiftAllRight(-count, inPlace);
            }

        if (inPlace && this.inPlace)
            {
            if (count > 0)
                {
                if (count < size)
                    {
                    for (Int i : 0 ..< count)
                        {
                        this[i] = this[i+count];
                        }
                    }

                fill(Zero, (size-count).maxOf(0) ..< size);
                }

            return this;
            }

        return new Element[size](i -> (i < size-count ? this[i + count] : Zero))
                .toArray(mutability, inPlace=True);
        }

    /**
     * Shift bits right, dragging the leftmost bit.
     *
     * For bit arrays representing signed integer values, this is an arithmetic right shift. For
     * bit arrays representing unsigned integer values, this is both a logical right shift and
     * arithmetic right shift.
     *
     * @param count   the number of places to shift
     * @param inPlace (optional) pass True to specify that the operation should occur using `this`
     *                to hold the result, if possible
     *
     * @return the resulting array, which _may_ be `this` iff inPlace is `True`
     */
    @Op(">>")
    BitArray shiftRight(Int count, Boolean inPlace = False)
        {
        if (count < 0)
            {
            return shiftLeft(-count, inPlace);
            }

        if (inPlace && this.inPlace)
            {
            if (count > 0)
                {
                if (count < size)
                    {
                    for (Int i : size >.. count)
                        {
                        this[i] = this[i-count];
                        }
                    }

                fill(this[0], 1 .. count.minOf(size-1));
                }

            return this;
            }

        return new Element[size](i -> (i < count ? this[0] : this[i - count]))
                .toArray(mutability, inPlace=True);
        }

    /**
     * "Unsigned" shift bits right.
     *
     * For bit arrays representing signed integer values, this is a logical right shift. For bit
     * arrays representing unsigned integer values, this is both a logical right shift and
     * an arithmetic right shift.
     *
     * @param count   the number of places to shift
     * @param inPlace (optional) pass True to specify that the operation should occur using `this`
     *                to hold the result, if possible
     *
     * @return the resulting array, which _may_ be `this` iff inPlace is `True`
     */
    @Op(">>>")
    BitArray shiftAllRight(Int count, Boolean inPlace = False)
        {
        if (count < 0)
            {
            return shiftLeft(-count, inPlace);
            }

        if (inPlace && this.inPlace)
            {
            if (count > 0)
                {
                if (count < size)
                    {
                    for (Int i : size >.. count)
                        {
                        this[i] = this[i-count];
                        }
                    }

                fill(Zero, 0 .. count.minOf(size-1));
                }

            return this;
            }

        return new Element[size](i -> (i < count ? Zero : this[i - count]))
                .toArray(mutability, inPlace=True);
        }

    /**
     * Rotate bits left.
     *
     * @param count   the number of places to shift
     * @param inPlace (optional) pass True to specify that the operation should occur using `this`
     *                to hold the result, if possible
     *
     * @return the resulting array, which _may_ be `this` iff inPlace is `True`
     */
    BitArray rotateLeft(Int count, Boolean inPlace = False)
        {
        // negative spin is going in the other direction
        if (count < 0)
            {
            return rotateRight(-count, inPlace);
            }

        // normalize count to the actual size of the array
        count = empty ? 0 : count % size;

        // the cutoff is the point at which the bits that fall off the left side go onto the right
        // side
        val cutoff = size - count;

        if (inPlace && this.inPlace)
            {
            if (count > 0)
                {
                // save off the portion that is going to rotate off of the left side
                Element[] orig = new Element[count](i -> this[i]);

                for (Int i : 0 ..< cutoff)
                    {
                    this[i] = this[i+count];
                    }

                for (Int i : 0 ..< count)
                    {
                    this[i+cutoff] = orig[i];
                    }
                }

            return this;
            }

        return new Element[size](i -> this[i < cutoff ? i+count : i-cutoff])
                .toArray(mutability, inPlace=True);
        }

    /**
     * Rotate bits right.
     *
     * @param count   the number of places to shift
     * @param inPlace (optional) pass True to specify that the operation should occur using `this`
     *                to hold the result, if possible
     *
     * @return the resulting array, which _may_ be `this` iff inPlace is `True`
     */
    BitArray rotateRight(Int count, Boolean inPlace = False)
        {
        // negative spin is going in the other direction
        if (count < 0)
            {
            return rotateLeft(-count, inPlace);
            }

        // normalize count to the actual size of the array
        count = empty ? 0 : count % size;

        // the cutoff is the point at which the bits that fall off the right side go onto the left
        // side
        val cutoff = size - count;

        if (inPlace && this.inPlace)
            {
            if (count > 0)
                {
                // save off the portion that is going to rotate off of the right side
                Element[] orig = new Element[count](i -> this[cutoff+i]);

                for (Int i : size >.. count)
                    {
                    this[i] = this[i-count];
                    }

                for (Int i : 0 ..< count)
                    {
                    this[i] = orig[i];
                    }
                }

            return this;
            }

        return new Element[size](i -> this[i < count ? i+cutoff : i-count])
                .toArray(mutability, inPlace=True);
        }

    /**
     * If any bits are set in this bit array, then return the **index** of the most significant
     * (left-most) of those bits set.
     *
     * @return True if any bits are set
     * @return (conditional) the index of the left-most bit
     */
    conditional Int leftmostBit()
        {
        Loop: for (Bit bit : this)
            {
            if (bit == 1)
                {
                return True, Loop.count;
                }
            }

        return False;
        }

    /**
     * If any bits are set in this bit array, then return the **index** of the least significant
     * (right-most) of those bits set.
     *
     * @return True if any bits are set
     * @return (conditional) the index of the right-most bit
     */
    conditional Int rightmostBit()
        {
        for (Int i : size >.. 0)
            {
            if (this[i] == 1)
                {
                return True, i;
                }
            }

        return False;
        }

    /**
     * The number of bits that are zero preceding the most significant (left-most) `1` bit.
     * This scans from left-to-right (most significant to least significant).
     */
    Int leadingZeroCount.get()
        {
        for (Int count : 0 ..< size)
            {
            if (this[count] == 1)
                {
                return count;
                }
            }
        return size;
        }

    /**
     * The number of bits that are zero following the least significant (right-most) `1` bit.
     * This scans from right-to-left (least significant to most significant).
     *
     * For a bit array with `bitCount==1`, this provides the log2 value of the integer represented
     * by the bit array.
     */
    Int trailingZeroCount.get()
        {
        for (Int count : 0 ..< size)
            {
            if (this[size - count - 1] == 1)
                {
                return count;
                }
            }
        return size;
        }

    /**
     * The number of bits that are set (non-zero) in the bit array. This is also referred to as a
     * _population count_, or `POPCNT`.
     */
    Int bitCount.get()
        {
        Int count = 0;
        for (Bit bit : this)
            {
            if (bit == 1)
                {
                ++count;
                }
            }
        return count;
        }


    // ----- view support --------------------------------------------------------------------------

    /**
     * Obtain a _view_ of this array-of-bits as an array-of-booleans. The resulting array has the
     * same mutability as this array. Conceptually, this is very similar to array delegation, except
     * that the Element type is different between the delegating array (the new Boolean array) and
     * the delegatee (this Bit array).
     *
     * @return an array of Booleans that acts as a read/write view of the Bit contents of this array
     */
    Boolean[] asBooleanArray()
        {
        static class Translator(Bit[] bits)
                implements ArrayDelegate<Boolean>
            {
            private Bit[] bits;

            @Override
            Mutability mutability.get()
                {
                return bits.mutability;
                }

            @Override
            Int capacity
                {
                @Override
                Int get()
                    {
                    return bits.capacity;
                    }

                @Override
                void set(Int c)
                    {
                    bits.capacity = c;
                    }
                }

            @Override
            Int size.get()
                {
                return bits.size;
                }

            @Override
            Var<Boolean> elementAt(Int index)
                {
                assert:bounds 0 <= index < size;

                return new Object()
                    {
                    Boolean element
                        {
                        @Override
                        Boolean assigned.get()
                            {
                            return index < bits.size;
                            }

                        @Override
                        Boolean get()
                            {
                            return bits[index].toBoolean();
                            }

                        @Override
                        void set(Boolean v)
                            {
                            bits[index] = v.toBit();
                            }
                        }
                    }.&element;
                }

            @Override
            Translator insert(Int index, Boolean value)
                {
                Bit[] newBits = bits.insert(index, value.toBit());
                return &bits == &newBits ? this : new Translator(newBits);
                }

            @Override
            Translator delete(Int index)
                {
                Bit[] newBits = bits.delete(index);
                return &bits == &newBits ? this : new Translator(newBits);
                }

            @Override
            Boolean[] reify(Mutability? mutability = Null)
                {
                mutability ?:= this.mutability;
                return new Boolean[size](i -> elementAt(i).get()).toArray(mutability, inPlace=True);
                }
            }

        return new Array<Boolean>(new Translator(this), mutability);
        }

    /**
     * Obtain a _view_ of this array-of-bits as an array-of-nibbles. The resulting array has the
     * same mutability as this array. Conceptually, this is very similar to array delegation, except
     * that the Element type is different between the delegating array (the new Nibble array) and
     * the delegatee (this Bit array).
     *
     * @return an array of nibbles that acts as a read/write view of the contents of this bit array
     */
    Nibble[] asNibbleArray()
        {
        assert:bounds size % 4 == 0;

        static class Translator(Bit[] bits)
                implements ArrayDelegate<Nibble>
            {
            private Bit[] bits;

            @Override
            Mutability mutability.get()
                {
                return bits.mutability;
                }

            @Override
            Int capacity
                {
                @Override
                Int get()
                    {
                    return bits.capacity / 4;
                    }

                @Override
                void set(Int c)
                    {
                    bits.capacity = c * 4;
                    }
                }

            @Override
            Int size.get()
                {
                (Int nibbles, Int remain) = bits.size /% 4;
                assert remain == 0;
                return nibbles;
                }

            @Override
            Var<Nibble> elementAt(Int index)
                {
                assert:bounds 0 <= index < size;

                return new Object()
                    {
                    Nibble element
                        {
                        @Override
                        Boolean assigned.get()
                            {
                            return index < size;
                            }

                        @Override
                        Nibble get()
                            {
                            assert:bounds assigned;
                            val offset = index * 4;
                            return bits[offset ..< offset+4].toNibble();
                            }

                        @Override
                        void set(Nibble v)
                            {
                            if (v != get())
                                {
                                Bit[] newBits = v.toBitArray();
                                Int   offset  = index * 4;
                                for (Int i : 0 ..< 4)
                                    {
                                    bits[offset+i] = newBits[i];
                                    }
                                }
                            }
                        }
                    }.&element;
                }

            @Override
            Translator insert(Int index, Nibble value)
                {
                Bit[] newBits = bits.insertAll(index * 4, value.toBitArray());
                return &bits == &newBits ? this : new Translator(newBits);
                }

            @Override
            Translator delete(Int index)
                {
                Int offset = index * 4;
                Bit[] newBits = bits.deleteAll(offset ..< offset+4);
                return &bits == &newBits ? this : new Translator(newBits);
                }

            @Override
            Nibble[] reify(Mutability? mutability = Null)
                {
                mutability ?:= this.mutability;
                return new Nibble[size](i -> elementAt(i).get()).toArray(mutability, inPlace=True);
                }
            }

        return new Array<Nibble>(new Translator(this), mutability);
        }

    /**
     * Obtain a _view_ of this array-of-bits as an array-of-bytes. The resulting array has the same
     * mutability as this array. Conceptually, this is very similar to array delegation, except that
     * the Element type is different between the delegating array (the new Byte array) and the
     * delegatee (this Bit array).
     *
     * @return an array of bytes that acts as a read/write view of the contents of this bit array
     */
    Byte[] asByteArray()
        {
        assert:bounds size % 8 == 0;

        static class Translator(Bit[] bits)
                implements ArrayDelegate<Byte>
            {
            private Bit[] bits;

            @Override
            Mutability mutability.get()
                {
                return bits.mutability;
                }

            @Override
            Int capacity
                {
                @Override
                Int get()
                    {
                    return bits.capacity / 8;
                    }

                @Override
                void set(Int c)
                    {
                    bits.capacity = c * 8;
                    }
                }

            @Override
            Int size.get()
                {
                (Int bytes, Int remain) = bits.size /% 8;
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
                            assert:bounds index < size;
                            val offset = index * 8;
                            return bits[offset ..< offset+8].toByte();
                            }

                        @Override
                        void set(Byte v)
                            {
                            if (v != get())
                                {
                                Bit[] newBits = v.toBitArray();
                                Int   offset  = index * 8;
                                for (Int i : 0 ..< 8)
                                    {
                                    bits[offset+i] = newBits[i];
                                    }
                                }
                            }
                        }
                    }.&element;
                }

            @Override
            Translator insert(Int index, Byte value)
                {
                Bit[] newBits = bits.insertAll(index * 8, value.toBitArray());
                return &bits == &newBits ? this : new Translator(newBits);
                }

            @Override
            Translator delete(Int index)
                {
                Int offset = index * 8;
                Bit[] newBits = bits.deleteAll(offset ..< offset+8);
                return &bits == &newBits ? this : new Translator(newBits);
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
     * Obtain a copy of this array's data that exposes the underlying data as Boolean values instead
     * of Bit values.
     *
     * @param mutability  the desired mutability for the resulting array, defaulting to constant
     *
     * @return an array of booleans corresponding to the bits in this array
     */
    Boolean[] toBooleanArray(Mutability mutability = Constant)
        {
        return asBooleanArray().reify(mutability);
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

    /**
     * Convert the bit array to a nibble.
     *
     * @throws OutOfBounds  if the resulting value is out of the signed 4-bit nibble range
     */
    Nibble toNibble()
        {
        switch (size <=> 4)
            {
            case Lesser:
                val cutoff = 4 - size;
                return empty ? 0 : new Nibble(new Element[4](i -> i < cutoff ? Zero : this[i - cutoff]));

            case Equal:
                return new Nibble(this);

            case Greater:
                assert:bounds !this[0 ..< size-4].contains(One);
                return new Nibble(this[size-4 ..< size]);
            }
        }

    @Override
    Int toInt(Boolean truncate = False, Rounding direction = TowardZero)
        {
        if (size > 128)
            {
            return new Int(this);
            }

        // verify that the bit array is sign-extended
        assert:bounds truncate || !this[0 ..< size-128].contains(~this[size-128]);
        return new Int(this[size-128 ..< size]);
        }

    @Override
    Int8 toInt8(Boolean truncate = False, Rounding direction = TowardZero)
        {
        switch (size <=> 8)
            {
            case Lesser:
                // sign-extend the bit array
                val cutoff = 8 - size;
                return empty ? 0 : new Int8(new Element[8](i -> this[i < cutoff ? 0 : i - cutoff]));

            case Equal:
                return new Int8(this);

            case Greater:
                // verify that the bit array is sign-extended
                assert:bounds truncate || !this[0 ..< size-8].contains(~this[size-8]);
                return new Int8(this[size-8 ..< size]);
            }
        }

    @Override
    Int16 toInt16(Boolean truncate = False, Rounding direction = TowardZero)
        {
        switch (size <=> 16)
            {
            case Lesser:
                // sign-extend the bit array
                val cutoff = 16 - size;
                return empty ? 0 : new Int16(new Element[16](i -> this[i < cutoff ? 0 : i - cutoff]));

            case Equal:
                return new Int16(this);

            case Greater:
                // verify that the bit array is sign-extended
                assert:bounds truncate || !this[0 ..< size-16].contains(~this[size-16]);
                return new Int16(this[size-16 ..< size]);
            }
        }

    @Override
    Int32 toInt32(Boolean truncate = False, Rounding direction = TowardZero)
        {
        switch (size <=> 32)
            {
            case Lesser:
                // sign-extend the bit array
                val cutoff = 32 - size;
                return empty ? 0 : new Int32(new Element[32](i -> this[i < cutoff ? 0 : i - cutoff]));

            case Equal:
                return new Int32(this);

            case Greater:
                // verify that the bit array is sign-extended
                assert:bounds truncate || !this[0 ..< size-32].contains(~this[size-32]);
                return new Int32(this[size-32 ..< size]);
            }
        }

    @Override
    Int64 toInt64(Boolean truncate = False, Rounding direction = TowardZero)
        {
        switch (size <=> 64)
            {
            case Lesser:
                // sign-extend the bit array
                val cutoff = 64 - size;
                return empty ? 0 : new Int64(new Element[64](i -> this[i < cutoff ? 0 : i - cutoff]));

            case Equal:
                return new Int64(this);

            case Greater:
                // verify that the bit array is sign-extended
                assert:bounds truncate || !this[0 ..< size-64].contains(~this[size-64]);
                return new Int64(this[size-64 ..< size]);
            }
        }

    @Override
    Int128 toInt128(Boolean truncate = False, Rounding direction = TowardZero)
        {
        switch (size <=> 128)
            {
            case Lesser:
                // sign-extend the bit array
                val cutoff = 128 - size;
                return empty ? 0 : new Int128(new Element[128](i -> this[i < cutoff ? 0 : i - cutoff]));

            case Equal:
                return new Int128(this);

            case Greater:
                // verify that the bit array is sign-extended
                assert:bounds truncate || !this[0 ..< size-128].contains(~this[size-128]);
                return new Int128(this[size-128 ..< size]);
            }
        }

    @Override
    IntN toIntN(Rounding direction = TowardZero)
        {
        Bit[]  bits = this;
        UInt64 size = this.size.toUInt64();
        if (size & 0b111 != 0)
            {
            // sign-extend the value out to a byte (8-bit) boundary
            val fullSize = size + 7 & ~0b111;
            val diff     = fullSize - size;
            bits = new Element[fullSize](i -> this[i < diff ? 0 : i-diff]);
            }

        return new IntN(bits);
        }

    @Override
    UInt toUInt(Boolean truncate = False, Rounding direction = TowardZero)
        {
        if (size <= 128)
            {
            return new UInt(this);
            }

        // verify that the rest of the bit array is zeros
        assert:bounds truncate || !this[0 ..< size-128].contains(Bit:1.as(Element)); // TODO GG: .contains(1);
        return new UInt(this[size-128 ..< size]);
        }

    @Override
    UInt8 toUInt8(Boolean truncate = False, Rounding direction = TowardZero)
        {
        switch (size <=> 8)
            {
            case Lesser:
                val cutoff = 8 - size;
                return empty ? 0 : new UInt8(new Element[8](i -> i < cutoff ? Zero : this[i - cutoff]));

            case Equal:
                return new UInt8(this);

            case Greater:
                assert:bounds truncate || !this[0 ..< size-8].contains(One);
                return new UInt8(this[size-8 ..< size]);
            }
        }

    @Override
    UInt16 toUInt16(Boolean truncate = False, Rounding direction = TowardZero)
        {
        switch (size <=> 16)
            {
            case Lesser:
                val cutoff = 16 - size;
                return empty ? 0 : new UInt16(new Element[16](i -> i < cutoff ? Zero : this[i - cutoff]));

            case Equal:
                return new UInt16(this);

            case Greater:
                assert:bounds truncate || !this[0 ..< size-16].contains(One);
                return new UInt16(this[size-16 ..< size]);
            }
        }

    @Override
    UInt32 toUInt32(Boolean truncate = False, Rounding direction = TowardZero)
        {
        switch (size <=> 32)
            {
            case Lesser:
                val cutoff = 32 - size;
                return empty ? 0 : new UInt32(new Element[32](i -> i < cutoff ? Zero : this[i - cutoff]));

            case Equal:
                return new UInt32(this);

            case Greater:
                assert:bounds truncate || !this[0 ..< size-32].contains(One);
                return new UInt32(this[size-32 ..< size]);
            }
        }

    @Override
    UInt64 toUInt64(Boolean truncate = False, Rounding direction = TowardZero)
        {
        switch (size <=> 64)
            {
            case Lesser:
                val cutoff = 64 - size;
                return empty ? 0 : new UInt64(new Element[64](i -> i < cutoff ? Zero : this[i - cutoff]));

            case Equal:
                return new UInt64(this);

            case Greater:
                assert:bounds truncate || !this[0 ..< size-64].contains(One);
                return new UInt64(this[size-64 ..< size]);
            }
        }

    @Override
    UInt128 toUInt128(Boolean truncate = False, Rounding direction = TowardZero)
        {
        switch (size <=> 128)
            {
            case Lesser:
                val cutoff = 128 - size;
                return empty ? 0 : new UInt128(new Element[128](i -> i < cutoff ? Zero : this[i - cutoff]));

            case Equal:
                return new UInt128(this);

            case Greater:
                assert:bounds truncate || !this[0 ..< size-128].contains(One);
                return new UInt128(this[size-128 ..< size]);
            }
        }

    @Override
    UIntN toUIntN(Rounding direction = TowardZero)
        {
        Bit[]  bits = this;
        UInt64 size = this.size.toUInt64();
        if (size & 0b111 != 0)
            {
            // zero-extend the value out to a byte (8-bit) boundary
            val fullSize = size + 7 & ~0b111;
            val diff     = fullSize - size;
            bits = new Element[fullSize](i -> i < diff ? Zero : this[i-diff]);
            }

        return new UIntN(bits);
        }

    @Override
    Dec toDec()
        {
        assert:bounds size == 28 || size == 32 || size == 60 || size == 64 || size == 128;
        return new Dec(this);
        }

    @Override
    Dec32 toDec32()
        {
        assert:bounds size == 32;
        return new Dec32(this);
        }

    @Override
    Dec64 toDec64()
        {
        assert:bounds size == 64;
        return new Dec64(this);
        }

    @Override
    Dec128 toDec128()
        {
        assert:bounds size == 128;
        return new Dec128(this);
        }

    @Override
    DecN toDecN()
        {
        return new DecN(this);
        }

    @Override
    Float8e4 toFloat8e4()
        {
        assert:bounds size == 8;
        return new Float8e4(this);
        }

    @Override
    Float8e5 toFloat8e5()
        {
        assert:bounds size == 8;
        return new Float8e5(this);
        }

    @Override
    BFloat16 toBFloat16()
        {
        assert:bounds size == 16;
        return new BFloat16(this);
        }

    @Override
    Float16 toFloat16()
        {
        assert:bounds size == 16;
        return new Float16(this);
        }

    @Override
    Float32 toFloat32()
        {
        assert:bounds size == 32;
        return new Float32(this);
        }

    @Override
    Float64 toFloat64()
        {
        assert:bounds size == 64;
        return new Float64(this);
        }

    @Override
    Float128 toFloat128()
        {
        assert:bounds size == 128;
        return new Float128(this);
        }

    @Override
    FloatN toFloatN()
        {
        return new FloatN(this);
        }


    // ----- Stringable methods --------------------------------------------------------------------

    @Override
    Int estimateStringLength(
            String                    sep    = "",
            String?                   pre    = "0b",
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
            String?                   pre    = "0b",
            String?                   post   = Null,
            Int?                      limit  = Null,
            String                    trunc  = "...",
            function String(Element)? render = Null)
        {
        return super(buf, sep, pre, post, limit, trunc, render);
        }
    }