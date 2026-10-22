/**
 * Represents the ability to open a message produced by an [AuthenticatedEncryptor].
 */
interface AuthenticatedDecryptor
        extends AuthenticatedEncryptor {
    /**
     * The private key used by the algorithm. A `Null` indicates that the decryptor is not permitted
     * to expose the private key.
     */
    @RO CryptoKey? privateKey;

    /**
     * Authenticate and decrypt a sealed message.
     *
     * No plaintext is returned unless the ciphertext, nonce, authentication tag, and associated
     * data have all been successfully authenticated.
     *
     * @param encrypted       the sealed message to authenticate and decrypt
     * @param associatedData  the same additional data supplied when the message was sealed
     *
     * @return `True` iff authentication succeeds
     * @return (conditional) the decrypted data
     */
    conditional Byte[] open(AuthenticatedCiphertext encrypted, Byte[] associatedData = []);
}
