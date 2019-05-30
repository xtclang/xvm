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
        if (size == 0)
            {
            return [""];
            }

        String[] results = new String[];

        Int start = 0;
        while (Int next := indexOf(separator, start))
            {
            results += start == next ? "" : this[start..next-1];
            start    = next + 1;
            }

        // whatever remains after the last separator (or the entire String, if there were no
        // separators found)
        results += substring(start);

        return results;
        }

    /**
     * Look for the specified {@code that} starting at the specified index.
     *
     * @param that     the substring to search for
     * @param startAt  the first index to search from (optional)
     *
     * @return a conditional return of the location of the index of the specified substring, or
     *         false if the substring could not be found
     */
     conditional Int indexOf(String! that, Int startAt = 0)
         {
         // if we've already run out of space for that string to fit, then it can't be found
         Int thisLen = this.size;
         Int thatLen = that.size;
         if (startAt > thisLen - thatLen)
             {
             return false;
             }

         // can't start before the start of the string (at zero)
         startAt = startAt.maxOf(0);

         // break out the special conditions (for small search strings)
         if (thatLen <= 1)
             {
             // assume that we can find the empty string wherever we look
             if (thatLen == 0)
                {
                return true, startAt;
                }

             // for single-character strings, use the more efficient single-character search
             return indexOf(that[0], startAt);
             }

         // otherwise, brute force
         Char first = that[0];
         NextTry: while (Int next := indexOf(first, startAt))
             {
             for (Int of = 1; of < thatLen; ++of)
                 {
                 if (this[next+of] != that[of])
                     {
                     startAt = next + 1;
                     continue NextTry;
                     }
                 }
             return true, next;
             }

         return false;
         }

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
     * Format this String into a left-justified String of the specified length, with the remainder
     * of the new String filled with the specified character. If the specified length is shorter
     * than the size of this String, then the result will be a truncated copy of this String,
     * containing only the first _length_ characters of this String.
     *
     * @param length  the size of the resulting String
     * @param fill    an optional fill character to use
     *
     * @return this String formatted into a left-justified String filled with the specified
     *         character
     */
    String! leftJustify(Int length, Char fill = ' ')
        {
        switch (length.sign)
            {
            case Negative:
                assert;

            case Zero:
                return "";

            case Positive:
                Int append = length - size;
                switch (append.sign)
                    {
                    case Negative:
                        return this[0..length];

                    case Zero:
                        return this;

                    case Positive:
                        return new StringBuffer(length)
                            .add(this)
                            .add(fill * append)
                            .to<String>();
                    }
            }
        }

    /**
     * Format this String into a right-justified String of the specified length, with the remainder
     * of the new String filled with the specified character. If the specified length is shorter
     * than the size of this String, then the result will be a truncated copy of this String,
     * containing only the last _length_ characters of this String.
     *
     * @param length  the size of the resulting String
     * @param fill    an optional fill character to use
     *
     * @return this String formatted into a left-justified String filled with the specified
     *         character
     */
    String! rightJustify(Int length, Char fill = ' ')
        {
        switch (length.sign)
            {
            case Negative:
                assert;

            case Zero:
                return "";

            case Positive:
                Int append = length - size;
                switch (append.sign)
                    {
                    case Negative:
                        return this.substring(-append);

                    case Zero:
                        return this;

                    case Positive:
                        return new StringBuffer(length)
                            .add(fill * append)
                            .add(this)
                            .to<String>();
                    }
            }
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
