/**
 * Unit tests for the crypto module.
 */
module CryptoTest {
    package crypto import crypto.xtclang.org;
    package xunit import xunit.xtclang.org;

    import crypto.Algorithm;
    import crypto.Algorithms;
    import crypto.AuthenticatedCiphertext;
    import crypto.AuthenticatedDecryptor;
    import crypto.AuthenticatedEncryptor;
    import crypto.CryptoKey;
    import crypto.KeyGenerator;
    import crypto.KeyUnwrapper;
    import crypto.KeyWrapper;
    import crypto.PrivateKey;
    import crypto.PublicKey;
    import crypto.WrappedKey;
    import xunit.assertions.assertThrows;

    /**
     * Tests for AES-GCM authenticated encryption.
     */
    class AesGcmTest {
        @Inject Algorithms algorithms;

        @Test
        void shouldRoundTripSupportedKeySizes() {
            for (Int keySize : [16, 24, 32]) {
                AuthenticatedDecryptor decryptor = decryptorFor(newKey(keySize));
                Byte[] data = new Byte[73](i -> (i * 3).toByte()).freeze(inPlace=True);
                Byte[] aad  = "tenant:field".utf8();

                AuthenticatedCiphertext encrypted = decryptor.seal(data, aad);

                assert encrypted.algorithm == "AES/GCM/NoPadding";
                assert encrypted.nonce.size == 12;
                assert encrypted.ciphertext.size == data.size + 16;
                assert Byte[] opened := decryptor.open(encrypted, aad);
                assert opened == data;
            }
        }

        @Test
        void shouldRoundTripEmptyDataAndAssociatedData() {
            PrivateKey key = newKey(32);
            assert AuthenticatedEncryptor encryptor :=
                algorithms.authenticatedEncryptorFor("AES/GCM/NoPadding", key);
            AuthenticatedDecryptor decryptor = decryptorFor(key);

            AuthenticatedCiphertext encrypted = encryptor.seal([]);

            assert encrypted.ciphertext.size == 16;
            assert Byte[] opened := decryptor.open(encrypted);
            assert opened.empty;
        }

        @Test
        void shouldGenerateAUniqueNonce() {
            AuthenticatedDecryptor decryptor = decryptorFor(newKey(32));
            Byte[] data = "same plaintext".utf8();

            AuthenticatedCiphertext first  = decryptor.seal(data);
            AuthenticatedCiphertext second = decryptor.seal(data);

            assert first.nonce != second.nonce;
            assert first.ciphertext != second.ciphertext;
        }

        @Test
        void shouldOpenNistKnownAnswerVector() {
            PrivateKey key = new PrivateKey(
                "nist-aes-128",
                "AES",
                16,
                #00000000000000000000000000000000
            );
            AuthenticatedDecryptor decryptor = decryptorFor(key);
            AuthenticatedCiphertext encrypted = new AuthenticatedCiphertext(
                "AES/GCM/NoPadding",
                #000000000000000000000000,
                #58E2FCCEFA7E3061367F1D57A4E7455A
            );

            assert Byte[] opened := decryptor.open(encrypted);
            assert opened.empty;
        }

        @Test
        void shouldRejectModifiedCiphertextAndTag() {
            AuthenticatedDecryptor decryptor = decryptorFor(newKey(32));
            AuthenticatedCiphertext encrypted = decryptor.seal("secret".utf8());
            Byte[] changed = copy(encrypted.ciphertext);
            changed[0] ^= 1;

            assert !decryptor.open(new AuthenticatedCiphertext(
                encrypted.algorithm, encrypted.nonce, changed));

            changed = copy(encrypted.ciphertext);
            changed[changed.size - 1] ^= 1;

            assert !decryptor.open(new AuthenticatedCiphertext(
                encrypted.algorithm, encrypted.nonce, changed));
        }

