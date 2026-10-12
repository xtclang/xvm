/**
 * The self-contained result of authenticated encryption.
 *
 * The [ciphertext] contains both the encrypted data and its authentication tag, using the layout
 * defined by the named [algorithm]. The [nonce] is public information, but it must not be reused
 * with the same key; instances are produced by [AuthenticatedEncryptor.seal], which generates the
 * nonce on behalf of the caller.
 */
const AuthenticatedCiphertext(String algorithm, Byte[] nonce, Byte[] ciphertext) {
    /**
     * The name of the authenticated-encryption algorithm.
     */
    String algorithm;

    /**
     * The nonce generated for this encryption operation.
     */
    Byte[] nonce;

    /**
     * The encrypted data followed by its authentication tag.
     */
    Byte[] ciphertext;
}
