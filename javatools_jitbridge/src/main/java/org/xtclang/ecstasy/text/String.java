package org.xtclang.ecstasy.text;

import org.xtclang.ecstasy.xConst;

import org.xvm.javajit.Ctx;

import static java.lang.Character.toCodePoint;
import static org.xvm.util.Handy.require;

/**
 * An implementation of an arbitrarily-sized character string data type, using a 64-bit index and
 * length, and  supporting all valid Unicode characters (21-bit codepoint values).
 *
 * A significant design question related to the `xStr` class was how to best represent its internal
 * storage. The obvious default is to hold the contents as a Java String object, or as an array
 * of Java primitive `char` values. The Java String has the benefits of (i) already existing in a
 * well-tested and well-optimized form, (ii) internally optimizing to use ISO 8859-1 for single byte
 * "compressed string" storage, and (iii) using the Java String class would make it quite easy to
 * pass instances of `xStr` to/from any Java API as a Java String, by simply wrapping and unwrapping
 * the Java String value as necessary. Java Strings are fundamentally UTF-16 strings, though, and
 * not "real" Unicode strings; Java Strings can contain UTF-16 formatted surrogate pairs -- which
 * are legal in a UTF-16 encoding, but illegal codepoints in Unicode! Java Strings can also contain
 * badly formed UTF-16, including illegal codepoints and unmatched surrogates. Because of UTF-16,
 * when a Java string has a `length()` of 16, that could mean any actual number of characters in the
 * Unicode string length is somewhere between 8 and 16 inclusive. Similarly, requesting `charAt(5)`
 * from a Java String can return the fifth character, but it can instead return either the third or
 * the fourth character -- or worse, it can return just a half of one of those characters.
 * Addressing these flaws would be a significant undertaking, with significant performance
 * penalties. Furthermore, the internal data of a Java String is not directly accessible, which
 * incurs an additional performance penalty, particularly when copies of that data are necessary.
 * Lastly, Java Strings are limited to 2GB, since the JVM is fundamentally a 32-bit design. In
 * summary, using the Java String class adds significant complexity and could negatively impact
 * performance.
 *
 * An alternative experiment using UTF-8 data was attempted, using a read-only `byte[]` as the
 * storage, and supporting strings >2GB. The engineering concern with this approach was the cost of
 * random access (i.e. access by index). By caching the most recently accessed index and position
 * within the UTF-8 data, the cost of common (e.g. sequential) access patterns was minimized, but
 * still calculated to be significantly more costly than array-based access.
 *
 * The selected design is similar to the "compressed strings" approach in the Java String
 * implementation, but instead of supporting a 1-byte vs 2-byte encoding, `xStr` implements an 8-bit
 * (1-byte) vs 21-bit encoding, since Unicode codepoints are 21-bit values. The underlying data
 * structure is a Java `long` (64-bit integer) array, allowing either 8x 8-bit (ISO 8859-1) or 3x
 * 21-bit (Unicode) values to be stored in each `long`.
 */
