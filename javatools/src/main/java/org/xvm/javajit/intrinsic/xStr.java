package org.xvm.javajit.intrinsic;


/**
 * TODO
 *
 * The main design question with xStr is how to best represent its internal storage. There are two
 * obvious answers, of which one must be selected:
 *
 * * Hold the content as a Java String object (java.lang.String). This allows us to leverage the
 *   compressed string optimizations in Java (ISO 8859-1 for single byte or UTF-16 for all others),
 *   and makes it easy to pass the xStr to/from any Java API as a Java String by simply unwrapping
 *   the Java String value. The downsides:
 * * * Java Strings are fundamentally UTF-16, and not actually Unicode -- they can contain UTF-16
 *     formatted surrogate pairs as well as all sorts of illegal Unicode data, so when a Java string
 *     says its length is 16, that could mean any actual Unicode length between 8 and 16 inclusive,
 *     and when you ask for the 5th character, you could get the fifth or the third or the fourth or
 *     a part of any of them. We would still need to do all of the "heavy lifting" for these areas.
 * * * We can't access the internal data of Java strings, so copying information out could be quite
 *     expensive.
 * * * Strings are limited to 2GB, so we would have to do the heavy lifting for that as well.
 *
 * * Hold the content as a read-only `byte[]` in UTF-8 format. This is what we had hoped that the
 *   Java compressed string optimization would do, but it doesn't. We'd have to re-implement a big
 *   portion of the string API, but that's not particularly challenging, and we'd have to do that
 *   anyway to handle the weird cases where the Strings contain supplementary characters (i.e. when
 *   Java uses surrogate pairs). The big downside is that we'll need to convert to/from Java String
 *   any time we need to use Java APIs, which is both time and space wasteful.
 *
 * Also, a String can be larger than the Java 2GB limit.
 */
public class xStr
        extends xObj {

    byte[]  bytes;
    int     offset;
    int     length;


    // 1-byte vs. utf8

    /**
     * When a String is a concatenation of multiple strings, this is used to point to the next part
     * of the string. This allows `xStr` to support >2GB strings.
     */
    xStr next;

    /**
     * This is `true` iff the `byte[]` is "owned" by this `xStr`.
     */
    boolean owned;

    /**
     * This is `true` iff the
     */
    boolean utf8;


    @Override public xType $type() {

        return null;
    }

    @Override public boolean $isImmut() {
        return false;
    }

    @Override public void $makeImmut() {

    }

    @Override public boolean $isA(xType t) {
        return false;
    }

    @Override public xContainer $container() {
        return null;
    }
}
