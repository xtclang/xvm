/**
 * The results of successfully matching a String value with a RegEx regular expression.
 */
class Matcher(RegEx regEx, Int groupCount)
    {
    /**
     * The regular expression used to create this Matcher.
     */
    public/private RegEx regEx;

    /**
     * The number of capturing groups in this matcher's pattern.
     *
     * Group zero represents the entire pattern, and is not included in this count.
     *
     * Valid group indexes for this `Matcher` are any non-negative Int values less than or equal to
     * the `groupCount`.
     */
    public/private Int groupCount;

    /*
     * Returns the input subsequence captured by the given group during the previous match operation.
     *
     * Valid values for the `index` parameter are non-negative Int values, less than or equal to
     * this matcher's `groupCount`.
     *
     * Group zero is used to represent the entire matched pattern and will be the only group for
     * patterns without capturing groups.
     *
     * @param  index  the index of a capturing group in this matcher's pattern
     *
     * @return True iff the group at the given index index matched part of the input String
     * @return (optional) the substring of the input String matched by the group
     */
    conditional String group(Int index)
        {
        return False;
        }

    /*
     * Returns the input subsequence captured by the given group during the previous
     * match operation.
     *
     * Valid values for the `index` parameter are non-negative Int values, less than or equal to
     * this matcher's `groupCount`.
     *
     * Group zero is used to represent the entire matched pattern and will be the only group for
     * patterns without capturing groups.
     *
     * @param  index  the index of a capturing group in this matcher's pattern
     *
     * @return the (possibly empty) substring of the input String matched by the group at the given
     *         index
     */
    @Op("[]")
    String? getGroupOrNull(Int index)
        {
        return Null;
        }

    /**
     * Find the next subsequence of the input sequence that matches the pattern.
     *
     * If this `Matcher` was created from `Pattern.match(String input)` there will be no subsequent
     * matches and this method will return `False`. If this `Matcher` was created from
     *`Pattern.matchPrefix(String input)` or `Pattern.find(String input)` there may be subsequent
     * matches.
     *
     * @return  True iff the input sequence contains another match of this matcher's pattern
     */
    Boolean next()
        {
        return False;
        }

    /**
     * Replaces every subsequence of the input sequence that matches the pattern with the given
     * replacement string.
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
     * @param  replacement  the replacement string
     *
     * @return  the string constructed by replacing each matching subsequence by the replacement
     *          string, substituting captured subsequences as needed
     */
    String replaceAll(String replacement)
        {
        TODO
        }
    }