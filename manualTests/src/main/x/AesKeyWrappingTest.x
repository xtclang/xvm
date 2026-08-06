/**
 * End-to-end smoke test for AES key wrapping.
 */
module AesKeyWrappingTest {
    package crypto import crypto.xtclang.org;

    import crypto.Algorithms;
    import crypto.CryptoKey;
    import crypto.KeyUnwrapper;
    import crypto.PrivateKey;
    import crypto.WrappedKey;

    void run() {
        @Inject Algorithms algorithms;

        PrivateKey wrappingKey = new PrivateKey(
            "wrapping-key",
            "AES",
            32,
            new Byte[32](i -> i.toByte())
        );
        PrivateKey key = new PrivateKey(
            "application-key",
            "AES",
            16,
            new Byte[16](i -> (i + 32).toByte())
        );
        KeyUnwrapper unwrapper =
            algorithms.keyUnwrapperFor("AES/KW/NoPadding", wrappingKey)
                ?: assert as "AES-KW unavailable";

        WrappedKey wrapped = unwrapper.wrap(key);

        assert CryptoKey recovered := unwrapper.unwrap(wrapped, "recovered-key", "AES");
        assert unwrapper.wrap(recovered) == wrapped;
    }
}
