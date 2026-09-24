/**
 * Represents the ability to recover a secret key protected by a [KeyWrapper].
 */
interface KeyUnwrapper
        extends KeyWrapper {
    /**
     * Verify and recover a wrapped secret key.
     *
     * The name and algorithm are supplied by the caller so that key semantics are established by
     * trusted, out-of-band information rather than unauthenticated envelope metadata.
     *
     * @param wrapped       the wrapped key material
     * @param keyName       the name to assign to the recovered key
     * @param keyAlgorithm  the algorithm for which the recovered key will be used
     *
     * @return `True` iff the wrapped key passes its integrity check and can be recovered
     * @return (conditional) an opaque secret key
     */
    conditional CryptoKey unwrap(
        WrappedKey wrapped, String keyName, String keyAlgorithm);
}
