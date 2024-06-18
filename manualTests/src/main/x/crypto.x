module TestCrypto {
    @Inject Console console;

    package convert import convert.xtclang.org;
    package crypto import crypto.xtclang.org;

    import crypto.*;

    void run(String[] args = ["password"]) {
        @Inject Directory curDir;
        File store = curDir.fileFor("test.p12");

        String password = args[0];
        String pairName = "test_pair";
        String dName    = CertificateManager.distinguishedName("xqiz.it", orgUnit="Crypto Test");
        String symName  = "test_sym";
        String pwdName  = "test_pass";

        @Inject CertificateManager manager;
        manager.createCertificate(store, password, pairName, dName);
        manager.createSymmetricKey(store, password, symName);
        manager.createPassword(store, password, pwdName, "super-secret");

        @Inject(opts=new KeyStore.Info(store.contents, password)) KeyStore keystore;

        assert CryptoKey keyPair := keystore.getKey(pairName);
        assert CryptoKey symKey  := keystore.getKey(symName);

        @Inject Algorithms algorithms;
        @Inject Random random;

        testDecryptor(algorithms, "RSA", keyPair, SMALL_TEXT);
        testDecryptor(algorithms, "RSA/ECB/PKCS1Padding", keyPair, SMALL_TEXT);

        testDecryptor(algorithms, "AES", symKey, BIG_TEXT);

        assert KeyGenerator desKeyGen := algorithms.keyGeneratorFor("DES");
        CryptoKey tempKey = desKeyGen.generateSecretKey("test-temp");
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

        assert Byte[] publicBytes := publicKey.isVisible();
        PublicKey publicKeyM = new PublicKey("test-copy", "RSA", publicKey.size, publicBytes);
        KeyPair keyPairM = new KeyPair("test-copy", publicKeyM, privateKey);
        testDecryptor(algorithms, "RSA", keyPairM, SMALL_TEXT);

        PrivateKey privateKeyM = new PrivateKey("test-copy", "DES", 8, random.bytes(8));
        testDecryptor(algorithms, "DES", privateKeyM, BIG_TEXT);

        if (False) {
            Byte[] bytes = manager.extractKey(store, password, pairName);
            console.print("-----BEGIN PRIVATE KEY-----");
            String sKey = convert.formats.Base64Format.Instance.encode(bytes);
            Int    size = sKey.size;
            for (Int start = 0; start < size; ) {
                Int end = size.minOf(start+64);
                console.print(sKey[start ..< end]);
                start = end;
            }
            console.print("-----END PRIVATE KEY-----");
        }

        assert Certificate cert := keystore.getCertificate(pairName);
        console.print(cert);

        assert CryptoPassword pwd := keystore.getPassword(pwdName);
        console.print($"{pwd}; type={&pwd.actualType}");

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
            console.print($"Cannot find hasher for {name.quoted()}");
        }
    }

    void testSigner(Algorithms algorithms, String name, CryptoKey key, String text) {
        if (Signer signer := algorithms.signerFor(name, key)) {
            console.print($"*** {signer}");

            Signature sig = signer.sign(text.utf8());
            console.print(sig.bytes.toHexDump());

            assert signer.verify(sig, text.utf8());
        } else {
            console.print($"Cannot find signer for {name.quoted()}");
        }
    }

    static String SMALL_TEXT =
            \|The RSA encryption is meant to be used only for small data chunks; \
             |primary use - a symmetric key (less than 256 bytes)
             ;

    static String BIG_TEXT = $./crypto.x;
}