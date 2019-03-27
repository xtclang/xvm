package org.xvm.asm;


import static org.xvm.util.Handy.isDigit;
import static org.xvm.util.Handy.parseDelimitedString;


/**
 * Represents an Ecstasy module version.
 */
public class Version
        implements Comparable<Version>
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a Version from a version string.
     *
     * @param literal  the version string
     */
    public Version(String literal)
        {
        this.literal = literal;
        this.strs    = parseDelimitedString(literal, '.');

        boolean fGA  = true;
        for (int i = 0, c = strs.length; i < c; ++i)
            {
            // each of the parts has to be an integer, except for the last which can start with
            // a non-GA designator
            String part = strs[i];
            if (isValidVersionPart(part, i == c-1))
                {
                // check if it's a non-GA part
                if (!isDigit(part.charAt(0)))
                    {
                    if (!fGA)
                        {
                        throw new IllegalStateException("illegal version: " + literal
                                + " (only one non-GA specifier allowed)");
                        }
                    fGA = false;

                    if (i > 0 && Integer.valueOf(strs[i-1]) == 0)
                        {
                        throw new IllegalStateException("illegal version: " + literal
                                + " (a non-GA specifier cannot follow a dot-zero version)");
                        }
                    }
                }
            else
                {
                throw new IllegalStateException("illegal version: " + literal);
                }
            }
        }

    public Version(String[] parts)
        {
        StringBuilder sb  = new StringBuilder();
        boolean       err = parts.length == 0;
        boolean       fGA = true;
        for (int i = 0, c = parts.length; i < c; ++i)
            {
            // each of the parts has to be an integer, except for the last which can start with
            // a non-GA designator
            String part = parts[i];
            if (isValidVersionPart(part, i == c-1))
                {
                // check if it's a non-GA part
                if (!isDigit(part.charAt(0)))
                    {
                    if (!fGA)
                        {
                        throw new IllegalStateException("illegal version: " + literal
                                + " (only one non-GA specifier allowed)");
                        }
                    fGA = false;

                    if (i > 0 && Integer.valueOf(parts[i-1]) == 0)
                        {
                        throw new IllegalStateException("illegal version: " + literal
                                + " (a non-GA specifier cannot follow a dot-zero version)");
                        }
                    }
                }
            else
                {
                throw new IllegalStateException("illegal version: " + literal);
                }

            if (i > 0)
                {
                sb.append('.');
                }
            sb.append(part);
            }

        this.literal = sb.toString();
        this.strs    = parts;
        }

    public Version(int[] parts)
        {
        assert parts != null;

        // each version indicator must be >= 0, except the second-to-the-last or last, which may be
        // between -1 and -5
        StringBuilder sb  = new StringBuilder();
        boolean       err = parts.length == 0;
        for (int i = 0, c = parts.length; i < c; ++i)
            {
            int part = parts[i];
            if (parts[i] >= 0)
                {
                sb.append(part);
                if (i < c - 1)
                    {
                    sb.append('.');
                    }
                }
            else if (part >= -PREFIX.length)
                {
                sb.append(PREFIX[part + PREFIX.length]);
                switch (c - i)
                    {
                    case 1:
                        // last element; ok
                        break;
                    case 2:
                        // second to last element; last must be >= 0
                        if (parts[i+1] < 0)
                            {
                            err = true;
                            }
                        break;
                    default:
                        err = true;
                        break;
                    }
                if (i > 0 && parts[i-1] == 0)
                    {
                    // previous part must > 0
                    err = true;
                    }
                }
            else
                {
                sb.append("illegal(")
                  .append(i)
                  .append(')');
                err = true;
                }
            }

        this.literal = sb.toString();
        this.ints    = parts;

        if (err)
            {
            throw new IllegalStateException("illegal version: " + literal);
            }
        }


    // ----- accessors -----------------------------------------------------------------------------

    /**
     * Obtain the Version as an array of {@code String}, one for each dot-delimited indicator in
     * the version string.
     *
     * @return an array of Strings
     */
    public String[] toStringArray()
        {
        return ensureStringArray().clone();
        }

    /**
     * Obtain the Version as an array of ints, which may be one element larger than the number of
     * parts in the version due to the manner in which pre-release versions are numbered.
     *
     * @return an array of ints
     */
    public int[] toIntArray()
        {
        return ensureIntArray().clone();
        }

    /**
     * @return true iff the version number indicates a generally available release; false if the
     *         version number indicates a continuous integration build, a dev build, an alpha or
     *         beta release, or a release candidate
     */
    public boolean isGARelease()
        {
        for (int part : ensureIntArray())
            {
            if (part < 0)
                {
                return false;
                }
            }
        return true;
        }

    /**
     * @return a value between -5 and 0, representing "dev", "ci", "alpha", "beta", "rc", or "ga"
     */
    public int getReleaseCategory()
        {
        for (int part : ensureIntArray())
            {
            if (part < 0)
                {
                return part;
                }
            }
        return 0;
        }

    /**
     * @return one of "dev", "ci", "alpha", "beta", "rc", or "ga"
     */
    public String getReleaseCategoryString()
        {
        int n = getReleaseCategory();
        return n < 0 ? PREFIX[n + PREFIX.length] : "ga";
        }

    /**
     * Determine if another version is the same version as this, or derives from this version.
     * <p/>
     * A version is either a base version, the subsequent version of another version, or an revision
     * of another version. A version number is represented as a dot-delimited string of integer
     * values; for example, version "1" is a potential base version number, version "2" is a
     * subsequent version of version "1", and version "1.1" is a revision of version 1.
     * <p/>
     * For each integer in the version string, the first integer is considered the most significant
     * version indicator, and each following integer is less significant, with the last integer
     * being the least significant version indicator. If the least significant version indicator is
     * zero, then the version is identical to a version that does not include that least significant
     * version indicator; in other words, version "1", version "1.0", and version "1.0.0" (etc.) all
     * refer to the same identical version. For purposes of comparison:
     *
     * <ul><li>The actual versions <tt>v<sub>A</sub></tt> is <b>identical to</b> the requested
     * version <tt>v<sub>R</sub></tt> iff after removing every trailing (least significant) "0"
     * indicator, each version indicator from the most significant to the least significant is
     * identical; in other words, version "1.2.1" is identical only to version "1.2.1" (which is
     * identical to version "1.2.1.0").</li>
     * <li>The actual versions <tt>v<sub>A</sub></tt> is <b>substitutable for</b> the requested
     * version <tt>v<sub>R</sub></tt> iff each version indicator of the requested version from the
     * most significant to the least significant is identical to the corresponding version indicator
     * in the actual version, or if the first different version indicator in the actual version is
     * greater than the corresponding version indicator in the requested version; in other words,
     * version "1.2", "1.2.1", and "1.2.1.7", and "1.3" are all substitutable for version "1.2", but
     *  "2.0" and "2.1" are not.</li>
     * <li>In the previous example, to use only one of the versions that begins with "1.2", the
     * requested version <tt>v<sub>R</sub></tt> should be specified as "1.2.0"; versions "1.2",
     * "1.2.1", and "1.2.1.7" are subsitutes for 1.2.0, but versions "1.3", "2.0", and "2.1" are
     * not.</li>
     * </ul>
     *
     * @param that  another version
     *
     * @return true iff the specified Version is the same as or is derived from this Version
     */
    public boolean isSubstitutableFor(Version that)
        {
        if (this.equals(that))
            {
            return true;
            }

        // check all of the shared version parts (except for the last shared version part) to make
        // sure that they are identical; for example, when comparing "1.2.3" and "1.2.4", this would
        // compare both the "1" and the "2" parts, but when comparing "1.2.3" and "1.2", this would
        // only check the "1" part.
        int[] thisInts = this.ensureIntArray();
        int[] thatInts = that.ensureIntArray();
        int   cThis    = thisInts.length;
        int   cThisGA  = thisInts[cThis - 1] < 0 ? cThis - 1 : cThis >= 2 && thisInts[cThis - 2] < 0 ? cThis - 2 : cThis;
        int   cThat    = thatInts.length;
        int   cThatGA  = thatInts[cThat - 1] < 0 ? cThat - 1 : cThat >= 2 && thatInts[cThat - 2] < 0 ? cThat - 2 : cThat;
        int   iLast    = Math.min(cThis, cThat) - 1;
        int   iLastGA  = Math.min(cThisGA, cThatGA) - 1;
        for (int i = 0; i < iLastGA; ++i)
            {
            if (thisInts[i] != thatInts[i])
                {
                return false;
                }
            }

        // if this was a smaller version than that, then this cannot substitute for that
        int nVerDif = iLastGA >= 0 ? thisInts[iLastGA] - thatInts[iLastGA] : 0;
        if (nVerDif < 0)
            {
            return false;
            }

        // if this was a larger version than that, then this can sub for that if we're comparing
        // the last digit of that
        if (nVerDif > 0)
            {
            return cThisGA >= cThatGA;
            }

        // all of the shared GA digits are identical; check the non-shared digits
        if (cThisGA > cThatGA)
            {
            // any remaining version part number in this version higher than zero indicates this
            // could sub for that
            for (int i = cThatGA; i < cThisGA; ++i)
                {
                if (thisInts[i] > 0)
                    {
                    return true;
                    }
                }
            // this could still be substitutable for that, because the GA versions are the same
            }
        else if (cThisGA < cThatGA)
            {
            // any remaining version part number in that version higher than zero indicates this
            // can NOT sub for that; the number of version parts in this is fewer than the number of
            // version parts in that, so the only way that this is substitutable for that is if all
            // subsequent version parts of that are "0"; for example, "1.2" can sub for "1.2.0.0.0"
            for (int i = cThisGA; i < cThatGA; ++i)
                {
                if (thatInts[i] > 0)
                    {
                    return false;
                    }
                }
            // this could still be substitutable for that, because the GA versions are the same
            }

        // the two GA versions are identical; the only thing left to check is the non-GA information
        boolean fThisGA = cThis == cThisGA;
        boolean fThatGA = cThat == cThatGA;
        if (!fThisGA || !fThatGA)
            {
            // at least one is a non-GA
            // if this is GA and that is a non-GA, then this will sub for that
            // if this is non-GA and that is GA, then this can not sub for that
            if (fThisGA ^ fThatGA)
                {
                return fThisGA;
                }

            // they're both pre-release versions; need to compare the pre-release version parts
            int cThisNonGA   = cThis - cThisGA;
            int cThatNonGA   = cThat - cThatGA;
            int cSharedNonGA = Math.min(cThisNonGA, cThatNonGA);
            assert cSharedNonGA == 1 || cSharedNonGA == 2;
            for (int of = 0; of < cSharedNonGA; ++of)
                {
                nVerDif = thisInts[cThisGA + of] - thatInts[cThatGA + of];
                if (nVerDif < 0)
                    {
                    // this is an older pre-release
                    return false;
                    }
                else if (nVerDif > 0)
                    {
                    // this ia newer pre-release
                    return true;
                    }
                }

            // all the shared digits of the pre-release matched; check for non-shared digits of
            // one of the pre-release versions
            if (cThisNonGA != cThatNonGA)
                {
                // one of the pre-release versions has a sub-version
                // if this has a sub-version, then this is newer (and thus substitutable)
                // if that has a sub-version, then that is newer (and this is NOT substitutable)
                return cThisNonGA > cThatNonGA;
                }
            }

        return true;
        }

    /**
     * Compare two versions to determine if they are the same version. This is a different test than
     * the {@link #equals} method, in that two version objects are considered equal iff their
     * version strings are identical, while two versions are considered to be the same version iff
     * they are equal or the only difference between them is an addition of version parts that are
     * all zeros. For example, version "1.2" is the same version as "1.2.0" and "1.2.0.0.0.0" and
     * so on.
     *
     * @param that  another version
     *
     * @return true iff <i>this</i> Version refers to the same exact version as <i>that</i> Version
     */
    public boolean isSameAs(Version that)
        {
        if (this.equals(that))
            {
            return true;
            }

        // check all of the shared version parts to make sure that they are identical
        int[] thisInts = this.ensureIntArray();
        int[] thatInts = that.ensureIntArray();
        int   cThis    = thisInts.length;
        int   cThat    = thatInts.length;
        int   cShared  = Math.min(cThis, cThat);
        for (int i = 0; i < cShared; ++i)
            {
            if (thisInts[i] != thatInts[i])
                {
                return false;
                }
            }

        // all remaining parts need to be "0"
        if (cThis != cThat)
            {
            int[] remaining = cThis > cThat ? thisInts : thatInts;
            for (int i = cShared, c = remaining.length; i < c; ++i)
                {
                if (thatInts[i] != 0)
                    {
                    return false;
                    }
                }
            }

        return true;
        }

    /**
     * If the version ends with ".0", return a version that does not end with ".0" but represents
     * the same version.
     *
     * @return a normalized version
     */
    public Version normalize()
        {
        int[] parts  = ensureIntArray();
        int   cParts = parts.length;
        int   cZeros = 0;
        for (int i = cParts - 1; i > 0; --i)
            {
            if (parts[i] == 0)
                {
                ++cZeros;
                }
            else
                {
                break;
                }
            }

        if (cZeros == 0)
            {
            return this;
            }

        int[] partsNew = new int[cParts - cZeros];
        System.arraycopy(parts, 0, partsNew, 0, cParts - cZeros);
        return new Version(partsNew);
        }


    // ----- Comparable methods --------------------------------------------------------------------

    @Override
    public int compareTo(Version that)
        {
        int[] thisParts = this.ensureIntArray();
        int[] thatParts = that.ensureIntArray();
        int   nDefault  = thisParts.length - thatParts.length;
        for (int i = 0, c = Math.min(thisParts.length, thatParts.length); i < c; ++i)
            {
            if (thisParts[i] != thatParts[i])
                {
                return thisParts[i] - thatParts[i];
                }
            }

        return nDefault;
        }


    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public String toString()
        {
        return literal;
        }

    @Override
    public int hashCode()
        {
        return literal.hashCode();
        }

    @Override
    public boolean equals(Object obj)
        {
        return obj instanceof Version && literal.equals(((Version) obj).literal);
        }


    // ----- debugging assistance ------------------------------------------------------------------

    public String toDebugString()
        {
        StringBuilder sb = new StringBuilder();

        // yes, we could just return the literal value, but doing it the hard way tests to make
        // sure that the parsing works

        sb.append('\"');
        String[] strs  = ensureStringArray();
        boolean  first = true;
        for (String str : strs)
            {
            if (first)
                {
                first = false;
                }
            else
                {
                sb.append('.');
                }
            sb.append(str);
            }
        sb.append('\"');

        int[] ints = ensureIntArray();
        if (ints.length > strs.length || ints[ints.length-1] < 0)
            {
            sb.append(" /* ");
            first = true;
            for (int n : ints)
                {
                if (first)
                    {
                    first = false;
                    }
                else
                    {
                    sb.append('.');
                    }
                sb.append(n);
                }
            sb.append(" */");
            }

        return sb.toString();
        }


    // ----- internal ------------------------------------------------------------------------------

    /**
     * @return the version as an array of Strings
     */
    protected String[] ensureStringArray()
        {
        if (strs == null)
            {
            strs = parseDelimitedString(literal, '.');
            }
        return strs;
        }

    /**
     * @return the version as an array of ints
     */
    protected int[] ensureIntArray()
        {
        if (ints == null)
            {
            String[] parts = ensureStringArray();

            int cInts = parts.length;
            String last = parts[cInts - 1];
            if (!isDigit(last.charAt(0)) && isDigit(last.charAt(last.length() - 1)))
                {
                // starts with a non-GA string indicator but ends with a digit, meaning the last part
                // is actually two parts
                ++cInts;
                }

            ints = new int[cInts];
            int i = 0;
            EachPart:
            for (String part : parts)
                {
                if (isDigit(part.charAt(0)))
                    {
                    ints[i++] = Integer.valueOf(part);
                    }
                else
                    {
                    assert part == last;
                    int ver = -PREFIX.length;
                    for (String prefix : PREFIX)
                        {
                        if (part.startsWith(prefix))
                            {
                            ints[i++] = ver;
                            if (part.length() > prefix.length())
                                {
                                ints[i] = Integer.valueOf(part.substring(prefix.length()));
                                }
                            break EachPart;
                            }
                        ++ver;
                        }
                    throw new IllegalStateException("invalid version token: " + part);
                    }
                }
            }
        return ints;
        }

    /**
     * Examine a part of a version to see if it is a legitimate part of a version.
     *
     * @param part   a part of a dot-delimited version
     *
     * @return true iff the string is a legitimate part of a version
     */
    public static boolean isValidVersionPart(String part, boolean fLast)
        {
        // check to see if it's all numbers
        boolean allDigits = true;
        for (char ch : part.toCharArray())
            {
            if (!isDigit(ch))
                {
                allDigits = false;
                break;
                }
            }
        if (allDigits)
            {
            return true;
            }

        if (fLast)
            {
            for (String prefix : PREFIX)
                {
                if (part.equals(prefix))
                    {
                    return true;
                    }

                if (part.startsWith(prefix))
                    {
                    for (char ch : part.substring(prefix.length()).toCharArray())
                        {
                        if (!isDigit(ch))
                            {
                            return false;
                            }
                        }
                    return true;
                    }
                }
            }

        return false;
        }


    // ----- fields --------------------------------------------------------------------------------

    private static final String[] PREFIX = {"dev", "ci", "qc", "alpha", "beta", "rc"};

    protected String   literal;
    protected String[] strs;
    protected int[]    ints;
    }
