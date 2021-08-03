/**
 * A representation of a regular expression pattern.
 *
 * A RegEx can be matched against String values, which if successful will produce a Match.
 *
 * A RegEx is a constant, the state of the result of a match is in the Match, meaning that a
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
     * @return (optional) a Match resulting from matching the input string
     */
    conditional Match match(String input)
        {
        return False;
        }

    /**
     * Match this pattern from the beginning of the specified input String.
     *
     * Unlike the `match` method that matches the whole input value this method only matches the
     * beginning, subsequent characters remaining after the pattern was matched are ignored.
     *
     * For example, the expression "[0-9]" matches a single digit, so calling this method with
     * the input "a2bc" will return False, because the beginning of the input is not a digit.
     * Calling this method with the input "2bc" will return True and a `Match` that matches
     * the "2", ignoring the remaining "bc".
     *
     * @param input  the string value to match
     *
     * @return True iff the input starts with a sub-sequence that matches this pattern
     * @return (optional) a Match resulting from matching the input string
     */
    conditional Match matchPrefix(String input)
        {
        return False;
        }

    /**
     * Find the first occurrence of this pattern in the specified input String.
     *
     * This method will start at the beginning of the input and search for the first sub-sequence
     * that matches this pattern. Subsequent matches may be found by calling the `find()` method on
     * the returned `Match`.
     *
     * When searching for matches any non-matching sequences will be skipped.
     *
     * For example, the expression `[0-9]` matches a single digit. Calling this method with the
     * input "a1b2c3" will first match the "1", returning `true` and a `Match`. Subsequent calls
     * to `Match.find()` will match the "2" and the "3".
     *
     * @param input   the string value to match
     * @param offset  the optional index of the character in the input String to begin searching from
     *
     * @return True iff the input contains a sub-sequence that matches this pattern
     * @return (optional) a Match resulting from matching the input string
     */
    conditional Match find(String input, Int offset = 0)
        {
        return False;
        }

    /**
     * Replaces every subsequence of the input text that matches this regular expression pattern
     * with the given replacement string.
     *
     * This method scans the input text looking for matches of the regular expression pattern.
     * Characters that are not part of any match are appended directly to the result string;
     * each match is replaced in the result by the replacement string.
     *
     * Note that backslashes `\` and dollar signs `$` in the replacement string may cause the
     * results to be different than if it were being treated as a literal replacement string.
     * Dollar signs may be treated as references to captured subsequences as described above, and
     * backslashes are used to escape literal characters in the replacement string.
     *
     * @param  text         the string in which to replace matches of the regular expression pattern
     * @param  replacement  the replacement string
     *
     * @return  a string constructed by replacing each matching subsequence by the replacement
     *          string, substituting captured subsequences as needed
     */
    String replaceAll(String text, String replacement)
        {
        TODO
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