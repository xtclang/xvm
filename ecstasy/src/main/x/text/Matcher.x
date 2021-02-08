/**
 * The result of a call to Pattern.match().
 */
interface Matcher
        extends Const
    {
    @RO Boolean matched;

    /**
     * Returns the number of capturing groups in this matcher's pattern.
     *
     * Group zero denotes the entire pattern by convention. It is not
     * included in this count.
     *
     * Any non-negative integer smaller than or equal to the value
     * returned by this method is guaranteed to be a valid group index for
     * this matcher.
     *
     * @return The number of capturing groups in this matcher's pattern
     */
    @RO Int groupCount;

    /**
     * Return the Pattern used to create this Matcher.
     */
    @RO Pattern pattern;

    /*
     * Returns the input subsequence captured by the given group during the
     * previous match operation.
     *
     * @param  group  the index of a capturing group in this matcher's pattern
     */
    @Op("[]")
    String? group(Int index);

    /**
     * Attempts to find the next subsequence of the input sequence that matches
     * the pattern.
     *
     * @return  True iff a subsequence of the input sequence matches this matcher's pattern
     */
    Boolean find();

    /**
     * Replaces every subsequence of the input sequence that matches the
     * pattern with the given replacement string.
     *
     * This method first resets this matcher.  It then scans the input
     * sequence looking for matches of the pattern.  Characters that are not
     * part of any match are appended directly to the result string; each match
     * is replaced in the result by the replacement string.
     *
     * Note that backslashes `\` and dollar signs `$` in
     * the replacement string may cause the results to be different than if it
     * were being treated as a literal replacement string. Dollar signs may be
     * treated as references to captured subsequences as described above, and
     * backslashes are used to escape literal characters in the replacement
     * string.
     *
     * Invoking this method changes this matcher's state.  If the matcher
     * is to be used in further matching operations then it should first be
     * reset.
     *
     * @param  replacement  the replacement string
     *
     * @return  The string constructed by replacing each matching subsequence
     *          by the replacement string, substituting captured subsequences
     *          as needed
     */
    String replaceAll(String replacement);

    /**
     * Resets this matcher.
     *
     * Resetting a matcher discards all of its explicit state information
     * and sets its append position to zero. The matcher's region is set to the
     * default region, which is its entire character sequence. The anchoring
     * and transparency of this matcher's region boundaries are unaffected.
     *
     * @return this matcher
     */
    Matcher reset();
    }