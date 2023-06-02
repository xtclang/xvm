/**
 * Represents a generator of `Secret` keys used for symmetrical encryption/decryption algorithms.
 */
interface KeyGenerator {
    /**
     * The algorithm implemented by this KeyGenerator.
     */
    @RO Algorithm algorithm;

    /**
     * Generate a `Secret` [CryptoKey] that can be used for symmetrical encoding/decoding.
     *
     * @param name  the name to assign to the generated key
     *
     * @return a [CryptoKey] that is knows to be of the `Secret` form
     */
    CryptoKey generateSecretKey(String name);
}