        @Test
        void shouldRejectModifiedNonceAndAssociatedData() {
            AuthenticatedDecryptor decryptor = decryptorFor(newKey(32));
            Byte[] aad = "partner:credential".utf8();
            AuthenticatedCiphertext encrypted = decryptor.seal("secret".utf8(), aad);
            Byte[] changedNonce = copy(encrypted.nonce);
            changedNonce[0] ^= 1;

            assert !decryptor.open(new AuthenticatedCiphertext(
                encrypted.algorithm, changedNonce, encrypted.ciphertext), aad);
            assert !decryptor.open(encrypted, "other context".utf8());
        }

        @Test
        void shouldRejectWrongKeyAndMalformedEnvelope() {
            AuthenticatedCiphertext encrypted = decryptorFor(newKey(32)).seal("secret".utf8());
            AuthenticatedDecryptor wrongKey = decryptorFor(newKey(32, 1));

            assert !wrongKey.open(encrypted);
            assert !wrongKey.open(new AuthenticatedCiphertext(
                "other", encrypted.nonce, encrypted.ciphertext));
            assert !wrongKey.open(new AuthenticatedCiphertext(
                encrypted.algorithm, #0001, encrypted.ciphertext));
            assert !wrongKey.open(new AuthenticatedCiphertext(
                encrypted.algorithm, encrypted.nonce, #0001));
        }

        @Test
        void shouldAcceptOpaqueGeneratedKey() {
            assert KeyGenerator generator := algorithms.keyGeneratorFor("AES");
            CryptoKey key = generator.generateSecretKey("aes-gcm-test");
            AuthenticatedDecryptor decryptor = decryptorFor(key);

            AuthenticatedCiphertext encrypted = decryptor.seal("opaque".utf8());

            assert Byte[] opened := decryptor.open(encrypted);
            assert opened.unpackUtf8() == "opaque";
        }

        @Test
        void shouldAcceptByteArraySlices() {
            AuthenticatedDecryptor decryptor = decryptorFor(newKey(32));
            Byte[] dataBacking = #00010203040506;
            Byte[] aadBacking  = #101112131415;
            Byte[] data = dataBacking[1..<6];
            Byte[] aad  = aadBacking[1..<5];

            AuthenticatedCiphertext encrypted = decryptor.seal(data, aad);

            assert Byte[] opened := decryptor.open(encrypted, aad);
            assert opened == data;
        }

        @Test
        void shouldReportMetadataAndRejectInvalidKeys() {
            Algorithm algorithm = algorithms.byCategory[
                    Algorithm.Category.AuthenticatedEncryption.ordinal]
                    .get("AES/GCM/NoPadding") ?: assert;

            assert algorithm.blockSize == 16;
            assert !algorithms.authenticatedDecryptorFor(
                algorithm, new PrivateKey("wrong-size", "AES", 15, #000102030405060708090A0B0C0D0E));
            assert !algorithms.authenticatedDecryptorFor(
                algorithm, new PrivateKey("wrong-algorithm", "HmacSHA256", 16,
                    #000102030405060708090A0B0C0D0E0F));
            assert !algorithms.authenticatedDecryptorFor(
                algorithm, new PublicKey("public", "AES", 16,
                    #000102030405060708090A0B0C0D0E0F));
        }

        private AuthenticatedDecryptor decryptorFor(CryptoKey key) {
            return algorithms.authenticatedDecryptorFor("AES/GCM/NoPadding", key)
                    ?: assert as "AES-GCM unavailable";
        }

        private PrivateKey newKey(Int size, Int offset = 0) {
            return new PrivateKey(
                "aes-gcm-test",
                "AES",
                size,
                new Byte[size](i -> (i + offset).toByte())
            );
        }

        private Byte[] copy(Byte[] bytes) {
            return new Byte[bytes.size](i -> bytes[i]);
        }
    }

    /**
     * Tests for AES key wrapping.
     */
    class AesKeyWrappingTest {
        @Inject Algorithms algorithms;

        @Test
        void shouldMatchRfc3394KnownAnswerVector() {
            PrivateKey wrappingKey = new PrivateKey(
                "rfc3394-kek", "AES", 16, #000102030405060708090A0B0C0D0E0F);
            PrivateKey key = new PrivateKey(
                "rfc3394-key", "AES", 16, #00112233445566778899AABBCCDDEEFF);
            KeyUnwrapper unwrapper = unwrapperFor("AES/KW/NoPadding", wrappingKey);

            WrappedKey wrapped = unwrapper.wrap(key);

            assert wrapped.bytes == #1FA68B0A8112B447AEF34BD8FB5A7B829D3E862371D2CFE5;
            assert CryptoKey recovered := unwrapper.unwrap(wrapped, "recovered", "AES");
            assert !recovered.isVisible();
            assert unwrapper.wrap(recovered) == wrapped;
        }

        @Test
        void shouldMatchRfc5649KnownAnswerVector() {
            PrivateKey wrappingKey = new PrivateKey(
                "rfc5649-kek", "AES", 24, #5840DF6E29B02AF1AB493B705BF16EA1AE8338F4DCC176A8);
            PrivateKey key = new PrivateKey(
                "rfc5649-key", "HmacSHA1", 20, #C37B7E6492584340BED12207808941155068F738);
            KeyUnwrapper unwrapper = unwrapperFor("AES/KWP/NoPadding", wrappingKey);

            WrappedKey wrapped = unwrapper.wrap(key);

            assert wrapped.bytes ==
                #138BDEAA9B8FA7FC61F97742E72248EE5AE6AE5360D1AE6A5F54F373FA543B6A;
            assert CryptoKey recovered := unwrapper.unwrap(wrapped, "recovered", "HmacSHA1");
            assert unwrapper.wrap(recovered) == wrapped;
        }

        @Test
        void shouldRoundTripWithAllWrappingKeySizes() {
            for (Int keySize : [16, 24, 32]) {
                PrivateKey wrappingKey = newKey("kek", "AES", keySize);

                KeyUnwrapper kw = unwrapperFor("AES/KW/NoPadding", wrappingKey);
                WrappedKey wrappedAes = kw.wrap(newKey("aes", "AES", 16, 3));
                assert CryptoKey aes := kw.unwrap(wrappedAes, "aes-restored", "AES");
                assert kw.wrap(aes) == wrappedAes;

                KeyUnwrapper kwp = unwrapperFor("AES/KWP/NoPadding", wrappingKey);
                WrappedKey wrappedHmac = kwp.wrap(newKey("hmac", "HmacSHA256", 13, 7));
                assert CryptoKey hmac := kwp.unwrap(
                    wrappedHmac, "hmac-restored", "HmacSHA256");
                assert kwp.wrap(hmac) == wrappedHmac;
            }
        }

        @Test
        void shouldSupportOpaqueWrappingAndTargetKeys() {
            assert KeyGenerator aesGenerator := algorithms.keyGeneratorFor("AES");
            assert KeyGenerator hmacGenerator := algorithms.keyGeneratorFor("HmacSHA256");
            CryptoKey opaqueWrappingKey = aesGenerator.generateSecretKey("opaque-kek");
            CryptoKey opaqueTargetKey   = hmacGenerator.generateSecretKey("opaque-target");
            KeyUnwrapper unwrapper = unwrapperFor(
                "AES/KWP/NoPadding", opaqueWrappingKey);

            WrappedKey wrapped = unwrapper.wrap(opaqueTargetKey);

            assert CryptoKey recovered := unwrapper.unwrap(
                wrapped, "recovered-target", "HmacSHA256");
            assert !recovered.isVisible();
            assert unwrapper.wrap(recovered) == wrapped;
        }

        @Test
        void shouldProvideSeparateWrapperCapability() {
            PrivateKey wrappingKey = newKey("kek", "AES", 16);
            assert KeyWrapper wrapper :=
                algorithms.keyWrapperFor("AES/KW/NoPadding", wrappingKey);

            WrappedKey wrapped = wrapper.wrap(newKey("target", "AES", 16));

            assert wrapped.algorithm == "AES/KW/NoPadding";
            assert wrapped.bytes.size == 24;
        }

        @Test
        void shouldWrapVisibleKeyWithoutSecretKeyFactory() {
            KeyUnwrapper unwrapper = unwrapperFor(
                "AES/KW/NoPadding", newKey("kek", "AES", 16));
            PrivateKey key = newKey("target", "ChaCha20", 32);

            WrappedKey wrapped = unwrapper.wrap(key);

            assert CryptoKey recovered := unwrapper.unwrap(wrapped, "recovered", "ChaCha20");
            assert unwrapper.wrap(recovered) == wrapped;
        }

        @Test
        void shouldRejectTamperingWrongKeysAndMalformedEnvelopes() {
            KeyUnwrapper unwrapper = unwrapperFor(
                "AES/KW/NoPadding", newKey("kek", "AES", 16));
            WrappedKey wrapped = unwrapper.wrap(newKey("target", "AES", 16));
            Byte[] changed = copy(wrapped.bytes);
            changed[0] ^= 1;

            assert !unwrapper.unwrap(
                new WrappedKey(wrapped.algorithm, changed), "target", "AES");

            KeyUnwrapper wrongKey = unwrapperFor(
                "AES/KW/NoPadding", newKey("other-kek", "AES", 16, 1));
            assert !wrongKey.unwrap(wrapped, "target", "AES");
            assert !unwrapper.unwrap(
                new WrappedKey("AES/KWP/NoPadding", wrapped.bytes), "target", "AES");
            assert !unwrapper.unwrap(
                new WrappedKey(wrapped.algorithm, #0001), "target", "AES");
            assert !unwrapper.unwrap(wrapped, "target", "");
        }

        @Test
        void shouldRejectUnsupportedKeysAndKwPayloadSizes() {
            assert !algorithms.keyUnwrapperFor(
                "AES/KW/NoPadding", newKey("wrong-size", "AES", 15));
            assert !algorithms.keyUnwrapperFor(
                "AES/KW/NoPadding", newKey("wrong-algorithm", "HmacSHA256", 16));
            assert !algorithms.keyUnwrapperFor(
                "AES/KW/NoPadding", new PublicKey(
                    "public", "AES", 16, #000102030405060708090A0B0C0D0E0F));

            KeyUnwrapper unwrapper = unwrapperFor(
                "AES/KW/NoPadding", newKey("kek", "AES", 16));
            assertThrows(IllegalArgument,
                () -> unwrapper.wrap(newKey("unaligned", "HmacSHA256", 13)));
            assertThrows(IllegalArgument,
                () -> unwrapper.wrap(new PublicKey(
                    "public", "AES", 16, #000102030405060708090A0B0C0D0E0F)));

            KeyUnwrapper padded = unwrapperFor(
                "AES/KWP/NoPadding", newKey("kek", "AES", 16));
            assertThrows(IllegalArgument,
                () -> padded.wrap(newKey("empty", "HmacSHA256", 0)));
        }

        private KeyUnwrapper unwrapperFor(String algorithm, CryptoKey key) {
            return algorithms.keyUnwrapperFor(algorithm, key)
                    ?: assert as $"{algorithm} unavailable";
        }

        private PrivateKey newKey(
                String name, String algorithm, Int size, Int offset = 0) {
            return new PrivateKey(
                name,
                algorithm,
                size,
                new Byte[size](i -> (i + offset).toByte())
            );
        }

        private Byte[] copy(Byte[] bytes) {
            return new Byte[bytes.size](i -> bytes[i]);
        }
    }
}