public class String
        extends xConst {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct an Ecstasy string from a Java String.
     *
     * @param containerId  the container within which the string is being created
     * @param s            the Java String
     */
    public String(Ctx ctx, java.lang.String s) {
        super(ctx);

        require("s", s);
        next = null;
        if (s.isEmpty()) {
            data = EmptyString.data;
            return;
        }

        // scan string for unicode surrogate pairs
        int utf16len = s.length();
        int pairs    = 0;
        for (int i = 0; i < utf16len; ++i) {
            char ch = s.charAt(i);
            if (ch > 0xFF) {
                unicode = true;
                if (ch >= 0xD800) {
                    if (ch <= 0xDBFF) {
                        if (i == utf16len - 1) {
                            throw new IllegalArgumentException("The string ends with codepoint 0x"
                                    + Integer.toString(ch, 16)
                                    + ", which is the first half of a surrogate pair");
                        }
                        char low = s.charAt(++i);
                        if (low < 0xDC00 || low > 0xDFFF) {
                            throw new IllegalArgumentException("Character 0x"
                                    + Integer.toString(low, 16) + " at offset " + i
                                    + " follows the high surrogate 0x" + Integer.toString(ch, 16)
                                    + ", but is not a low surrogate");
                        }
                        ++pairs;
                    } else if (ch < 0xDFFF) {
                        throw new IllegalArgumentException("Character 0x"
                                + Integer.toString(ch, 16) + " at offset " + i
                                + " is a low surrogate, but does not follow a high surrogate");
                    }
                }
            }
        }

        if (unicode) {
            int unicodeLen = utf16len - pairs;
            data = new long[(unicodeLen + 2) / 3];
            int  packed = 0;
            int  dest   = 0;
            long tri    = 0;
            for (int src = 0; src < utf16len; ++src) {
                char utf16 = s.charAt(src);
                int  uni21 = utf16 < 0xD800 || utf16 > 0xDBFF
                    ? utf16
                    : toCodePoint(utf16, s.charAt(++src));      // combine the surrogate pair
                tri = tri << 21 | uni21;
                if (++packed == 3) {
                    data[dest++] = tri;
                    tri    = 0;
                    packed = 0;
                }
            }
            if (tri != 0) {
                data[dest] = tri << (21 * (3 - packed));
            }
            end = unicodeLen;
        } else {
            data = new long[(utf16len + 7) >>> 3];
            long octo = 0;
            for (int src = 0; src < utf16len; ++src) {
                char ch = s.charAt(src);
                octo = octo << 8 | ch;
                if ((src & 0b111) == 0b111) {
                    data[src>>>3] = octo;
                    octo = 0;
                }
            }
            if (octo != 0) {
                // use `last` as the `src` of the last char in the string
                // if (last & 0b111) == 1, that means that we have 1 unflushed byte, so << 56
                int last = utf16len - 1;
                data[last>>>3] = octo << (~(last & 0b111) << 3);
            }
            end = utf16len;
        }
    }

    /**
     * Construct an Ecstasy String from UTF-8 data
     *
     * @param containerId  the container within which the string is being created
     * @param utf8         UTF-8 data in a byte array
     */
    public String(Ctx ctx, byte[] utf8) {
        // TODO
        this(ctx, null, false, 0, 0, 0, null);
    }

    /**
     * Internal constructor.
     *
     * @param containerId  the container within which the string is being created
     * @param data
     * @param unicode
     * @param hash
     * @param start
     * @param end
     * @param next
     */
    String(Ctx ctx, long[] data, boolean unicode, long hash, int start, int end, String next) {
        super(ctx);
        this.data       = data;
        this.unicode    = unicode;
        this.hash       = hash;
        this.start      = start;
        this.end        = end;
        this.next       = next;
    }

    // ----- fields --------------------------------------------------------------------------------

    /**
     * The storage for the string contents.
     */
    private long[] data;

    /**
     * Index of the first character of the string.
     */
    private int start;

    /**
     * Index of the character following the last character of the string.
     */
    private int end;

    /**
     * Linked overflow for long strings.
     */
    private final String next;

    /**
     * `False` iff the contents are stored in an ISO 8859 (8-bit) format.
     * `True` iff the contents are stored as complete 21-bit Unicode codepoints.
     */
    private boolean unicode;

    /**
     * The lazily computed and cached hash code. The value `0` indicates that the hash code has not
     * yet been computed.
     */
    private long hash;

    /**
     * An empty string. All empty strings are identical.
     */
    public static final String EmptyString = new String(null, new long[0], true, 0, 0, 0, null);

    // ----- xStr API ------------------------------------------------------------------------------

    /**
     * @return `true` iff the string contains no characters
     */
    public boolean empty() {
        return start == end;
    }

    /**
     * @return the length of the string in characters
     */
    public long size() {
        return end - start + (next == null ? 0 : next.size());
    }

    public long utf16Size() {
        long localSize;
        localSize = end - start;
        if (unicode && localSize > 0) {
            // count the surrogate pairs (two Java chars for one Unicode char)
            int  next  = start / 3;
            long tri   = data[next++];
            int  shift = (2 - (start % 3)) * 21;
            for (int c = end - start; c > 0; --c) {
                if (shift < 0) {
                    tri   = data[next++];
                    shift = 42;
                }
                if (((tri >>> shift) & 0x1FFFFF) > 0xFFFF) {
                    ++localSize;
                }
                shift -= 21;
            }
        }
        return localSize + (next == null ? 0 : next.utf16Size());
    }

    /**
     * Get the codepoint located at the specified index within the string.
     *
     * @param index  the zero-based index
     *
     * @return the codepoint of the character at the specified index in the string
     */
    public int get(long index) {
        if (index < 0) {
            oob(index);
        }
        return getContinued(0, index);
    }


    /**
     * (Internal) Get the codepoint located at the specified index within the string.
     *
     * @param skipped  the count of indexes that precede this string, in a linked list of strings
     * @param index    the zero-based index into this string
     *
     * @return the codepoint of the character at the specified index in the string
     */
    private int getContinued(long skipped, long index) {
        int len = end - start;
        if (index > len) {
            if (next == null) {
                oob(skipped + index);
            }
            return next.getContinued(skipped + len, index - len);
        }

        index += start;
        if (unicode) {
            index *= 0x55555556L; // frdc algorithm: https://arxiv.org/abs/1902.01961
            long tri = data[(int) (index >>> 32)];
            return ((int) (tri >>> (21 * (2 - ((int) (((index & 0xFFFFFFFFL) * 3) >>> 32)))))) & 0x1FFFFF;
        }

        return (int) (data[(int) (index >>> 3)] >>> (8 * (~index & 0b111))) & 0xFF;
    }

    public @Override java.lang.String toString() {
        long len = size();
        if (len == 0) {
            return "";
        }

        // way-too-big strings are obvious problems
        if (len > Integer.MAX_VALUE - 8) {
            oob(len);
        }

        // one-byte (ISO 8859-1) format is simple
        return toStringContinued(new StringBuilder((int) utf16Size())).toString();
    }

    /**
     * Create a new Ecstasy `String` based on the specified Java `String`
     *
     * @return a new String object
     */
    public static String of(Ctx ctx, java.lang.String s) {
        // TODO: intern Java style?
        return new String(ctx, s);
    }

    private StringBuilder toStringContinued(StringBuilder buf) {
        if (unicode) {
            int  index = start / 3;
            long tri   = data[index];
            int  shift = (2 - (start % 3)) * 21;
            for (int c = end - start; c > 0; --c) {
                if (shift < 0) {
                    tri   = data[++index];
                    shift = 42;
                }
                buf.appendCodePoint((int) ((tri >>> shift) & 0x1FFFFF));
                shift -= 21;
            }
        } else {
            int  index = start >>> 3;
            long octo  = data[index];
            int  shift = (~start & 0b111) * 8;
            for (int c = end - start; c > 0; --c) {
                if (shift < 0) {
                    octo  = data[++index];
                    shift = 56;
                }
                buf.append((char) ((octo >>> shift) & 0xFF));
                shift -= 8;
            }
        }
        return next == null ? buf : next.toStringContinued(buf);
    }

    // ----- xObj internal -------------------------------------------------------------------------

    /**
     * @param index an illegal index
     *
     * @throws StringIndexOutOfBoundsException
     */
    private boolean oob(long index) {
        if (index >= Integer.MIN_VALUE && index <= Integer.MAX_VALUE) {
            throw new StringIndexOutOfBoundsException((int) index);
        }
        throw new StringIndexOutOfBoundsException(java.lang.String.valueOf(index));
    }
}
