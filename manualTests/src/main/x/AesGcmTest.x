/**
 * End-to-end smoke test for AES-GCM authenticated encryption.
 */
module AesGcmTest {
    package crypto import crypto.xtclang.org;

    import crypto.Algorithms;
    import crypto.AuthenticatedCiphertext;
    import crypto.AuthenticatedDecryptor;
    import crypto.PrivateKey;

    void run() {
        @Inject Algorithms algorithms;

        PrivateKey key = new PrivateKey(
            "aes-gcm-smoke",
            "AES",
            32,
            new Byte[32](i -> i.toByte())
        );
        AuthenticatedDecryptor decryptor =
            algorithms.authenticatedDecryptorFor("AES/GCM/NoPadding", key)
                ?: assert as "AES-GCM unavailable";

        Byte[] data = "authenticated encryption".utf8();
        Byte[] aad  = "manual-test".utf8();
        AuthenticatedCiphertext encrypted = decryptor.seal(data, aad);

        assert Byte[] opened := decryptor.open(encrypted, aad);
        assert opened == data;
    }
}
