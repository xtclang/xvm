import ecstasy.Duplicable;


/**
 * A SparseIntSet is used to maintain a sparse set of Int values in order. It is implemented using
 * the SkiplistMap, within which each key is the "page" index `(n % 64 == 0)`, and each
 * corresponding value is a bit set of size 64.
 */
class SparseIntSet
        implements Set<Int>
        implements Duplicable
        implements Freezable
    {
    // ----- constructors --------------------------------------------------------------------------

    construct()
        {
        contents = new SkiplistMap<Int, Int>();
        }

    /**
     * Copy constructor.
     *
     * @param that  the SparseIntSet to copy
     */
    construct(SparseIntSet that)
        {
        this.contents = that.contents.duplicate();
        this.size     = that.size;
        }


    // ----- properties ----------------------------------------------------------------------------

    /**
     * The underlying SkiplistMap used to store the sparse bitmap.
     */
    protected SkiplistMap<Int, Int> contents;


    // ----- read operations -----------------------------------------------------------------------

    @Override
    public/protected Int size;

    @Override
    Iterator<Int> iterator() // TODO GG I have to say "Int" everywhere instead of "Element"
        {
        // for each key in the SkiplistMap, there is one or more value in the set
        val bitsets = contents.entries.iterator();

        return new Iterator<Int>()
            {
            Int index  = Int.maxvalue;
            Int bitset = 0;

            @Override conditional Int next()
                {
                if (bitset == 0)
                    {
                    if (val entry := bitsets.next())
                        {
                        index  = entry.key;
                        bitset = entry.value;
                        assert bitset != 0;
                        }
                    else
                        {
                        return False;
                        }
                    }

                Int mask = bitset.rightmostBit;
                bitset &= ~mask;
// TODO GG error msg?
// return index + mask.trailingZeroCount;
                return True, (index + mask.trailingZeroCount);
                }

            @Override conditional Int min(Iterator<Int>.Orderer? order = Null)
// TODO GG
// @Override conditional Int min(Orderer? order = Null)
                {
                if (order != Null)
                    {
// assert Orderer actualOrder := knownOrder();
                    assert Iterator<Int>.Orderer actualOrder := knownOrder();
                    if (order != actualOrder)
                        {
                        return super(order);
                        }
                    }

                return next();
                }

// TODO GG
// @Override conditional Int max(Orderer? order = Null)
            @Override conditional Int max(Iterator<Int>.Orderer? order = Null)
                {
                if (order != Null)
                    {
// assert Orderer actualOrder := knownOrder();
                    assert Iterator<Int>.Orderer actualOrder := knownOrder();
                    if (order != actualOrder)
                        {
                        return super(order);
                        }
                    }

                if (val maxEntry := bitsets.max())
                    {
// TODO GG error msg
// return maxEntry.key + maxEntry.value.leftmostBit.trailingZeroCount;
                    return True, maxEntry.key + maxEntry.value.leftmostBit.trailingZeroCount;
                    }

                return False;
                }

//@Override conditional Range<Int> range(Orderer? order = Null)
            @Override conditional Range<Int> range(Iterator<Int>.Orderer? order = Null)
                {
                if (order != Null)
                    {
// assert Orderer actualOrder := knownOrder();
                    assert Iterator<Int>.Orderer actualOrder := knownOrder();
                    if (order != actualOrder)
                        {
                        return super(order);
                        }
                    }

                if (Int minVal := min())
                    {
                    if (Int maxVal := max())
                        {
// TODO GG error msg
//return minVal..maxVal;
                        return True, minVal..maxVal;
                        }
                    else
                        {
// TODO GG error msg
//return minVal..minVal;
                        return True, minVal..minVal;
                        }
                    }

                return False;
                }

            @Override Boolean knownDistinct()
                {
                return True;
                }

            @Override conditional Iterator<Int>.Orderer knownOrder()
                {
                assert val orderer := this.SparseIntSet.ordered();
                return True, orderer? : assert;
                }

            @Override Boolean knownEmpty()
                {
                return this.SparseIntSet.empty || bitsets.knownEmpty();
                }

            @Override conditional Int knownSize()
                {
                // if the iterator has not yet started, then we know the remaining size
                if (index == Int.maxvalue)
                    {
                    return this.SparseIntSet.knownSize();
                    }

                return super();
                }
            };
        }

    @Override
    conditional Orderer? ordered()
        {
        return contents.keys.ordered();
        }

    @Override
    Boolean contains(Int value)
        {
        return testBit(value, loadBitset(value));
        }


    // ----- write operations ----------------------------------------------------------------------

    @Override
    @Op("+") SparseIntSet add(Int value)
        {
        if (Int bitset := setBit(value, loadBitset(value)))
            {
            storeBitset(value, bitset);
            ++size;
            }
        return this;
        }

    @Override
    @Op("-") SparseIntSet remove(Int value)
        {
        if (Int bitset := clearBit(value, loadBitset(value)))
            {
            storeBitset(value, bitset);
            --size;
            }
        return this;
        }

    @Override
    @Op("+") SparseIntSet addAll(Iterable<Element> values)
        {
        if (values.is(SparseIntSet))
            {
            SkiplistMap<Int, Int> these = this.contents;
            SkiplistMap<Int, Int> those = values.contents;
            Int overlap = 0;
            for ((Int index, Int addBits) : those)
                {
                if (Int before := these.get(index))
                    {
                    Int after = before | addBits;
                    these.put(index, after);
                    overlap += before.bitCount + addBits.bitCount - after.bitCount;
                    }
                else
                    {
                    these.put(index, addBits);
                    }
                }
            size = this.size + values.size - overlap;
            }
        else
            {
            super(values);
            }

        return this;
        }

    @Override
    @Op("-") SparseIntSet removeAll(Iterable<Element> values)
        {
        if (values.is(SparseIntSet))
            {
            SkiplistMap<Int, Int> these = this.contents;
            SkiplistMap<Int, Int> those = values.contents;
            Int removed = 0;
            for ((Int index, Int remove) : those)
                {
                if (Int before := these.get(index), before & remove != 0)
                    {
                    Int after = before & ~remove;
                    if (after == 0)
                        {
                        these.remove(index);
                        removed += before.bitCount;
                        }
                    else
                        {
                        these.put(index, after);
                        removed += before.bitCount - after.bitCount;
                        }
                    }
                }
            size -= removed;
            }
        else
            {
            super(values);
            }

        return this;
        }

    @Override
    @Op("&") SparseIntSet retainAll(Iterable<Element> values)
        {
        if (values.is(SparseIntSet))
            {
            SkiplistMap<Int, Int> these = this.contents;
            SkiplistMap<Int, Int> those = values.contents;
            if (those.empty)
                {
                return clear();
                }

            Int removed = 0;
            for ((Int index, Int before) : these)
                {
                if (Int retain := those.get(index), before & retain != 0)
                    {
                    Int after = before & retain;
                    if (before != after)
                        {
                        these.put(index, after);
                        removed += before.bitCount - after.bitCount;
                        }
                    }
                else
                    {
                    these.remove(index);
                    removed += before.bitCount;
                    }
                }
            size -= removed;
            }
        else
            {
            super(values);
            }

        return this;
        }

    @Override
    SparseIntSet clear()
        {
        contents.clear();
        size = 0;
        return this;
        }


    // ----- Freezable interface ---------------------------------------------------------------

    @Override
    immutable SparseIntSet freeze(Boolean inPlace = False)
        {
        if (this.is(immutable SparseIntSet))
            {
            return this;
            }

        if (inPlace)
            {
            contents = contents.freeze(True);
            return makeImmutable();
            }

        return new SparseIntSet(this).freeze(True);
        }


    // ----- helpers -------------------------------------------------------------------------------

    /**
     * Obtain the bitset that corresponds to (holds a bit for) the passed value.
     *
     * @param  an integer value that might be stored in the SparseIntSet
     *
     * @param  bitset  an integer value containing some number of set (1) and clear (0) bits
     */
    protected Int loadBitset(Int n)
        {
        return contents.getOrDefault(indexFor(n), 0);
        }

    /**
     * Update the bitset that corresponds to (holds a bit for) the passed value.
     *
     * @param  n       an integer value that might be stored in the SparseIntSet
     * @param  bitset  an integer value containing some number of set (1) and clear (0) bits
     */
    protected void storeBitset(Int n, Int bitset)
        {
        Int key = indexFor(n);
        if (bitset == 0)
            {
            contents.remove(key);
            }
        else
            {
            contents.put(key, bitset);
            }
        }

    /**
     * Test to see if the passed value has a set (1) or clear (0) bit for the specified value.
     *
     * @param  n       an integer value that might be stored in the SparseIntSet
     * @param  bitset  an integer value containing some number of set (1) and clear (0) bits
     *
     * @return True iff the specified value is represented as a set bit (1) in the passed bitset
     */
    protected static Boolean testBit(Int n, Int bitset)
        {
        return bitset & maskFor(n) != 0;
        }

    /**
     * Test to see if the passed value has a set (1) or clear (0) bit for the specified value,
     * and if it is a clear (0) bit, then change it to a set (1) bit.
     *
     * @param  n       an integer value that might be stored in the SparseIntSet
     * @param  bitset  an integer value containing some number of set (1) and clear (0) bits
     *
     * @return True iff the bitset was modified
     * @return (optional) an integer value containing the modified bitset
     */
    protected static conditional Int setBit(Int n, Int bitset)
        {
        Int mask = maskFor(n);
        if ((bitset & mask) == 0)
            {
            return True, bitset | mask;
            }

        return False;
        }

    /**
     * Test to see if the passed value has a set (1) or clear (0) bit for the specified value,
     * and if it is a set (1) bit, then change it to a clear (0) bit.
     *
     * @param  n       an integer value that might be stored in the SparseIntSet
     * @param  bitset  an integer value containing some number of set (1) and clear (0) bits
     *
     * @return True iff the bitset was modified
     * @return (optional) an integer value containing the modified bitset
     */
    protected static conditional Int clearBit(Int n, Int bitset)
        {
        Int mask = maskFor(n);
        if ((bitset & mask) != 0)
            {
            return True, bitset & ~mask;
            }

        return False;
        }

    /**
     * Calculate a bitset index for the specified value.
     *
     * @param  an integer value that might be stored in the SparseIntSet
     *
     * @return an integer value that acts as a key to the SkiplistMap
     */
    protected static Int indexFor(Int n)
        {
        return n & ~0x3F;
        }

    /**
     * Calculate a bitset mask for the specified value.
     *
     * @param  an integer value that might be stored in the SparseIntSet
     *
     * @return an integer value to use as a mask, corresponding to the passed value
     */
    protected static Int maskFor(Int n)
        {
        return 1 << (n & 0x3F);
        }
    }
