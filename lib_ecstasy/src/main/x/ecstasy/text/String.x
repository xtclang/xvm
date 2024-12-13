/**
 * String is a well-known, immutable data type for holding textual information.
 */
const String
        implements UniformIndexed<Int, Char>
        implements Iterable<Char>
        implements Sliceable<Int>
        implements Stringable
        implements Destringable {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a String from an array of characters.
     *
     * @param chars  an array of characters to include in the String
     */
    construct(Char[] chars) {
        this.chars = chars.is(immutable Char[]) ? chars : chars.freeze();
    }

    @Override
    construct(String! text) {
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
    private @Lazy Int64 hash.calc() {
        Int64 hash = Int64:982_451_653;         // start with a prime number
        for (Char char : chars) {
            hash = hash * 31 + char.toInt64();
        }
        return hash;
    }

    /**
     * Support for link-time conditional evaluation: Determine if the name represented by this
     * String is defined (an enabled option), within the [TypeSystem] of the current service.
     *
     * For example, to determine if the `TypeSystem` is formed using the "test" mode:
     *
     *     if ("test".defined) {...}
     *
     * Or, to specify that a class is only present when in "test" mode:
     *
     *     @Iff("test".defined) class ...
     */
    Boolean defined.get() {
        return this:service.typeSystem.definedNames.contains(this);
    }


    // ----- operators -----------------------------------------------------------------------------

    /**
     * Duplicate this String the specified number of times.
     *
     * @param n  the number of times to duplicate this String
     *
     * @return a String that is the result of duplicating this String the specified number of times
     */
    @Op("*") String! dup(Int n) {
        if (n <= 1) {
            return n == 1
                    ? this
                    : "";
        }

        StringBuffer buf = new StringBuffer(size*n);
        for (Int i = 0; i < n; ++i) {
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
    @Op("+") String! add(Object o) {
        Int          add = o.is(Stringable) ? o.estimateStringLength() : 0x0F;
        StringBuffer buf = new StringBuffer(size + add);
        return (buf + this + o).toString();
    }


    // ----- operations ----------------------------------------------------------------------------

    /**
     * Obtain a portion of this String, beginning at the specified character index, and continuing
     * to the end of the String.
     *
     * To obtain a starting or middle portion of this string, use the [slice] method instead of this
     * method.
     *
     * This method is tolerant of a starting index that is not within the bounds of this string. An
     * index less than zero indicates the number of characters from the end of this string to take
     * as the substring. An index is greater than or equal to the size of this string indicates that
     * an empty string should be returned.
     *
     * @param startAt  the index into this string of the first character of the string to return; a
     *                 negative value indicates the number of characters from the end of this string
     *                 to take as the substring
     *
     * @return the specified sub-string
     */
    String! substring(Int startAt) {
        if (startAt < 0) {
            startAt += size;
        }

        return startAt <= 0   ? this                   :
               startAt < size ? this[startAt ..< size] : "";
    }

    /**
     * Obtain this String, but with its contents reversed.
     *
     * @return the reversed form of this String
     */
    String! reversed() {
        return size <= 1
                ? this
                : this[size >.. 0];
    }

    /**
     * Strip the whitespace off of the front and back of the string.
     *
     * @param whitespace  a function that determines whether a character should be trimmed
     *
     * @return the contents of this String, but without any leading or trailing whitespace
     */
    String trim(function Boolean(Char) whitespace = Char.isWhitespace) {
        Int leading = 0;
        val length  = size;
        while (leading < length && whitespace(this[leading])) {
            ++leading;
        }

        if (leading == length) {
            return "";
        }

        Int trailing = 0;
        while (whitespace(this[length-trailing-1])) {
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
    Int count(Char value) {
        Int count = 0;
        for (Char ch : chars) {
            if (ch == value) {
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
     * @param omitEmpty  (optional) indicates whether empty strings are to be omitted from the
     *                   resulting array
     * @param trim       (optional) pass `True` to trim whitespace from each string in the
     *                   resulting array, or pass a function that determines what whitespace
     *                   characters to trim
     *
     * @return an immutable array of Strings
     */
    String![] split(Char                             separator,
                    Boolean                          omitEmpty = False,
                    Boolean | function Boolean(Char) trim      = False,
                   ) {
        Int length = size;
        if (length == 0) {
            return omitEmpty ? [] : [""];
        }

        String[] results = new String[];
        if (trim == False) {
            Int start = 0;
            while (Int next := indexOf(separator, start)) {
                if (start == next) {
                    if (!omitEmpty) {
                        results += "";
                    }
                } else {
                    results += this[start ..< next];
                }
                start = next + 1;
            }
            // whatever remains after the last separator (or the entire String, if there were no
            // separators found)
            String last = substring(start);
            if (!omitEmpty || !last.empty) {
                results += last;
            }
        } else {
            Char[]                 chars      = this.chars;
            Int                    offset     = 0;
            Int                    start      = 0;
            Boolean                leading    = True;
            function Boolean(Char) whitespace = trim.is(function Boolean(Char)) ?: Char.isWhitespace;
            while (True) {
                if (offset >= length || chars[offset] == separator) {
                    Int end = offset-1;
                    while (end >= start && whitespace(chars[end])) {
                        --end;
                    }

                    if (end >= start) {
                        results += this[start..end];
                    } else if (!omitEmpty) {
                        results += "";
                    }

                    if (offset >= length) {
                        break;
                    }
                    start   = offset + 1;
                    leading = True;
                } else if (leading) {
                    if (whitespace(chars[offset])) {
                        start = offset + 1;
                    } else {
                        leading = False;
                    }
                }
                ++offset;
            }
        }

        return results.freeze(True);
    }

    /**
     * Extract the value at the specified index of a delimited String, and returning an empty string
     * for an index beyond the range of indexes in the delimited String. The behavior is the same as
     * if the following code were executed:
     *
     *     try {
     *         return split(separator)[index];
     *     } catch (OutOfBounds exception) {
     *         return "";
     *     }
     *
     * @param separator     the character that separates the items in the String
     * @param index         specifies the _n_-th item in the delimited String
     * @param defaultValue  (optional) the value to return if the index is out of bounds
     *
     * @return the specified item from the delimited String, or the `defaultValue` if the index
     *         is out of bounds
     */
    String extract(Char separator, Int index, String defaultValue="") {
        if (size == 0 || index < 0) {
            return size == index ? "" : defaultValue;
        }

        Int start = 0;
        Int count = 0;
        while (Int next := indexOf(separator, start)) {
            if (count == index) {
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
     * @param kvSeparator     the character that separates each key from its corresponding value in
     *                        each entry of the "map entries" represented by the String
     * @param entrySeparator  the character that separates each entry from the next entry in the
     *                        the sequence of "map entries" represented by the String
     * @param whitespace      a function that identifies white space to strip off of keys and values
     * @param valueQuote      a function that identifies an opening balanced quote of a quoted value
     *
     * @return a map from `String` keys to `String` values
     */
    Map<String!, String!> splitMap(
            Char                   kvSeparator    = '=',
            Char                   entrySeparator = ',',
            function Boolean(Char) whitespace     = ch -> ch.isWhitespace(),
            function Boolean(Char) valueQuote     = _ -> False,
            ) {
        if (size == 0) {
            return [];
        }

        return new StringMap(this, kvSeparator, entrySeparator, whitespace, valueQuote);
    }

    /**
     * A lightweight, immutable Map implementation over a delimited String. Note that the
     * implementation does not attempt to de-duplicate keys; the search for a specified key is
     * sequential, e.g. a call to `get(k)` in the Map will return the value from the first entry
     * with that key.
     */
    protected static const StringMap(
            String                 data,
            Char                   kvSep,
            Char                   entrySep,
            function Boolean(Char) whitespace,
            function Boolean(Char) valueQuote,
            )
            implements Map<String, String>
            extends maps.KeyBasedMap<String, String> {

        @Override
        conditional Orderer? ordered() = (True, Null);

        @Override
        @Lazy Int size.calc() = keyIterator().count();

        @Override
        @Lazy Boolean empty.get() = keyIterator().next();

        @Override
        Boolean contains(String key) = find(key);

        @Override
        conditional String get(String key) {
            if (Int delimOffset := find(key)) {
                Int length = data.size;
                if (delimOffset >= length || data[delimOffset] == entrySep) {
                    return True, "";
                }

                // skip leading white space in the value
                Int valStart = delimOffset + 1;
                while (valStart < length && whitespace(data[valStart])) {
                    ++valStart;
                }

                // it's possible that the entire value was whitespace
                if (valStart >= length || data[valStart] == entrySep) {
                    return True, "";
                }

                // check for a quoted value
                if (valueQuote(data[valStart])) {
                    Char quote   = data[valStart++];
                    Int  valStop = valStart;
                    while (valStop < length && data[valStop] != quote) {
                        ++valStop;
                    }
                    return True, data[valStart..<valStop];
                }

                // otherwise, just scan until the entry separator is encountered
                Int valStop = valStart;
                while (valStop < length && data[valStop] != entrySep) {
                    ++valStop;
                }

                // discard trailing whitespace
                --valStop;  // move backwards from the delim to the last character in the value
                while (whitespace(data[valStop])) {
                    --valStop;
                }
                return True, data[valStart..valStop];
            }
            return False;
        }

        @Override
        protected Iterator<String> keyIterator() = new Iterator<String>() {
            String data   = this.StringMap.data;
            Int    offset = 0;

            @Override
            conditional String next() {
                Int length = data.size;
                if (offset >= length) {
                    return False;
                }
                Int keyStart = offset;
                Int keyEnd   = offset;
                while (offset < length) {
                    Char ch = data[offset++];
                    if (ch == kvSep) {
                        offset = skipValue(offset);
                        break;
                    } else if (ch == entrySep) {
                        break;
                    } else {
                        ++keyEnd;
                    }
                }
                return True, data[keyStart..<keyEnd].trim(whitespace);
            }
        };

        /**
         * Internal helper to find a "key in a map" that is actually in the underlying String.
         *
         * @param key  the key to find
         *
         * @return True iff the key was found
         * @return (conditional) the offset of the delimiter following the key (which may be OOB)
         */
        protected conditional Int find(String key) {
            String data      = data;
            Int    length    = data.size;
            Int    offset    = 0;
            Int    keyLength = key.size;
            NextEntry: while (offset + keyLength <= length) {
                // skip leading whitespace
                while (whitespace(data[offset])) {
                    if (++offset >= length) {
                        return False;
                    }
                }

                // first, verify that the key would even fit
                if (offset + keyLength > length) {
                    return False;
                }

                // match the key, character by character
                Boolean match = True;
                for (Char keyChar : key) {
                    Char mapChar = data[offset++];
                    if (mapChar != keyChar) {
                        match = False;
                        --offset;
                        break;
                    }
                }

                // finish whatever remains of the key and the white space after it, up until the kv
                // separator or the entry separator is encountered (or there are no more chars)
                while (offset < length) {
                    Char ch = data[offset];
                    if (ch == entrySep) {
                        if (match) {
                            // key is followed immediately by the entry separator, so value is blank
                            return True, offset;
                        } else {
                            // wasn't a match: no value to skip, so try again to find the key
                            ++offset;
                            continue NextEntry;
                        }
                    }

                    if (ch == kvSep) {
                        if (match) {
                            // key is followed immediately by the key separator, so value is next
                            return True, offset;
                        } else {
                            // wasn't a match: skip the value, then try again to find the key
                            ++offset;
                            break;
                        }
                    }

                    if (match && !whitespace(ch)) {
                        match = False;
                    }
                    ++offset;
                }

                if (offset >= length) {
                    // we've passed the end of the data, so there is no following value, but we
                    // might have found the key; either way, the search is done
                    return match, offset;
                }

            offset = skipValue(offset);
            }

            return False;
        }

        /**
         * @param the offset of the first character of a value in the current k/v pair, i.e. one
         *        character past the `kvSep`
         *
         * @return the offset of the first character of a key in the next k/v pair (or the first
         *         index past the end of the string)
         */
        protected Int skipValue(Int offset) {
            String data      = data;
            Int    length    = data.size;
            while (offset < length && whitespace(data[offset])) {
                ++offset;
            }

            // skip past the quoted value (if the value is quoted)
            if (offset < length && valueQuote(data[offset])) {
                Char quote = data[offset++];
                while (offset < length && data[offset++] != quote) {}
            }

            // skip everything else until one character past the entry separator
            while (offset < length && data[offset++] != entrySep) {}
            return offset;
        }
    }

    /**
     * Determine if this String _starts-with_ the specified character.
     *
     * @param ch  a character to look for at the beginning of this String
     *
     * @return True iff this String starts-with the specified character
     */
    Boolean startsWith(Char ch) {
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
    Boolean startsWith(String that) {
        return this.chars.startsWith(that.chars);
    }

    /**
     * Determine if this String _ends-with_ the specified character.
     *
     * @param ch  a character to look for at the end of this String
     *
     * @return True iff this String ends-with the specified character
     */
    Boolean endsWith(Char ch) {
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
    Boolean endsWith(String that) {
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
    conditional Int indexOf(Char value, Int startAt = 0) {
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
    conditional Int lastIndexOf(Char value, Int startAt = MaxValue) {
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
     conditional Int indexOf(String that, Int startAt = 0) {
         Int thisLen = this.size;
         Int thatLen = that.size;

         // there has to be enough room to fit "that"
         if (startAt > thisLen - thatLen) {
             return False;
        }

         // can't start before the start of the string (at zero)
         startAt = startAt.notLessThan(0);

         // break out the special conditions (for small search strings)
         if (thatLen <= 1) {
             // assume that we can find the empty string wherever we look
             if (thatLen == 0) {
                return True, startAt;
            }

             // for single-character strings, use the more efficient single-character search
             return indexOf(that[0], startAt);
        }

         // otherwise, brute force
         Char first = that[0];
         NextTry: while (Int index := indexOf(first, startAt)) {
             if (index > thisLen - thatLen) {
                 return False;
            }
             for (Int of = 1; of < thatLen; ++of) {
                 if (this[index+of] != that[of]) {
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
     conditional Int lastIndexOf(String that, Int startAt = MaxValue) {
         Int thisLen = this.size;
         Int thatLen = that.size;

         // there has to be enough room to fit "that"
         if (startAt < thatLen) {
             return False;
        }

         // can't start beyond the end of the string
         startAt = startAt.notGreaterThan(thisLen);

         // break out the special conditions (for small search strings)
         if (thatLen <= 1) {
             // assume that we can find the empty string wherever we look
             if (thatLen == 0) {
                return True, startAt;
            }

             // for single-character strings, use the more efficient single-character search
             return lastIndexOf(that[0], startAt);
        }

         // otherwise, brute force
         Char first = that[0];
         NextTry: while (Int index := lastIndexOf(first, startAt)) {
             if (index > thisLen - thatLen) {
                 startAt = index - 1;
                 continue NextTry;
            }

             for (Int of = 1; of < thatLen; ++of) {
                 if (this[index+of] != that[of]) {
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
     * @return (conditional) a Matcher resulting from matching the regular expression with this String
     */
    conditional Match matches(RegEx pattern) {
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
     * @return (conditional) a Matcher resulting from matching the start of this String
     */
    conditional Match prefixMatches(RegEx pattern) {
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
     * @return (conditional) a Match resulting from matching the pattern
     */
    conditional Match containsMatch(RegEx pattern, Int offset = 0) {
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
    String! replaceAll(RegEx pattern, String replacement, Int offset = 0) {
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
    String! leftJustify(Int length, Char fill = ' ') {
        if (length <= 0) {
            return "";
        }

        Int append = length - size;
        return switch (append.sign) {
            case Negative: this[0 ..< length];
            case Zero    : this;
            case Positive: new StringBuffer(length)
                                .addAll(chars)
                                .addDup(fill, append)
                                .toString();
        };
    }

    /**
     * Format this String into a right-justified String of the specified length, with the remainder
     * of the new String left-filled with the specified `fill` character. If the specified length is
     * shorter than the size of this String, then the result will be a truncated copy of this
     * String, containing only the last _length_ characters of this String.
     *
     * @param length  the size of the resulting String
     * @param fill    an optional fill character to use
     *
     * @return this String formatted into a left-justified String filled with the specified
     *         character
     */
    String! rightJustify(Int length, Char fill = ' ') {
        if (length <= 0) {
            return "";
        }

        Int append = length - size;
        return switch (append.sign) {
            case Negative: this.substring(-append);
            case Zero    : this;
            case Positive: new StringBuffer(length)
                                .addDup(fill, append)
                                .addAll(chars)
                                .toString();
        };
    }

    /**
     * Format this String into the center of a String with the specified length, with the remainder
     * of the new String left- and right-filled with the specified `fill` character. If the
     * specified length is shorter than the size of this String, then the result will be a truncated copy of this
     * String, containing only the first _length_ characters of this String.
     *
     * @param length  the size of the resulting String
     * @param fill    an optional fill character to use
     *
     * @return this String formatted into a center-justified String filled with the specified
     *         character
     */
    String! center(Int length, Char fill = ' ') {
        if (length <= 0) {
            return "";
        }

        Int append  = length - size;
        Int appendL = append >> 1;
        return switch (append.sign) {
            case Negative: this[0 ..< length];
            case Zero    : this;
            case Positive: new StringBuffer(length)
                                .addDup(fill, appendL)
                                .addAll(chars)
                                .addDup(fill, append-appendL)
                                .toString();
        };
    }

    /**
    * Replace every appearance of the `match` substring in this String with the `replace` String.
    *
    * @param match    the substring to be replaced
    * @param replace  the replacement String
    *
    * @return the resulting String
    */
    String! replace(String! match, String! replace) {
        Int replaceSize = replace.size;

        if (Int matchOffset := indexOf(match)) {
            Int thisSize    = this.size;
            Int matchSize   = match.size;
            Int startOffset = 0;

            StringBuffer buffer = new StringBuffer(thisSize - matchSize + replaceSize);
            do {
                buffer.addAll(chars[startOffset ..< matchOffset]);
                if (replaceSize > 0) {
                    buffer.addAll(replace.chars);
                }
                startOffset = matchOffset + matchSize;
            } while (startOffset < thisSize, matchOffset := indexOf(match, startOffset));

            return buffer.addAll(chars[startOffset ..< thisSize]).toString();
        }
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


    // ----- UniformIndexed methods ----------------------------------------------------------------

    @Override
    @Op("[]") Char getElement(Int index) {
        return chars[index];
    }


    // ----- Sliceable methods ---------------------------------------------------------------------

    @Override
    @Op("[..]") String slice(Range<Int> indexes) {
        return new String(chars[indexes]);
    }


    // ----- conversions ---------------------------------------------------------------------------

    /**
     * @return the UTF-8 conversion of this String into a byte array
     */
    immutable Byte[] utf8() {
        Int    length = calcUtf8Length();
        Byte[] bytes  = new Byte[length];
        Int    actual = formatUtf8(bytes, 0);
        assert actual == length;
        return bytes.makeImmutable();
    }

    /**
     * @return the characters of this String as an array
     */
    immutable Char[] toCharArray() {
        return chars;
    }

    /**
     * @return a Reader over the characters of this String
     */
    Reader toReader() {
        return new io.CharArrayReader(chars);
    }

    @Override
    String! toString() {
        return this;
    }

    /**
     * Convert this `String` to its all-upper-case form.
     *
     * @return the upper-case form of this `String`
     */
    String! toUppercase() {
        Each: for (Char char : chars) {
            if (char != char.uppercase) {
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
    String! toLowercase() {
        Each: for (Char char : chars) {
            if (char != char.lowercase) {
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
    Int calcUtf8Length() {
        Int len = 0;
        for (Char ch : chars) {
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
    Int formatUtf8(Byte[] bytes, Int of) {
        Int len = 0;
        for (Char ch : chars) {
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
    conditional Int isEscaped() {
        Int total = size;
        for (Char ch : chars) {
            if (Int n := ch.isEscaped()) {
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
    Appender<Char> appendEscaped(Appender<Char> buf) {
        for (Char ch : chars) {
            ch.appendEscaped(buf);
        }
        return buf;
    }

    /**
     * @return the string as it would appear in source code, in double quotes and escaped as
     *         necessary
     */
    String quoted() {
        if (Int len := isEscaped()) {
            return appendEscaped(new StringBuffer(len + 2).add('\"')).add('\"').toString();
        } else {
            return new StringBuffer(size+2).add('\"').addAll(this).add('\"').toString();
        }
    }

    /**
     * Remove quotes from this string, and return the unquoted contents.
     *
     * @return `True` iff this string started and ended with double quotes
     * @return (conditional) the contents of the quoted String
     */
    conditional String unquote() {
        Int size = this.size;
        if (size >= 2 && chars[0] == '"' && chars[size-1] == '"') {
            return True, this[0 >..< size-1];
        }

        return False;
    }


    // ----- Hashable functions --------------------------------------------------------------------

    @Override
    static <CompileType extends String> Int64 hashCode(CompileType value) {
        return value.hash;
    }

    @Override
    static <CompileType extends String> Boolean equals(CompileType value1, CompileType value2) {
        return value1.chars == value2.chars;
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
}