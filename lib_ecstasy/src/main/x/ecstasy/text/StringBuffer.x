/**
 * A StringBuffer is used to efficiently create a resulting String from any number of contributions
 * of any size.
 *
 * TODO add deleteAll(offset..offset) method (or maybe just implement List<Char>)
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
     * Append a value to the StringBuffer.
     *
     * @param o  the object to append
     *
     * @return this buffer
     */
    @Op("+")
    StringBuffer append(Object o) {
        if (o.is(Stringable)) {
            o.appendTo(this);
        } else {
            o.toString().appendTo(this);
        }

        return this;
    }

    @Auto
    @Override
    String toString() {
        return new String(chars);
    }

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
            newSize = size + newSize;
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
    Int estimateStringLength() {
        return size;
    }

    @Override
    Appender<Char> appendTo(Appender<Char> buf) {
        return buf.addAll(chars);
    }


    // ----- Appender methods ----------------------------------------------------------------------

    @Override
    StringBuffer add(Char v) {
        chars[size] = v;
        return this;
    }

    @Override
    StringBuffer addAll(Iterable<Char> array) {
        chars += array;
        return this;
    }


    // ----- Iterable methods ----------------------------------------------------------------------

    @Override
    Int size.get() {
        return chars.size;
    }

    @Override
    Iterator<Char> iterator() {
        return chars.iterator();
    }


    // ----- UniformIndexed methods ----------------------------------------------------------------------

    @Override
    @Op("[]")
    Char getElement(Int index) {
        return chars[index];
    }

    @Override
    @Op("[]=")
    void setElement(Int index, Char value) {
        chars[index] = value;
    }


    // ----- Sliceable methods ----------------------------------------------------------------------

    @Override
    @Op("[..]") StringBuffer slice(Range<Int> indexes) {
        StringBuffer that = new StringBuffer(indexes.size);
        that.addAll(chars[indexes]);
        return that;
    }


    // -----  methods ----------------------------------------------------------------------

    Int capacity {
        @Override
        Int get() {
            return chars.capacity;
        }

        @Override
        void set(Int n) {
            chars.capacity = Int.maxOf(n, chars.size);
        }
    }

    conditional Int indexOf(Char value, Int startAt = 0) {
        return chars.indexOf(value, startAt);
    }

    conditional Int lastIndexOf(Char value, Int startAt = MaxValue) {
        return chars.lastIndexOf(value, startAt);
    }
}