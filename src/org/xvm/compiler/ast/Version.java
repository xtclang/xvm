package org.xvm.compiler.ast;


import org.xvm.compiler.Token;

import org.xvm.util.PackedInteger;

import static org.xvm.util.Handy.isDigit;
import static org.xvm.util.Handy.parseDelimitedString;


/**
 * A Version number is just a list of tokens, but it's wrapped in this class to allow for related
 * functionality to be added later.
 *
 * @author cp 2017.04.03
 */
public class Version
    {
    public Version(Token literal)
        {
        this.literal = literal;
        assert literal.getId() == Token.Id.LIT_STRING;
        }

    @Override
    public String toString()
        {
        StringBuilder sb = new StringBuilder();

        // yes, we could just return the literal value, but doing it the hard way tests to make
        // sure that the parsing works

        sb.append('\"');
        String[] strs  = toStringArray();
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

        int[] ints = toIntArray();
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

    public String[] toStringArray()
        {
        if (strs == null)
            {
            strs = parseDelimitedString((String) literal.getValue(), '.');
            }
        return strs;
        }

    /**
     * Convert the Version to an array of ints, which may be one element larger than the number of
     * parts in the version.
     *
     * @return an array of ints
     */
    public int[] toIntArray()
        {
        if (ints == null)
            {
            String[] parts = toStringArray();

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

    public static final String[] PREFIX = {"dev", "ci", "alpha", "beta", "rc"};

    public final Token literal;

    private String[] strs;
    private int[] ints;
    }
