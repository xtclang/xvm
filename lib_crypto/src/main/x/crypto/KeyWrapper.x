/**
 * Represents the ability to protect secret key material using a key-encryption key.
 */
interface KeyWrapper
        extends Closeable {
    /**
     * The key-wrapping algorithm.
     */
    @RO String algorithm;

    /**
     * The secret key-encryption key used to wrap keys.
     */
    @RO CryptoKey wrappingKey;

    /**
     * Protect a secret key.
     *
     * @param key  the secret key to wrap
     *
     * @return the wrapped key material
     *
     * @throws IllegalArgument  if the key form or encoded size is not supported by the algorithm
     */
    WrappedKey wrap(CryptoKey key);
}
