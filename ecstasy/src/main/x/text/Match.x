/**
 * The results of successfully matching an input String value with a RegEx regular expression.
 */
const Match(RegEx regEx, String text, Range<Int>?[] groups)
    {
    /**
     * The number of capturing groups in this matcher's pattern.
     *
     * Group zero represents the entire matched pattern, and is not included in this count.
     *
     * Valid group indexes for this `Matcher` are any non-negative Int values less than or equal to
     * the `groupCount`.
     */
    Int groupCount.get()
        {
        return groups.size - 1;
        }

    /*
     * Returns the input text captured by the given group during the previous match operation.
     *
     * Valid values for the `index` parameter are non-negative Int values, less than or equal to
     * this matcher's `groupCount`.
     *
     * Group zero is used to represent the entire matched pattern and will be the only group for
     * patterns without capturing groups. Group zero will always be present.
     *
     * @param  index  the index of a capturing group in this matcher's pattern
     *
     * @return True iff the group at the given index index matched part of the input String
     * @return (optional) the substring of the input String matched by the group
     * @return (optional) the Range containing the start and end index the input text of the
     *         specified capturing group
     */
    conditional (String, Range<Int>) group(Int index = 0)
        {
        if (Range<Int> range ?= groups[index])
            {
            return True, text[range], range;
            }
        return False;
        }

    /*
     * Returns the input text captured by the given group during the previous
     * match operation.
     *
     * Valid values for the `index` parameter are non-negative Int values, less than or equal to
     * this matcher's `groupCount`.
     *
     * Group zero is used to represent the entire matched pattern and will be the only group for
     * patterns without capturing groups. Group zero will always be present.
     *
     * @param  group  the index of a capturing group in this matcher's pattern
     *
     * @return the (possibly empty) substring of the input String matched by the group at
     *         the given index
     */
    @Op("[]")
    String? getGroupOrNull(Int group)
        {
        if (Range<Int> range ?= groups[group])
            {
            return text[range];
            }
        return Null;
        }

    /**
     * Finds the next subsequence in the input text that matches the regular expression
     * used to create this Match. The search will begin at the next character after the
     * last character in the input text matched by this Match.
     *
     * @return True iff the input text contains a further sub-sequence that matches this pattern
     * @return (optional) a Match resulting from matching the remaining input string
     */
    conditional Match! next()
        {
        if (Range<Int> range ?= groups[0])
            {
            Int start = range.lastExclusive ? range.last : range.last + 1;
            if (start < text.size)
                {
                return regEx.find(text, start);
                }
            }
        return False;
        }
    }