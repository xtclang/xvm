/**
 * Represents the ability to seal a message using authenticated encryption.
 *
 * Authenticated encryption protects both the confidentiality and integrity of the message.
 * Additional associated data is authenticated but is not encrypted. The encryptor owns nonce
 * generation; callers cannot accidentally reuse a nonce directly. A key must be replaced before
 * it has been used to seal 2^32 messages in total, including use by other encryptor instances.
 */
interface AuthenticatedEncryptor
        extends Closeable {
    /**
     * The algorithm name implemented by this `AuthenticatedEncryptor`.
     */
    @RO String algorithm;

    /**
     * The public key used by the algorithm, if it has one. A `Null` indicates that the encryptor
     * uses a symmetric private key.
     */
    @RO CryptoKey? publicKey;

    /**
     * Encrypt and authenticate the provided data.
     *
     * @param data            the data to encrypt
     * @param associatedData  additional data to authenticate without encrypting
     *
     * @return the encrypted data, authentication tag, and generated nonce
     */
    AuthenticatedCiphertext seal(Byte[] data, Byte[] associatedData = []);
}
