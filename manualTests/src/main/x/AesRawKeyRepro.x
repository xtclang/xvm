/**
 * Reproduces raw AES key import failure.
 *
 * The unpatched runtime fails at encrypt() with:
 *     RTError: AES SecretKeyFactory not available
 */
module AesRawKeyRepro {
    package crypto import crypto.xtclang.org;

    import crypto.Algorithms;
    import crypto.Decryptor;
    import crypto.PrivateKey;

    void run() {
        @Inject Algorithms algorithms;

        Byte[] keyBytes = new Byte[32](i -> i.toByte());
        PrivateKey key = new PrivateKey(
            "test-key", "AES", keyBytes.size, keyBytes);

        Decryptor aes = algorithms.decryptorFor("AES", key)
            ?: assert as "AES unavailable";

        Byte[] encrypted = aes.encrypt("hello".utf8());
        assert aes.decrypt(encrypted).unpackUtf8() == "hello";
    }
}
