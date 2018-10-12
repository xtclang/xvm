/**
 * String is a well-known, immutable data type for holding textual information.
 */
const String
        implements Sequence<Char>
        implements Stringable
    {
    /**
     * Construct a String from an array of characters.
     *
     * @param chars  an array of characters to include in the String
     */
    construct(Char[] chars)
        {
        this.chars = chars;
        }

    /**
     * The array of characters that form the content of the String.
     */
    private Char[] chars;


    // ----- String API ----------------------------------------------------------------------------

    /**
     * Obtain a portion of this String, beginning with at specified character index.
     *
     * @param startAt  the index of the first character of the new string
     *
     * @return the specified sub-string
     */
    String! substring(Int startAt)
        {
        return switch()
            {
            case startAt <= 0:   this;
            case startAt < size: this[startAt..size-1];
            default: "";
            };
        }

    /**
     * Obtain this String, but with its contents reversed.
     *
     * @return the reversed form of this String
     */
    String! reverse()
        {
        return size <= 1
                ? this
                : this[size-1..0];
        }

    /**
     * Count the number of occurrences of the specified character in this String.
     *
     * @param value  the character to search for
     *
     * @return the number of times that the specified character occurs in this String
     */
    Int count(Char value)
        {
        Int count = 0;
        for (Char ch : chars)
            {
            if (ch == value)
                {
                ++count;
                }
            }
        return count;
        }

    /**
     * Count the number of occurrences of the specified character in this String.
     *
     * @param value  the character to search for
     *
     * @return the number of times that the specified character occurs in this String
     */
    String![] split(Char separator)
        {
        String[] results = new String[];

        // TODO

        return results;
        }

    // TODO String versions of the various Char methods, including:
    // indexOf(String!)
    // lastIndexOf(String!)
    // count(String!)
    // String![] split(String! separator)

    @Override
    String! to<String!>()
        {
        return this;
        }

    /**
     * @return the characters of this String as an array
     */
    Char[] to<Char[]>()
        {
        return chars;
        }

    /**
     * Duplicate this String the specified number of times.
     *
     * @param n  the number of times to duplicate this String
     *
     * @return a String that is the result of duplicating this String the specified number of times
     */
    @Op("*")
    String! dup(Int n)
        {
        if (n <= 1)
            {
            return n == 1
                    ? this
                    : "";
            }

        StringBuffer buf = new StringBuffer(size*n);
        for (Int i = 0; i < n; ++i)
            {
            appendTo(buf);
            }
        return buf.to<String>();
        }

    /**
     * Add the String form of the passed object to this String, returning the result.
     *
     * @param o  the object to render as a String and append to this String
     *
     * @return the concatenation of the String form of the passed object onto this String
     */
    @Op("+")
    String! append(Object o)
        {
        Int          add = o.is(Stringable) ? o.estimateStringLength() : 0x0F;
        StringBuffer buf = new StringBuffer(size + add);
        return (buf + this + o).to<String>();
        }


    // ----- Sequence methods ----------------------------------------------------------------------

    @Override
    Int size.get()
        {
        return chars.size;
        }

    @Override
    @Op("[]")
    @Op Char getElement(Int index)
        {
        return chars[index];
        }

    @Override
    @Op("[..]")
    String slice(Range<Int> range)
        {
        return new String(chars[range]);
        }

    @Override
    Iterator<Char> iterator()
        {
        return chars.iterator();
        }

    @Override
    conditional Int indexOf(Char value, Int startAt = 0)
        {
        return chars.indexOf(value, startAt);
        }

    @Override
    conditional Int lastIndexOf(Char value, Int startAt = Int.maxvalue)
        {
        return chars.lastIndexOf(value, startAt);
        }


    // ----- Stringable methods --------------------------------------------------------------------

    @Override
    Int estimateStringLength()
        {
        return size;
        }

    @Override
    void appendTo(Appender<Char> appender)
        {
        appender.add(chars);
        }
    }
