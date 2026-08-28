package org.xvm.util;


import java.util.Arrays;

import static java.util.Objects.requireNonNull;


/**
 * An immutable, index-addressable view over a {@code byte[]}, for binary payloads that are shared
 * across threads, containers, and interned constants.
 *
 * <p>This is the primitive counterpart to {@link FrozenArray}, which is generic and therefore
 * cannot hold primitives at all. That gap left binary constant payloads
 * ({@code UInt8ArrayConstant}, {@code FPNConstant}, {@code Float128Constant}) handing out a
 * mutable alias of {@code ConstantPool}-interned storage, with no way to express the frozen
 * contract and no visibility to the stage-3 escape ratchet.</p>
 *
 * <p>The design points are inherited from {@link FrozenArray} and apply unchanged: this is
 * deliberately not a collection type, {@link #unsafeArray()} is a documented read-only escape
 * hatch for hot consumers rather than an oversight, {@link #adopt(byte[])} transfers ownership
 * while {@link #copyOf(byte[])} is defensive, and there is no {@code equals}/{@code hashCode}
 * override because wrapper identity is not element equality - use {@link #contentEquals}.</p>
 */
public final class FrozenByteArray {
    /** An empty frozen byte array. */
    public static final FrozenByteArray EMPTY = new FrozenByteArray(new byte[0]);

    /**
     * The wrapped storage; never written after construction, never handed out except through the
     * documented {@link #unsafeArray()} escape hatch.
     */
    private final byte[] bytes;

    private FrozenByteArray(byte[] bytes) {
        this.bytes = bytes;
    }

    /**
     * Wrap the specified array, taking ownership: the caller must not retain, mutate, or hand out
     * the array after this call.
     *
     * @param bytes  the array to adopt
     *
     * @return a frozen view backed directly by the array
     */
    public static FrozenByteArray adopt(byte[] bytes) {
        return requireNonNull(bytes, "bytes").length == 0 ? EMPTY : new FrozenByteArray(bytes);
    }

    /**
     * Wrap a copy of the specified array; the caller keeps ownership of its own array.
     *
     * @param bytes  the array to copy
     *
     * @return a frozen view backed by a private copy
     */
    public static FrozenByteArray copyOf(byte[] bytes) {
        return requireNonNull(bytes, "bytes").length == 0 ? EMPTY : new FrozenByteArray(Arrays.copyOf(bytes, bytes.length));
    }

    /**
     * @return the number of bytes
     */
    public int size() {
        return bytes.length;
    }

    /**
     * @return true iff there are no bytes
     */
    public boolean isEmpty() {
        return bytes.length == 0;
    }

    /**
     * @param i  the byte index
     *
     * @return the byte at the specified index
     */
    public byte get(int i) {
        return bytes[i];
    }

    /**
     * @return a fresh array containing the bytes; the caller owns it
     */
    public byte[] copy() {
        return Arrays.copyOf(bytes, bytes.length);
    }

    /**
     * The documented read-only escape hatch: returns the WRAPPED storage itself, so hot consumers
     * (hashing, serialization, {@code System.arraycopy}, {@code DataOutput.write}) avoid a copy.
     * The caller MUST NOT write to the returned array or hand it to anything that might.
     *
     * @return the wrapped array itself
     */
    public byte[] unsafeArray() {
        return bytes;
    }

    /**
     * @param that  another frozen byte array
     *
     * @return true iff both views hold equal bytes in the same order
     */
    public boolean contentEquals(FrozenByteArray that) {
        return this == that || Arrays.equals(bytes, that.bytes);
    }

    @Override
    public String toString() {
        return "FrozenByteArray[" + bytes.length + " bytes]";
    }
}
