package org.xvm.util;


import java.util.Arrays;

import java.util.stream.IntStream;

import static java.util.Objects.requireNonNull;


/**
 * An immutable, index-addressable view over an {@code int[]}, for index and magnitude vectors that
 * are shared or repeatedly handed out.
 *
 * <p>This is the primitive counterpart to {@link FrozenArray} for integers; see
 * {@link FrozenByteArray} for why the generic type cannot serve. Its motivating consumers differ
 * from the other two: rather than closing a raw escape, it removes a <em>defensive copy paid on
 * every call</em>. Accessors that returned {@code field.clone()} did so precisely because there
 * was no frozen type to hand back - the scattered-{@code .clone()}-convention tax that the
 * array-exposure work exists to retire.</p>
 *
 * <p>The design points are inherited from {@link FrozenArray} and apply unchanged: this is
 * deliberately not a collection type, {@link #unsafeArray()} is a documented read-only escape
 * hatch for hot consumers rather than an oversight, {@link #adopt(int[])} transfers ownership
 * while {@link #copyOf(int[])} is defensive, and there is no {@code equals}/{@code hashCode}
 * override because wrapper identity is not element equality - use {@link #contentEquals}.</p>
 */
public final class FrozenIntArray {
    /** An empty frozen int array. */
    public static final FrozenIntArray EMPTY = new FrozenIntArray(new int[0]);

    /**
     * The wrapped storage; never written after construction, never handed out except through the
     * documented {@link #unsafeArray()} escape hatch.
     */
    private final int[] ints;

    private FrozenIntArray(int[] ints) {
        this.ints = ints;
    }

    /**
     * Wrap the specified array, taking ownership: the caller must not retain, mutate, or hand out
     * the array after this call.
     *
     * @param ints  the array to adopt
     *
     * @return a frozen view backed directly by the array
     */
    public static FrozenIntArray adopt(int[] ints) {
        return requireNonNull(ints, "ints").length == 0 ? EMPTY : new FrozenIntArray(ints);
    }

    /**
     * Wrap a copy of the specified array; the caller keeps ownership of its own array.
     *
     * @param ints  the array to copy
     *
     * @return a frozen view backed by a private copy
     */
    public static FrozenIntArray copyOf(int[] ints) {
        return requireNonNull(ints, "ints").length == 0 ? EMPTY : new FrozenIntArray(Arrays.copyOf(ints, ints.length));
    }

    /**
     * @return the number of elements
     */
    public int size() {
        return ints.length;
    }

    /**
     * @return true iff there are no elements
     */
    public boolean isEmpty() {
        return ints.length == 0;
    }

    /**
     * @param i  the element index
     *
     * @return the element at the specified index
     */
    public int get(int i) {
        return ints[i];
    }

    /**
     * @return a sequential {@link IntStream} over the elements; the array is immutable, so the
     *         stream cannot observe a concurrent modification
     */
    public IntStream stream() {
        return Arrays.stream(ints);
    }

    /**
     * @return a fresh array containing the elements; the caller owns it
     */
    public int[] copy() {
        return Arrays.copyOf(ints, ints.length);
    }

    /**
     * The documented read-only escape hatch: returns the WRAPPED storage itself, so hot consumers
     * (hashing, serialization, {@code System.arraycopy}) avoid a copy. The caller MUST NOT write
     * to the returned array or hand it to anything that might.
     *
     * @return the wrapped array itself
     */
    public int[] unsafeArray() {
        return ints;
    }

    /**
     * @param that  another frozen int array
     *
     * @return true iff both views hold equal elements in the same order
     */
    public boolean contentEquals(FrozenIntArray that) {
        return this == that || Arrays.equals(ints, that.ints);
    }

    /**
     * Unlike its byte and char siblings, this renders the content: the vectors held here are index
     * and version tuples, which are short, and whose content is the useful part in a log or
     * debugger.
     *
     * @return the elements
     */
    @Override
    public String toString() {
        return "FrozenIntArray" + Arrays.toString(ints);
    }
}
