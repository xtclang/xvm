import libcrypto.AuthenticatedCiphertext;
import libcrypto.AuthenticatedDecryptor;
import libcrypto.CryptoKey;

/**
 * The native [AuthenticatedDecryptor] implementation.
 */
service RTAuthenticatedDecryptor(String algorithm)
        implements AuthenticatedDecryptor {

    construct(String algorithm, CryptoKey privateKey) {
        this.algorithm  = algorithm;
        this.privateKey = privateKey;
    }

    @Override
    CryptoKey? publicKey.get() = Null;

    @Override
    CryptoKey? privateKey;

    /**
     * The number of messages sealed by this engine. NIST SP 800-38D limits randomly generated
     * 96-bit IVs to at most 2^32 invocations under one key.
     */
    private Int sealCount;

    @Override
    AuthenticatedCiphertext seal(Byte[] data, Byte[] associatedData = []) {
        if (sealCount >= MaxSeals) {
            throw new IllegalState("AES-GCM key usage limit reached; rekey before sealing more data");
        }
        ++sealCount;

        if (Object secret := RTKeyStore.extractSecret(privateKey ?: assert)) {
            (Byte[] nonce, Byte[] ciphertext) = seal(algorithm, secret, data, associatedData);
            return new AuthenticatedCiphertext(algorithm, nonce, ciphertext);
        }

        throw new IllegalState($"Unsupported key {privateKey}");
    }

    @Override
    conditional Byte[] open(
            AuthenticatedCiphertext encrypted, Byte[] associatedData = []) {
        if (encrypted.algorithm != algorithm) {
            return False;
        }

        if (Object secret := RTKeyStore.extractSecret(privateKey ?: assert)) {
            return open(algorithm, secret, encrypted.nonce, encrypted.ciphertext, associatedData);
        }

        throw new IllegalState($"Unsupported key {privateKey}");
    }

    @Override
    void close(Exception? cause = Null) {}

    @Override
    String toString() {
        return $"{algorithm.quoted()} authenticated decryptor";
    }


    // ----- native helpers ------------------------------------------------------------------------

    protected (Byte[] nonce, Byte[] ciphertext)
            seal(String algorithm, Object secret, Byte[] data, Byte[] associatedData) {
        TODO("Native");
    }

    protected conditional Byte[]
            open(String algorithm, Object secret, Byte[] nonce, Byte[] ciphertext,
                 Byte[] associatedData) {
        TODO("Native");
    }


    // ----- constants -----------------------------------------------------------------------------

    static Int MaxSeals = 0x1_0000_0000;
}
