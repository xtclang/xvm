package org.xvm.asm;


import static org.xvm.util.Handy.isDigit;
import static org.xvm.util.Handy.parseDelimitedString;


/**
 * Represents an Ecstasy module version.
 *
 * @author cp 2017.04.20
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

        for (int i = 0, c = strs.length; i < c; ++i)
            {
            // each of the parts has to be an integer, except for the last which can start with
            // a non-GA designator
            if (!isValidVersionPart(strs[i], i == c-1))
                {
                throw new IllegalStateException("illegal version: " + literal);
                }
            }
        }

    public Version(String[] parts)
        {
        StringBuilder sb  = new StringBuilder();
        boolean       err = parts.length == 0;
        for (int i = 0, c = parts.length; i < c; ++i)
            {
            // each of the parts has to be an integer, except for the last which can start with
            // a non-GA designator
            String part = parts[i];
            if (!isValidVersionPart(part, i == c-1))
                {
                err = true;
                }

            if (i > 0)
                {
                sb.append('.');
                }
            sb.append(part);
            }

        this.literal = sb.toString();
        this.strs    = parts;

        if (err)
            {
            throw new IllegalStateException("illegal version: " + literal);
            }
        }

    public Version(int[] parts)
        {
        assert parts != null;

        // each version indicator must be >= 0, except the second-to-the-last which may be -1 to -5
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
     * @return one of "dev", "ci", "alpha", "beta", "rc", or "ga"
     */
    public String getReleaseCategory()
        {
        for (int part : ensureIntArray())
            {
            if (part < 0)
                {
                return PREFIX[part + PREFIX.length];
                }
            }
        return "ga";
        }

    // TODO
//    /**
//     * Obtain the least significant version indicator for this version that
//     * differentiates it from its base version.
//     *
//     * @return the least significant version indicator of this version
//     */
//    public int getVersionNumber()
//        {
//        return m_ver;
//        }
//
//    /**
//     * Determine if this version adds a dot to its base version, or if it
//     * simply is an increment to the base version.
//     *
//     * @return true iff this version adds a dot to its base version
//     */
//    public boolean isDotVersion()
//        {
//        return m_fDot;
//        }
//
//    /**
//     * Determine how deep this version is in the version hierarchy. In
//     * simple terms, the depth is equal to the number of dots in the version
//     * string.
//     *
//     * @return the depth of this version in the version hierarchy
//     */
//    public int getVersionDepth()
//        {
//        ensureCache();
//        return m_cDepth;
//        }
//
//    /**
//     * Determine the VersionConstant that this VersionConstant represents a
//     * "dot" version of.
//     *
//     * @return the version that this version is a "dot" version of, or null
//     *         if this version represents the most significant version
//     *         indicator (a version depth of 0)
//     */
//    public VersionConstant getUpVersion()
//        {
//        ensureCache();
//        return m_constUpVer;
//        }
//
//    /**
//     * Determine the VersionConstant that this VersionConstant represents an
//     * increment of.
//     *
//     * @return the version that this version is an increment of, or null if
//     *         this version is a "dot" version
//     */
//    public VersionConstant getLeftVersion()
//        {
//        return m_fDot ? null : m_constBaseVer;
//        }
//
//    /**
//     * Populate the lazily populated fields.
//     */
//    private void ensureCache()
//        {
//        if (m_iBase > 0 && m_constBaseVer == null)
//            {
//            // ensureCache can't be called before disassemble is called;
//            // this should only be possible while debugging or similar
//            return;
//            }
//
//        if (m_sVer == null)
//            {
//            VersionConstant constFirstNestedVer = this;
//            while (constFirstNestedVer != null && !constFirstNestedVer.m_fDot)
//                {
//                constFirstNestedVer = constFirstNestedVer.m_constBaseVer;
//                }
//
//            VersionConstant constUpVer = constFirstNestedVer == null
//                    ? null
//                    : constFirstNestedVer.m_constBaseVer;
//
//            String sThisVer = String.valueOf(m_ver);
//            if (constUpVer == null)
//                {
//                m_sVer   = sThisVer;
//                m_cDepth = 0;
//                }
//            else
//                {
//                m_sVer       = constUpVer.getVersionString() + '.' + sThisVer;
//                m_cDepth     = constUpVer.getVersionDepth() + 1;
//                m_constUpVer = constUpVer;
//                }
//            }
//        }
//
//    @Override
//    protected int compareDetails(Constant that)
//        {
//        VersionConstant vThat       = (VersionConstant) that;
//        int             cDepthThis  = this.getVersionDepth();
//        int             cDepthThat  = vThat.getVersionDepth();
//        int             cDepthDelta = cDepthThis - cDepthThat;
//        if (cDepthDelta != 0)
//            {
//            if (cDepthDelta < 0)
//                {
//                // e.g. 1.2 vs. 1.2.3
//                int nResult = this.compareDetails(vThat.getUpVersion());
//                return nResult == 0 && vThat.m_ver > 0 ? cDepthDelta : nResult;
//                }
//            else
//                {
//                // e.g. 1.2.3 vs. 1.2
//                int nResult = this.getUpVersion().compareDetails(vThat);
//                return nResult == 0 && this.m_ver > 0 ? cDepthDelta : nResult;
//                }
//            }
//
//        if (cDepthThis > 0)
//            {
//            int nResult = getUpVersion().compareDetails(vThat.getUpVersion());
//            if (nResult != 0)
//                {
//                return nResult;
//                }
//            }
//
//        // all "up versions" are identical; compare this version
//        return this.m_ver - vThat.m_ver;
//        }

    /**
     * Determine if another version is the same version as this, or derives from this version.
     *
     * @param that  another version
     *
     * @return true iff the specified Version is the same as or is derived from this Version
     */
    public boolean isDerivedFrom(Version that)
        {
        if (this.equals(that))
            {
            return true;
            }

        // TODO
        return false;
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
        if (ints.length > strs.length)
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

    private static final String[] PREFIX = {"dev", "ci", "alpha", "beta", "rc"};

    protected String   literal;
    protected String[] strs;
    protected int[]    ints;
    }
