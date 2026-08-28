package org.xvm.util;


import java.util.Arrays;
import java.util.Iterator;
import java.util.NoSuchElementException;

import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;


/**
 * An immutable, index-addressable view over a Java array, for metadata that is shared across
 * threads, containers, and interned constants.
 *
 * <p>This is the stage-3 representation from the array-exposure hardening plan: raw
 * {@code T[]} metadata fields hand every consumer a mutable alias of storage that interned
 * constants share (one array can back two constants after adoption), so safety rests on
 * scattered {@code .clone()} conventions at every write site. A {@code FrozenArray} makes the
 * contract structural instead: no {@code set}, no exposed storage mutation, and the sharing of
 * one instance between constants is provably harmless.</p>
 *
 * <p>Deliberate design points, per the audit:</p>
 * <ul>
 *   <li>NOT a {@code List}: {@code AbstractList} invites {@code set()}-shaped confusion, and
 *       {@code Arrays.asList} views are exactly the live-writable hazard being removed.</li>
 *   <li>{@link #unsafeArray()} is the documented escape hatch for hot consumers (hash
 *       computation, serialization, {@code System.arraycopy}, the JIT build path) where a
 *       per-call copy would be a measurable regression. Callers of {@code unsafeArray()} MUST
 *       NOT write to or hand out the returned array.</li>
 *   <li>{@link #adopt(Object[])} documents ownership transfer: the caller must not retain or
 *       mutate the array afterward. {@link #copyOf(Object[])} is the defensive alternative
 *       when the caller keeps its array.</li>
 *   <li>No {@code equals}/{@code hashCode} override: wrapper identity is not element
 *       equality, and the owning constants already hash/compare their elements through
 *       {@link #unsafeArray()}. Use {@link #contentEquals} for element comparison.</li>
 * </ul>
 */
public final class FrozenArray<T>
        implements Iterable<T> {
    /**
     * The wrapped storage; never written after construction, never handed out except through
     * the documented {@link #unsafeArray()} escape hatch.
     */
    private final T[] f_aElem;

    private FrozenArray(T[] aElem) {
        f_aElem = aElem;
    }

    /**
     * Wrap the specified array, taking ownership: the caller must not retain, mutate, or hand
     * out the array after this call.
     *
     * @param aElem  the array to adopt
     * @param <T>    the element type
     *
     * @return a frozen view backed directly by the array
     */
    public static <T> FrozenArray<T> adopt(T[] aElem) {
        return new FrozenArray<>(requireNonNull(aElem, "aElem"));
    }

    /**
     * Wrap a copy of the specified array; the caller keeps ownership of its own array.
     *
     * @param aElem  the array to copy
     * @param <T>    the element type
     *
     * @return a frozen view backed by a private copy
     */
    public static <T> FrozenArray<T> copyOf(T[] aElem) {
        return new FrozenArray<>(requireNonNull(aElem, "aElem").clone());
    }

    /**
     * @return the number of elements
     */
    public int size() {
        return f_aElem.length;
    }

    /**
     * @return true iff there are no elements
     */
    public boolean isEmpty() {
        return f_aElem.length == 0;
    }

    /**
     * @param i  the element index
     *
     * @return the element at the specified index
     */
    public T get(int i) {
        return f_aElem[i];
    }

    /**
     * @return a sequential {@link Stream} over the elements; the array is immutable, so the stream
     *         cannot observe a concurrent modification
     */
    public Stream<T> stream() {
        return Arrays.stream(f_aElem);
    }

    /**
     * @return a fresh array containing the elements; the caller owns it
     */
    public T[] copy() {
        return f_aElem.clone();
    }

    /**
     * The documented read-only escape hatch: returns the WRAPPED storage itself, so hot
     * consumers (hashing, serialization, arraycopy, the JIT build plane) avoid a copy. The
     * caller MUST NOT write to the returned array or hand it to anything that might.
     *
     * @return the wrapped array itself
     */
    public T[] unsafeArray() {
        return f_aElem;
    }

    /**
     * @param that  another frozen array
     *
     * @return true iff both views hold equal elements in the same order
     */
    public boolean contentEquals(FrozenArray<?> that) {
        return this == that || Arrays.equals(f_aElem, that.f_aElem);
    }

    @Override
    public Iterator<T> iterator() {
        return new Iterator<>() {
            private int iNext;

            @Override
            public boolean hasNext() {
                return iNext < f_aElem.length;
            }

            @Override
            public T next() {
                if (iNext >= f_aElem.length) {
                    throw new NoSuchElementException();
                }
                return f_aElem[iNext++];
            }
        };
    }

    @Override
    public String toString() {
        return "FrozenArray" + Arrays.toString(f_aElem);
    }
}
