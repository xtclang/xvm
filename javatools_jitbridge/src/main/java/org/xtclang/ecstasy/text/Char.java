package org.xtclang.ecstasy.text;

import org.xtclang.ecstasy.xConst;

import org.xvm.javajit.Ctx;

/**
 * Native shell for "ecstasy.text.Char".
 */
public class Char extends xConst {
    private Char(int codepoint) {
        super(null);
        $value = codepoint;
    }

    public static Char $new$0$p(Ctx ctx, int codepoint) {
        return $box(codepoint);
    }

    /**
     * Lazily populated cache.
     */
    private static final Char[][] $cache = new Char[1088][];

    public static final int $MaxValue = 0x10FFFF;

    public final int $value;

    public static Char $box(int codepoint) {
        Char ch = null;

        assert codepoint >= 0 && codepoint <= $MaxValue;

        // the cache is divided in 0x440 (1088) pages of 0x400 (1024) characters;
        // the pages are lazily created
        Char[] page = $cache[codepoint >>> 10];
        if (page == null) {
            $cache[codepoint >>> 10] = page = new Char[1024];
        } else {
            ch = page[codepoint & 0x3FF];
        }

        // the Char objects in the pages are lazily created
        if (ch == null) {
            page[codepoint & 0x3FF] = ch = new Char(codepoint);
        }

        return ch;
    }

    @Override
    public String toString(Ctx ctx) {
        return String.of(null, toString());
    }

    @Override
    public java.lang.String toString() {
        return java.lang.Character.toString($value);
    }
}
