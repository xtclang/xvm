/**
 * A representation of a regular expression pattern.
 *
 * A RegEx can be matched against String values, which if successful will produce a Matcher.
 *
 * A RegEx is a constant, the state of the result of a match is in the Matcher, meaning that a
 * single RegEx may be safely used multiple times to match different input values.
 */
const RegEx(String pattern)
    {
    /**
     * Match this pattern against the entire input value.
     *
     * @param input  the string value to match
     *
     * @return True iff the entire input String matches this pattern
     * @return (optional) a Matcher resulting from matching the input string
     */
    conditional Matcher match(String input)
        {
        return False;
        }

    /**
     * Match this pattern from the beginning of the specified input String.
     *
     * Unlike the `match` method that matches the whole input value this method only matches the
     * beginning, subsequent characters remaining after the pattern was matched are ignored.
     *
     * For example, the expression "[0-9]" matches a single digit.
     *
     * Calling this method with the input "a2bc" will return False, because the beginning of the
     * input is not a digit. Calling this method with the input "2bc" will return True and a
     * `Matcher` that matches the "2", ignoring the remaining "bc".
     *
     * @param input  the string value to match
     *
     * @return True iff the input starts with a sub-sequence that matches this pattern
     * @return (optional) a Matcher resulting from matching the input string
     */
    conditional Matcher matchPrefix(String input)
        {
        return False;
        }

    /**
     * Find the first occurrence of this pattern in the specified input String.
     *
     * This method will start at the beginning of the input and search for the first sub-sequence
     * that matches this pattern. Subsequent matches may be found by calling the `find()` method on
     * the returned `Matcher`.
     *
     * When searching for matches any non-matching sequences will be skipped.
     *
     * For example, the expression `[0-9]` matches a single digit. Calling this method with the
     * input "a1b2c3" will first match the "1", returning `true` and a `Matcher`. Subsequent calls
     * to `Matcher.find()` will match the "2" and the "3".
     *
     * @param input  the string value to match
     *
     * @return True iff the input contains a sub-sequence that matches this pattern
     * @return (optional) a Matcher resulting from matching the input string
     */
    conditional Matcher find(String input)
        {
        return False;
        }

    /**
     * Creates a regular expression that will match the specified String exactly as a literal,
     * ignoring any meta-characters or escape sequences that it contains.
     *
     * For example, the input String `foo*bar` will produce the output String `\Qfoo*bar\E`. The
     * "\Q" and "\E" meta-characters cause the regular expression engine to treat the characters
     * between them literally, so asterisk will ony match an asterisk and the not as a normal
     * expression would as zero or more "o" characters.
     *
     * Any "\E" characters in the input String are escaped to stop the literal pattern terminating
     * early. For example the input String `a*b\Ed*e` will produce the output String
     *`\Qfoo\E\\E\Qbar\E`, which is effectively the regular expression literal "a*b" followed by
     * "\E" followed by the literal "d*e", which again treats the asterisks as asterisks.
     *
     * @param input  the String to be turned into a literal pattern
     *
     * @return a literal String pattern that matches the input String
     */
    static String toLiteral(String input)
        {
        if (Int slashEIndex := input.indexOf("\\E"))
            {
            Int          current = 0;
            StringBuffer buffer  = new StringBuffer();
            buffer.append("\\Q");
            do
                {
                buffer.append(input[current..slashEIndex)).append("\\E\\\\E\\Q");
                current = slashEIndex + 2;
                }
            while (slashEIndex := input.indexOf("\\E", current));

            return buffer.append(input[current..input.size))
                         .append("\\E")
                         .toString();
            }
        else
            {
            return $"\\Q{input}\\E";
            }
        }
    }