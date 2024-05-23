/**
 * A StringBuffer is used to efficiently create a resulting String from any number of contributions
 * of any size.
 *
 * TODO insert and delete (including ranges) functionality
 */
class StringBuffer
        implements Appender<Char>
        implements UniformIndexed<Int, Char>
        implements Iterable<Char>
        implements Sliceable<Int>
        implements Stringable {
    /**
     * Construct a StringBuffer.
     *
     * @param capacity  an optional value indicating the expected size of the resulting String
     */
    construct(Int capacity = 0) {
        chars = new Array<Char>(capacity);
    }

    /**
     * The underlying representation of a StringBuffer is a mutable array of characters.
     */
    private Array<Char> chars;


    // ----- StringBuffer API ----------------------------------------------------------------------

    /**
     * The current capacity of the StringBuffer, which can be modified (but not to less than the
     * current size).
     */
    Int capacity {
        @Override
        Int get() = chars.capacity;

        @Override
        void set(Int n) {
            chars.capacity = Int.maxOf(n, chars.size);
        }
    }

    /**
     * Append a value to the StringBuffer.
     *
     * @param o  the object to append
     *
     * @return this buffer
     */
    @Op("+")
    StringBuffer append(Object o) {
        if (o.is(Stringable)) {
            ensureCapacity(o.estimateStringLength());
            o.appendTo(this);
        } else {
            o.toString().appendTo(this);
        }

        return this;
    }

    /**
     * Append the specified character repeatedly to the StringBuffer.
     *
     * @param ch  the character to repeatedly append
     * @param n   the number of times
     *
     * @return this buffer
     */
    StringBuffer addDup(Char ch, Int n) {
        ensureCapacity(n);
        while (--n >= 0) {
            add(ch);
        }
        return this;
    }

    /**
     * Look for the specified character value, starting at the specified index.
     *
     * @param value    the character value to search for
     * @param startAt  (optional) the first index to search from
     *
     * @return True iff this StringBuffer contains the character, at or after the `startAt` index
     * @return (conditional) the index at which the specified character was found
     */
    conditional Int indexOf(Char value, Int startAt = 0) = chars.indexOf(value, startAt);

    /**
     * Look for the specified character value, starting at the specified index and searching
     * backwards.
     *
     * @param value    the character value to search for
     * @param startAt  (optional) the index to start searching backwards from
     *
     * @return True iff this StringBuffer contains the character, at or before the `startAt` index
     * @return (conditional) the index at which the specified value was found
     */
    conditional Int lastIndexOf(Char value, Int startAt = MaxValue) = chars.lastIndexOf(value, startAt);

    /**
     * Modify the contents of this StringBuffer so that it has the specified size.
     *
     * @param newSize  if non-negative, the size to truncate the buffer to; otherwise the number of
     *                 characters to truncate from the end
     *
     * @return this buffer
     */
    StringBuffer truncate(Int newSize) {
        Int size = this.size;
        if (newSize < 0) {
            newSize += size;
        }

        assert:bounds 0 <= newSize < size;
        chars.deleteAll(newSize ..< size);
        return this;
    }

    /**
     * Clear the contents of this StringBuffer.
     */
    StringBuffer clear() {
        chars.clear();
        return this;
    }


    // ----- Stringable methods --------------------------------------------------------------------

    @Override
    Int estimateStringLength() = size;

    @Override
    Appender<Char> appendTo(Appender<Char> buf) = buf.addAll(chars);


    // ----- Appender methods ----------------------------------------------------------------------

    @Override
    StringBuffer add(Char v) {
        ensureCapacity(1);
        chars[size] = v;
        return this;
    }

    @Override
    StringBuffer addAll(Iterable<Char> iterable) {
        ensureCapacity(iterable.size);
        chars.addAll(iterable);
        return this;
    }

    @Override
    StringBuffer ensureCapacity(Int count) {
        // "count" represents an expected *additional* amount of capacity
        Int curCap = chars.capacity;
        Int reqCap = chars.size + count;
        if (reqCap > curCap) {
            if (curCap == 0 && reqCap > 1) {
                // the first attempt to specify the capacity may be exact, so trust it
                chars.capacity = reqCap;
            } else {
                // power-of-two capacity (but at least 32)
                chars.capacity = (reqCap + reqCap - 1).leftmostBit.notLessThan(32);
            }
        }
        return this;
    }


    // ----- Iterable methods ----------------------------------------------------------------------

    @Override
    Int size.get() = chars.size;

    @Override
    Iterator<Char> iterator() = chars.iterator();


    // ----- UniformIndexed methods ----------------------------------------------------------------------

    @Override
    @Op("[]")
    Char getElement(Int index) = chars[index];

    @Override
    @Op("[]=")
    void setElement(Int index, Char value) {
        chars[index] = value;
    }


    // ----- Sliceable methods ---------------------------------------------------------------------

    @Override
    @Op("[..]") StringBuffer slice(Range<Int> indexes) {
        StringBuffer that = new StringBuffer(indexes.size);
        that.addAll(chars[indexes]);
        return that;
    }


    // ----- Object methods ------------------------------------------------------------------------

    @Auto
    @Override
    String toString() = new String(chars);
}