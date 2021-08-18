/**
 * A StringBuffer is used to efficiently create a resulting String from any number of contributions
 * of any size.
 */
class StringBuffer
        implements Appender<Char>
        implements UniformIndexed<Int, Char>
        implements Iterable<Char>
        implements Sliceable<Int>
        implements Stringable
    {
    /**
     * Construct a StringBuffer.
     *
     * @param capacity  an optional value indicating the expected size of the resulting String
     */
    construct(Int capacity = 0)
        {
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
     */
    @Op("+")
    StringBuffer append(Object o)
        {
        if (o.is(Stringable))
            {
            o.appendTo(this);
            }
        else
            {
            o.toString().appendTo(this);
            }

        return this;
        }

    @Override
    @Auto String toString()
        {
        return new String(chars);
        }

    /**
     * Clear the contents of this StringBuffer.
     */
    void clear()
        {
        chars.clear();
        }


    // ----- Stringable methods --------------------------------------------------------------------

    @Override
    Int estimateStringLength()
        {
        return size;
        }

    @Override
    Appender<Char> appendTo(Appender<Char> buf)
        {
        return buf.addAll(chars);
        }


    // ----- Appender methods ----------------------------------------------------------------------

    @Override
    StringBuffer add(Char v)
        {
        chars[size] = v;
        return this;
        }

    @Override
    StringBuffer addAll(Iterable<Char> array)
        {
        chars += array;
        return this;
        }


    // ----- Iterable methods ----------------------------------------------------------------------

    @Override
    Int size.get()
        {
        return chars.size;
        }

    @Override
    Iterator<Char> iterator()
        {
        return chars.iterator();
        }


    // ----- UniformIndexed methods ----------------------------------------------------------------------

    @Override
    @Op("[]")
    @Op Char getElement(Int index)
        {
        return chars[index];
        }

    @Override
    @Op("[]=")
    void setElement(Int index, Char value)
        {
        chars[index] = value;
        }


    // ----- Sliceable methods ----------------------------------------------------------------------

    @Override
    @Op("[..]") StringBuffer slice(Range<Int> indexes)
        {
        StringBuffer that = new StringBuffer(indexes.size);
        that.addAll(chars[indexes]);
        return that;
        }


    // -----  methods ----------------------------------------------------------------------

    Int capacity
        {
        @Override
        Int get()
            {
            return chars.capacity;
            }

        @Override
        void set(Int n)
            {
            chars.capacity = Int.maxOf(n, chars.size);
            }
        }

    conditional Int indexOf(Char value, Int startAt = 0)
        {
        return chars.indexOf(value, startAt);
        }

    conditional Int lastIndexOf(Char value, Int startAt = Int.maxvalue)
        {
        return chars.lastIndexOf(value, startAt);
        }
    }
