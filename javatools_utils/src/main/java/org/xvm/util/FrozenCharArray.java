package org.xvm.util;


import java.util.Arrays;

import static java.util.Objects.requireNonNull;


/**
 * An immutable, index-addressable view over a {@code char[]}, for text payloads that are shared
 * across threads and containers.
 *
 * <p>This is the primitive counterpart to {@link FrozenArray} for characters; see
 * {@link FrozenByteArray} for why the generic type cannot serve. The motivating consumer is the
 * runtime's immutable-String backing store, which handed out a mutable alias of the very array
 * whose immutability the Ecstasy {@code String} type guarantees.</p>
 *
 * <p>The design points are inherited from {@link FrozenArray} and apply unchanged: this is
 * deliberately not a collection type, {@link #unsafeArray()} is a documented read-only escape
 * hatch for hot consumers rather than an oversight, {@link #adopt(char[])} transfers ownership
 * while {@link #copyOf(char[])} is defensive, and there is no {@code equals}/{@code hashCode}
 * override because wrapper identity is not element equality - use {@link #contentEquals}.</p>
 */
public final class FrozenCharArray {
    /** An empty frozen char array. */
    public static final FrozenCharArray EMPTY = new FrozenCharArray(new char[0]);

    /**
     * The wrapped storage; never written after construction, never handed out except through the
     * documented {@link #unsafeArray()} escape hatch.
     */
    private final char[] chars;

    private FrozenCharArray(char[] chars) {
        this.chars = chars;
    }

    /**
     * Wrap the specified array, taking ownership: the caller must not retain, mutate, or hand out
     * the array after this call.
     *
     * @param chars  the array to adopt
     *
     * @return a frozen view backed directly by the array
     */
    public static FrozenCharArray adopt(char[] chars) {
        return requireNonNull(chars, "chars").length == 0 ? EMPTY : new FrozenCharArray(chars);
    }

    /**
     * Wrap a copy of the specified array; the caller keeps ownership of its own array.
     *
     * @param chars  the array to copy
     *
     * @return a frozen view backed by a private copy
     */
    public static FrozenCharArray copyOf(char[] chars) {
        return requireNonNull(chars, "chars").length == 0 ? EMPTY : new FrozenCharArray(Arrays.copyOf(chars, chars.length));
    }

    /**
     * @return the number of characters
     */
    public int size() {
        return chars.length;
    }

    /**
     * @return true iff there are no characters
     */
    public boolean isEmpty() {
        return chars.length == 0;
    }

    /**
     * @param i  the character index
     *
     * @return the character at the specified index
     */
    public char get(int i) {
        return chars[i];
    }

    /**
     * @return a fresh array containing the characters; the caller owns it
     */
    public char[] copy() {
        return Arrays.copyOf(chars, chars.length);
    }

    /**
     * The documented read-only escape hatch: returns the WRAPPED storage itself, so hot consumers
     * (hashing, {@code System.arraycopy}, {@code String} construction) avoid a copy. The caller
     * MUST NOT write to the returned array or hand it to anything that might.
     *
     * @return the wrapped array itself
     */
    public char[] unsafeArray() {
        return chars;
    }

    /**
     * @param that  another frozen char array
     *
     * @return true iff both views hold equal characters in the same order
     */
    public boolean contentEquals(FrozenCharArray that) {
        return this == that || Arrays.equals(chars, that.chars);
    }

    /**
     * @return the characters as a {@link String}; unlike {@link #toString()} this renders the full
     *         content, and is the natural conversion for a text payload
     */
    public String asString() {
        return new String(chars);
    }

    /**
     * Reports the length rather than the content: a text payload is unbounded, and this type is
     * used where a debugger or log statement may render it implicitly. Use {@link #asString()} to
     * obtain the content deliberately.
     *
     * @return a bounded description
     */
    @Override
    public String toString() {
        return "FrozenCharArray[" + chars.length + " chars]";
    }
}
