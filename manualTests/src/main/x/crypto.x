module TestCrypto {
    @Inject Console console;

    package convert import convert.xtclang.org;
    package crypto import crypto.xtclang.org;

    import crypto.*;

    void run(String[] args = ["password"]) {
        @Inject Directory tmpDir;
        File store = tmpDir.fileFor("test.p12");

        String password = args[0];
        String pairName = "test_pair";
        String dName    = CertificateManager.distinguishedName("xqiz.it", orgUnit="Crypto Test");
        String symName  = "test_sym";
        String pwdName  = "test_pass";

        @Inject CertificateManager manager;
        manager.createCertificate(store, password, pairName, dName);
        manager.createSymmetricKey(store, password, symName);
        manager.createPassword(store, password, pwdName, "super-secret");

        KeyStore keystore = manager.keystoreFor(store, password);

        assert CryptoKey keyPair := keystore.getKey(pairName);
        assert CryptoKey symKey  := keystore.getKey(symName);

        @Inject Algorithms algorithms;
        @Inject Random random;

        testDecryptor(algorithms, "RSA", keyPair, SMALL_TEXT);
        testDecryptor(algorithms, "RSA/ECB/PKCS1Padding", keyPair, SMALL_TEXT);

        testDecryptor(algorithms, "AES", symKey, BIG_TEXT);

        assert KeyGenerator desKeyGen := algorithms.keyGeneratorFor("DES");
        CryptoKey tempKey = desKeyGen.generateSecretKey("test-des-key");
        testDecryptor(algorithms, "DES", tempKey, BIG_TEXT);
        testDecryptor(algorithms, "DES/ECB/PKCS5Padding", tempKey, BIG_TEXT);

        testHasher(algorithms, "MD5",         BIG_TEXT);
        testHasher(algorithms, "SHA-1",       BIG_TEXT);
        testHasher(algorithms, "SHA-256",     BIG_TEXT);
        testHasher(algorithms, "SHA-512",     BIG_TEXT);
        testHasher(algorithms, "SHA-512-256", BIG_TEXT);

        testSigner(algorithms, "SHA1withRSA"  , keyPair, BIG_TEXT);
        testSigner(algorithms, "SHA256withRSA", keyPair, BIG_TEXT);

        // manual key creation
        assert keyPair.is(KeyPair);
        CryptoKey publicKey  = keyPair.publicKey;
        CryptoKey privateKey = keyPair.privateKey;

        assert KeyGenerator macKeyGen := algorithms.keyGeneratorFor("HmacSHA256");
        CryptoKey macKey = macKeyGen.generateSecretKey("test-mac-key");
        testSigner(algorithms, "HmacSHA256", macKey, BIG_TEXT);
        testSigner(algorithms, "HmacSHA512", macKey, BIG_TEXT);
        testRawHmac(algorithms);

        PrivateKey privateKeyM = new PrivateKey("test-copy", "DES", 8, random.bytes(8));
        testDecryptor(algorithms, "DES", privateKeyM, BIG_TEXT);

        if (False) {
            import convert.formats.Base64Format;
            Byte[] bytes = manager.extractKey(store, password, pairName);
            console.print("-----BEGIN PRIVATE KEY-----");
            console.print(Base64Format.Instance.encode(bytes, pad=True, lineLength=64));
            console.print("-----END PRIVATE KEY-----");
        }

        assert Certificate cert := keystore.getCertificate(pairName);
        console.print(cert);

        assert CryptoPassword pwd := keystore.getPassword(pwdName);
        console.print($"{pwd}; type={&pwd.type}");

        store.delete();
    }

    void testDecryptor(Algorithms algorithms, String name, CryptoKey key, String text) {
        if (Decryptor decryptor := algorithms.decryptorFor(name, key)) {
            console.print($"*** {decryptor} for {key}");

            Byte[] bytes = decryptor.encrypt(text.utf8());
            console.print($"{bytes.size} bytes: {bytes.slice(0..<16).toHexDump(16)}");

            Byte[] data = decryptor.decrypt(bytes);
            assert data.unpackUtf8() == text;
        } else {
            console.print($"Cannot find decryptor for {name.quoted()} with {key}");
        }
    }

    void testHasher(Algorithms algorithms, String name, String text) {
        if (Signer hasher := algorithms.hasherFor(name)) {
            console.print($"*** {hasher}");

            Signature hash = hasher.sign(text.utf8());
            console.print(hash.bytes.toHexDump());

            assert hasher.verify(hash, text.utf8());
        } else {
            throw new Exception($"Cannot find the hasher for {name.quoted()}");
        }
    }

    void testSigner(Algorithms algorithms, String name, CryptoKey key, String text) {
        if (Signer signer := algorithms.signerFor(name, key)) {
            console.print($"*** {signer}");

            Signature sig = signer.sign(text.utf8());
            console.print(sig.bytes.toHexDump());

            assert signer.verify(sig, text.utf8());
        } else {
            throw new Exception($|Cannot find the signer for "name" or the key form or size is not \
                                 |acceptable
                               );
        }
    }

    void testRawHmac(Algorithms algorithms) {
        Byte[] secret = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef".utf8();
        Byte[] data   = "hmac test".utf8();
        for (String name : ["HmacSHA1", "HmacSHA256", "HmacSHA512"]) {
            PrivateKey key = new PrivateKey("test-raw-key", name, secret.size, secret);
            assert Signer signer := algorithms.signerFor(name, key);
            assert Verifier verifier := algorithms.verifierFor(name, key);
            Signature signature = signer.sign(data);
            assert signer.verify(signature, data);
            assert verifier.verify(signature, data);
            assert !verifier.verify(signature, "changed data".utf8());

            PrivateKey empty = new PrivateKey("test-empty-key", name, 0, []);
            assert !algorithms.signerFor(name, empty);
            assert !algorithms.verifierFor(name, empty);
        }

        // RFC 4231 test case 6 exercises a key larger than the SHA-256 block size
        // https://www.rfc-editor.org/rfc/rfc4231#section-4.7
        Byte[] longSecret = new Byte[131](_ -> 0xAA).freeze(inPlace=True);
        Byte[] longData   = "Test Using Larger Than Block-Size Key - Hash Key First".utf8();

        // HMAC uses the hash of a key that exceeds the hash function's block size
        assert Signer sha256 := algorithms.hasherFor("SHA-256");
        Byte[] digest = sha256.sign(longSecret).bytes;
        PrivateKey hashedKey = new PrivateKey("test-hashed-key", "HmacSHA256", digest.size, digest);
        assert Signer reference := algorithms.signerFor("HmacSHA256", hashedKey);
        Byte[] expected = reference.sign(longData).bytes;

        PrivateKey key = new PrivateKey("test-long-key", "HmacSHA256", longSecret.size, longSecret);
        assert Signer signer := algorithms.signerFor("HmacSHA256", key);
        assert signer.sign(longData).bytes == expected;
        assert signer.verify(expected, longData);
    }

    static String SMALL_TEXT =
            \|The RSA encryption is meant to be used only for small data chunks; \
             |primary use - encrypting a symmetric key to send over an insecure channel
             ;

    static String BIG_TEXT = $./crypto.x;
}
