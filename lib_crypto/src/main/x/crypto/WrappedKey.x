/**
 * Key material protected by a [KeyWrapper].
 *
 * The algorithm and intended use of the wrapped key are supplied independently when the key is
 * unwrapped; they are not encoded as unauthenticated metadata in this envelope.
 */
const WrappedKey(String algorithm, Byte[] bytes) {
    /**
     * The key-wrapping algorithm used to produce the wrapped bytes.
     */
    String algorithm;

    /**
     * The wrapped key bytes, including the integrity check defined by the algorithm.
     */
    Byte[] bytes;
}
