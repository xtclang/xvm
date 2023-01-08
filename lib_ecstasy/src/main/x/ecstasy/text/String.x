/**
 * String is a well-known, immutable data type for holding textual information.
 */
const String
        implements UniformIndexed<Int, Char>
        implements Iterable<Char>
        implements Sliceable<Int>
        implements Stringable
        implements Destringable
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a String from an array of characters.
     *
     * @param chars  an array of characters to include in the String
     */
    construct(Char[] chars)
        {
        this.chars = chars.is(immutable Char[]) ? chars : chars.freeze();
        }

    @Override
    construct(String! text)
        {
        construct String(text.chars.reify(Constant));
        }


    // ----- properties ----------------------------------------------------------------------------

    /**
     * The array of characters that form the content of the String.
     */
    Char[] chars;

    /**
     * A lazily calculated, cached hash code.
     */
    private @Lazy Int64 hash.calc()
        {
        @Unchecked Int64 hash = Int64:982_451_653.toUnchecked(); // start with a prime number
        for (Char char : chars)
            {
            hash = hash * 31 + char.toInt64();
            }
        return hash;
        }


    // ----- operators -----------------------------------------------------------------------------

    /**
     * Duplicate this String the specified number of times.
     *
     * @param n  the number of times to duplicate this String
     *
     * @return a String that is the result of duplicating this String the specified number of times
     */
    @Op("*") String! dup(Int n)
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
        return buf.toString();
        }

    /**
     * Add the String form of the passed object to this String, returning the result.
     *
     * @param o  the object to render as a String and append to this String
     *
     * @return the concatenation of the String form of the passed object onto this String
     */
    @Op("+") String! append(Object o)
        {
        Int          add = o.is(Stringable) ? o.estimateStringLength() : 0x0F;
        StringBuffer buf = new StringBuffer(size + add);
        return (buf + this + o).toString();
        }


    // ----- operations ----------------------------------------------------------------------------

    /**
     * Obtain a portion of this String, beginning with at specified character index.
     *
     * @param startAt  the index of the first character of the new string
     *
     * @return the specified sub-string
     */
    String! substring(Int startAt)
        {
        return switch ()
            {
            case startAt <= 0:   this;
            case startAt < size: this[startAt ..< size];
            default: "";
            };
        }

    /**
     * Obtain this String, but with its contents reversed.
     *
     * @return the reversed form of this String
     */
    String! reversed()
        {
        return size <= 1
                ? this
                : this[size >.. 0];
        }

    /**
     * Strip the whitespace off of the front and back of the string.
     *
     * @return the contents of this String, but without any leading or trailing whitespace
     */
    String trim()
        {
        Int leading = 0;
        val length  = size;
        while (leading < length && this[leading].isWhitespace())
            {
            ++leading;
            }

        if (leading == length)
            {
            return "";
            }

        Int trailing = 0;
        while (this[length-trailing-1].isWhitespace())
            {
            ++trailing;
            }

        return leading == 0 && trailing == 0
                ? this
                : this[leading ..< size-trailing];
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
     * Split the String into an array of Strings, by finding each occurrence of the specified
     * separator character within the String, and collecting the array of Strings demarcated by that
     * character.
     *
     * @param separator  the character that separates the items in the String
     *
     * @return an array of Strings
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
            results += start == next ? "" : this[start ..< next];
            start    = next + 1;
            }

        // whatever remains after the last separator (or the entire String, if there were no
        // separators found)
        results += substring(start);

        return results;
        }

    /**
     * Extract the value at the specified index of a delimited String, and returning an empty string
     * for an index beyond the range of indexes in the delimited String. The behavior is the same as
     * if the following code were executed:
     *
     *     try
     *         {
     *         return split(separator)[index];
     *         }
     *     catch (OutOfBounds exception)
     *         {
     *         return "";
     *         }
     *
     * @param separator     the character that separates the items in the String
     * @param index         specifies the _n_-th item in the delimited String
     * @param defaultValue  (optional) the value to return if the index is out of bounds
     *
     * @return the specified item from the delimited String, or the `defaultValue` if the index
     *         is out of bounds
     */
    String extract(Char separator, Int index, String defaultValue="")
        {
        if (size == 0 || index < 0)
            {
            return size == index ? "" : defaultValue;
            }

        Int start = 0;
        Int count = 0;
        while (Int next := indexOf(separator, start))
            {
            if (count == index)
                {
                return start == next ? "" : this[start ..< next];
                }

            start = next + 1;
            ++count;
            }

        return count == index
                ? substring(start)
                : defaultValue;
        }

    /**
     * Split the String into an map of String keys and String values, by finding each occurrence of
     * the specified entry separator character within the String, and collecting the array of
     * Strings demarcated by that character.
     *
     * @param separator  the character that separates the items in the String
     *
     * @return an array of Strings
     */
    Map<String!, String!> splitMap(Char kvSeparator='=', Char entrySeparator=',')
        {
        if (size == 0)
            {
            return [];
            }

        return new StringMap(this, kvSeparator, entrySeparator);
        }

    /**
     * A lightweight, immutable Map implementation over a delimited String. Note that the
     * implementation does not attempt to de-duplicate keys; the search for a specified key is
     * sequential, e.g. a call to `get(k)` in the Map will return the value from  the first entry
     * with that key.
     */
    protected static const StringMap(String data, Char kvSep, Char entrySep)
            implements Map<String, String>
            incorporates collections.maps.KeySetBasedMap<String, String>
        {
        @Override
        conditional Orderer? ordered()
            {
            return True, Null;
            }

        @Override
        @Lazy Int size.calc()
            {
            return data.count(entrySep) + 1;
            }

        @Override
        Boolean empty.get()
            {
            return False;
            }

        @Override
        Boolean contains(String key)
            {
            return find(key);
            }

        @Override
        conditional String get(String key)
            {
            if ((Int keyStart, Int sepOffset, Int valueEnd) := find(key))
                {
                return True, valueEnd > sepOffset+1 ? data[sepOffset >..< valueEnd] : "";
                }
            return False;
            }

        conditional (Int keyStart, Int sepOffset, Int valueEnd) find(String key)
            {
            String data      = data;
            Int    length    = data.size;
            Int    offset    = 0;
            Int    keyLength = key.size;
            Int    keyOffset = 0;
            EachEntry: while (offset + keyLength <= length)
                {
                keyOffset = offset;
                Boolean match = True;
                for (Char keyChar : key)
                    {
                    Char mapChar = data[offset++];
                    if (mapChar != keyChar)
                        {
                        if (mapChar == entrySep)
                            {
                            continue EachEntry;
                            }
                        else
                            {
                            match = False;
                            break;
                            }
                        }
                    }

                if (offset >= length)
                    {
                    // key is at the very end, with no delimiter
                    return match, keyOffset, length, length;
                    }

                Int  sepOffset = offset;
                Char sepChar   = data[offset++];
                if (match && sepChar == entrySep)
                    {
                    // key is followed immediately by the entry separator, so value is blank
                    return True, keyOffset, sepOffset, sepOffset;
                    }

                // find the separator offset
                while (offset < length && data[offset] != entrySep)
                    {
                    ++offset;
                    }

                if (match && sepChar == kvSep)
                    {
                    // we did find the key, and now we have found the end of the value
                    return True, keyOffset, sepOffset, offset;
                    }

                ++offset;
                }

            return False;
            }

        @Override
        @Lazy Set<String> keys.calc()
            {
            return new KeySet();
            }

        const KeySet
                implements Set<String>
            {
            @Override
            conditional Orderer? ordered()
                {
                return True, Null;
                }

            @Override
            Int size.get()
                {
                return this.StringMap.size;
                }

            @Override
            Boolean empty.get()
                {
                return False;
                }

            @Override
            Boolean contains(String value)
                {
                return this.StringMap.contains(value);
                }

            @Override
            Iterator<String> iterator()
                {
                return new Iterator<String>()
                    {
                    String data   = this.StringMap.data;
                    Int    offset = 0;

                    @Override
                    conditional String next()
                        {
                        Int length = data.size;
                        if (offset >= length)
                            {
                            return False;
                            }

                        // find the end of the entry
                        Int endEntry = length;
                        endEntry := data.indexOf(entrySep, offset);

                        // the delimiter between key and value is optional (i.e. value assumed
                        // to be "")
                        Int endKey = endEntry;
                        if (endKey := data.indexOf(kvSep, offset), endKey > endEntry)
                            {
                            endKey = endEntry;
                            }

                        String key = data[offset ..< endKey];
                        offset = endEntry + 1;

                        return True, key;
                        }
                    };
                }
            }
        }

    /**
     * Determine if this String _starts-with_ the specified character.
     *
     * @param ch  a character to look for at the beginning of this String
     *
     * @return True iff this String starts-with the specified character
     */
    Boolean startsWith(Char ch)
        {
        return size > 0 && chars[0] == ch;
        }

    /**
     * Determine if `this` String _starts-with_ `that` String. A String `this` of at least `n`
     * characters "starts-with" another String `that` of exactly `n` elements iff, for each index
     * `0..<n`, the character at the index in `this` String is equal to the character at the same
     * index in `that` String.
     *
     * @param that  a String to look for at the beginning of this String
     *
     * @return True iff this String starts-with that String
     */
    Boolean startsWith(String that)
        {
        return this.chars.startsWith(that.chars);
        }

    /**
     * Determine if this String _ends-with_ the specified character.
     *
     * @param ch  a character to look for at the end of this String
     *
     * @return True iff this String ends-with the specified character
     */
    Boolean endsWith(Char ch)
        {
        Int size = this.size;
        return size > 0 && chars[size-1] == ch;
        }

    /**
     * Determine if `this` String _ends-with_ `that` String. A String `this` of `m` characters
     * "ends-with" another String `that` of `n` characters iff `n <= m` and, for each index `i`
     * in the range `0..<n`, the character at the index `m-n+i` in `this` String is equal to the
     * character at index `i` in `that` String.
     *
     * @param that  a String to look for at the end of this String
     *
     * @return True iff this String ends-with that String
     */
    Boolean endsWith(String that)
        {
        return this.chars.endsWith(that.chars);
        }

    /**
     * Look for the specified character starting at the specified index.
     *
     * @param value    the character to search for
     * @param startAt  the first index to search from (optional)
     *
     * @return True iff this string contains the character, at or after the `startAt` index
     * @return (conditional) the index at which the specified character was found
     */
    conditional Int indexOf(Char value, Int startAt = 0)
        {
        return chars.indexOf(value, startAt);
        }

    /**
     * Look for the specified character starting at the specified index and searching backwards.
     *
     * @param value    the character to search for
     * @param startAt  the index to start searching backwards from (optional)
     *
     * @return True iff this string contains the character, at or before the `startAt` index
     * @return (conditional) the index at which the specified character was found
     */
    conditional Int lastIndexOf(Char value, Int startAt = MaxValue)
        {
        return chars.lastIndexOf(value, startAt);
        }

    /**
     * Look for the specified `that` starting at the specified index.
     *
     * @param that     the substring to search for
     * @param startAt  the first index to search from (optional)
     *
     * @return True iff this string contains the specified string, at or after the `startAt` index
     * @return (conditional) the index at which the specified string was found
     */
     conditional Int indexOf(String that, Int startAt = 0)
         {
         Int thisLen = this.size;
         Int thatLen = that.size;

         // there has to be enough room to fit "that"
         if (startAt > thisLen - thatLen)
             {
             return False;
             }

         // can't start before the start of the string (at zero)
         startAt = startAt.notLessThan(0);

         // break out the special conditions (for small search strings)
         if (thatLen <= 1)
             {
             // assume that we can find the empty string wherever we look
             if (thatLen == 0)
                {
                return True, startAt;
                }

             // for single-character strings, use the more efficient single-character search
             return indexOf(that[0], startAt);
             }

         // otherwise, brute force
         Char first = that[0];
         NextTry: while (Int index := indexOf(first, startAt))
             {
             if (index > thisLen - thatLen)
                 {
                 return False;
                 }
             for (Int of = 1; of < thatLen; ++of)
                 {
                 if (this[index+of] != that[of])
                     {
                     startAt = index + 1;
                     continue NextTry;
                     }
                 }
             return True, index;
             }

         return False;
         }

    /**
     * Look for the specified `that` starting at the specified index and searching backwards.
     *
     * @param that     the substring to search for
     * @param startAt  the first index to search backwards from (optional)
     *
     * @return True iff this string contains the specified string, at or before the `startAt` index
     * @return (conditional) the index at which the specified string was found
     */
     conditional Int lastIndexOf(String that, Int startAt = MaxValue)
         {
         Int thisLen = this.size;
         Int thatLen = that.size;

         // there has to be enough room to fit "that"
         if (startAt < thatLen)
             {
             return False;
             }

         // can't start beyond the end of the string
         startAt = startAt.notGreaterThan(thisLen);

         // break out the special conditions (for small search strings)
         if (thatLen <= 1)
             {
             // assume that we can find the empty string wherever we look
             if (thatLen == 0)
                {
                return True, startAt;
                }

             // for single-character strings, use the more efficient single-character search
             return lastIndexOf(that[0], startAt);
             }

         // otherwise, brute force
         Char first = that[0];
         NextTry: while (Int index := lastIndexOf(first, startAt))
             {
             if (index > thisLen - thatLen)
                 {
                 startAt = index - 1;
                 continue NextTry;
                 }

             for (Int of = 1; of < thatLen; ++of)
                 {
                 if (this[index+of] != that[of])
                     {
                     startAt = index - 1;
                     continue NextTry;
                     }
                 }
             return True, index;
             }

         return False;
         }

    /**
     * Match all characters in this String to a regular expression pattern.
     *
     * @param pattern  the regular expression to match
     *
     * @return True iff this entire String matches the pattern
     * @return (optional) a Matcher resulting from matching the regular expression with this String
     */
    conditional Match matches(RegEx pattern)
        {
        return pattern.match(this);
        }

    /**
     * Match the start of this String to a regular expression pattern.
     *
     * Unlike the `match` method that matches the whole String value this method only matches the
     * beginning, subsequent characters remaining after the pattern was matched are ignored.
     *
     * @param pattern  the regular expression to match
     *
     * @return True iff this String starts with a sub-sequence that matches the regular expression
     * @return (optional) a Matcher resulting from matching the start of this String
     */
    conditional Match prefixMatches(RegEx pattern)
        {
        return pattern.matchPrefix(this);
        }

    /**
     * Find the first sub-sequence of characters in this String that match a regular expression
     * pattern.
     *
     * This method will start at the specified `offset` in this String and search for the first
     * sub-sequence that matches the expression. Subsequent matches may be found by calling the
     *  `matchAny()` method with an offset equal to the `end` property of the returned `Match`.
     *
     * When searching for matches any non-matching sub-sequences of characters will be skipped.
     *
     * @param pattern  the regular expression to match
     * @param offset   the position in the String to start searching from
     *
     * @return True iff the input contains a sub-sequence that matches the pattern
     * @return (optional) a Match resulting from matching the pattern
     */
    conditional Match containsMatch(RegEx pattern, Int offset = 0)
        {
        return pattern.find(this, offset);
        }

    /**
     * Replaces every subsequence of this String that matches the pattern with the given
     * replacement string; optionally starting at a given offset in this String.
     *
     * This method first resets this matcher.  It then scans the input sequence looking for matches
     * of the pattern.  Characters that are not part of any match are appended directly to the
     * result string; each match is replaced in the result by the replacement string.
     *
     * Note that backslashes `\` and dollar signs `$` in the replacement string may cause the
     * results to be different than if it were being treated as a literal replacement string.
     * Dollar signs may be treated as references to captured subsequences as described above, and
     * backslashes are used to escape literal characters in the replacement string.
     *
     * Invoking this method changes this matcher's state.  If the matcher is to be used in further
     * matching operations then it should first be reset.
     *
     * @param pattern      the regular expression to match
     * @param replacement  the replacement string
     * @param offset       the position in the String to start searching from
     *
     * @return  A String constructed by replacing each matching subsequence by the replacement
     *          string
     */
    String! replaceAll(RegEx pattern, String replacement, Int offset = 0)
        {
        return pattern.replaceAll(this[offset ..< this.size], replacement);
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
                        return this[0 ..< length];  // REVIEW GG

                    case Zero:
                        return this;

                    case Positive:
                        return new StringBuffer(length)
                                .addAll(this)
                                .addAll(fill * append)
                                .toString();
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
                                .addAll(fill * append)
                                .addAll(this)
                                .toString();
                    }
            }
        }

    /**
    * Replace every appearance of the `match` substring in this String with the `replace` String.
    *
    * @param match    the substring to be replaced
    * @param replace  the replacement String
    *
    * @return the resulting String
    */
    String! replace(String! match, String! replace)
        {
        Int replaceSize = replace.size;

        if (Int matchOffset := indexOf(match))
            {
            Int thisSize    = this.size;
            Int matchSize   = match.size;
            Int startOffset = 0;

            StringBuffer buffer = new StringBuffer(thisSize - matchSize + replaceSize);
            do
                {
                buffer.addAll(chars[startOffset ..< matchOffset]);
                if (replaceSize > 0)
                    {
                    buffer.addAll(replace.chars);
                    }
                startOffset = matchOffset + matchSize;
                }
            while (startOffset < thisSize, matchOffset := indexOf(match, startOffset));

            return buffer.addAll(chars[startOffset ..< thisSize]).toString();
            }
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


    // ----- UniformIndexed methods ----------------------------------------------------------------

    @Override
    @Op("[]") Char getElement(Int index)
        {
        return chars[index];
        }


    // ----- Sliceable methods ---------------------------------------------------------------------

    @Override
    @Op("[..]") String slice(Range<Int> indexes)
        {
        return new String(chars[indexes]);
        }


    // ----- conversions ---------------------------------------------------------------------------

    /**
     * @return the UTF-8 conversion of this String into a byte array
     */
    immutable Byte[] utf8()
        {
        Int    length = calcUtf8Length();
        Byte[] bytes  = new Byte[length];
        Int    actual = formatUtf8(bytes, 0);
        assert actual == length;
        return bytes.makeImmutable();
        }

    /**
     * @return the characters of this String as an array
     */
    immutable Char[] toCharArray()
        {
        return chars;
        }

    /**
     * @return a Reader over the characters of this String
     */
    Reader toReader()
        {
        return new io.CharArrayReader(chars);
        }

    @Override
    String! toString()
        {
        return this;
        }

    /**
     * Convert this `String` to its all-upper-case form.
     *
     * @return the upper-case form of this `String`
     */
    String! toUppercase()
        {
        Each: for (Char char : chars)
            {
            if (char != char.uppercase)
                {
                Int checked = Each.count;
                Char[] upperChars = new Char[size](offset ->
                        offset < checked ? chars[offset] : chars[offset].uppercase);
                return new String(upperChars.makeImmutable());
                }
            }

        return this;
        }

    /**
     * Convert this `String` to its all-lower-case form.
     *
     * @return the lower-case form of this `String`
     */
    String! toLowercase()
        {
        Each: for (Char char : chars)
            {
            if (char != char.lowercase)
                {
                Int checked = Each.count;
                Char[] lowerChars = new Char[size](offset ->
                        offset < checked ? chars[offset] : chars[offset].lowercase);
                return new String(lowerChars.makeImmutable());
                }
            }

        return this;
        }


    // ----- helper methods ------------------------------------------------------------------------

    /**
     * @return the minimum number of bytes necessary to encode the string in UTF8 format
     */
    Int calcUtf8Length()
        {
        Int len = 0;
        for (Char ch : chars)
            {
            len += ch.calcUtf8Length();
            }
        return len;
        }

    /**
     * Encode this string into the passed byte array using the UTF8 format.
     *
     * @param bytes  the byte array to write the UTF8 bytes into
     * @param of     the offset into the byte array to write the first byte
     *
     * @return the number of bytes used to encode the character in UTF8 format
     */
    Int formatUtf8(Byte[] bytes, Int of)
        {
        Int len = 0;
        for (Char ch : chars)
            {
            len += ch.formatUtf8(bytes, of + len);
            }
        return len;
        }

    /**
     * Determine if the string needs to be escaped in order to be displayed.
     *
     * @return True iff the string should be escaped in order to be displayed
     * @return (conditional) the number of characters in the escaped string
     */
    conditional Int isEscaped()
        {
        Int total = size;
        for (Char ch : chars)
            {
            if (Int n := ch.isEscaped())
                {
                total += n - 1;
                }
            }

        return total == size
                ? False
                : True, total;
        }

    /**
     * Append the string to the Appender, escaping characters as necessary.
     *
     * @param buf  the Appender to append to
     */
    Appender<Char> appendEscaped(Appender<Char> buf)
        {
        for (Char ch : chars)
            {
            ch.appendEscaped(buf);
            }
        return buf;
        }

    /**
     * @return the string as it would appear in source code, in double quotes and escaped as
     *         necessary
     */
    String quoted()
        {
        if (Int len := isEscaped())
            {
            return appendEscaped(new StringBuffer(len + 2).add('\"')).add('\"').toString();
            }
        else
            {
            return new StringBuffer(size+2).add('\"').addAll(this).add('\"').toString();
            }
        }


    // ----- Hashable functions --------------------------------------------------------------------

    @Override
    static <CompileType extends String> Int64 hashCode(CompileType value)
        {
        return value.hash;
        }

    @Override
    static <CompileType extends String> Boolean equals(CompileType value1, CompileType value2)
        {
        return value1.chars == value2.chars;
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
    }