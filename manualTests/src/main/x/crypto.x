module TestCrypto
    {
    @Inject Console console;

    package crypto import crypto.xtclang.org;

    import crypto.*;

    void run(String[] args = ["password"])
        {
        File   store   = File:./resources/test_store.p12;
        String keyName = "test";

        @Inject(opts=new KeyStore.Info(store.contents, args[0])) KeyStore keystore;

        assert CryptoKey   privateKey := keystore.getKey(keyName);
        assert Certificate cert       := keystore.getCertificate(keyName);
        assert CryptoKey   publicKey  := cert.containsKey();

        KeyPair keyPair = new KeyPair("test", publicKey, privateKey);

        @Inject Algorithms algorithms;

        testDecryptor(algorithms, "RSA", keyPair, SMALL_TEXT);
        testDecryptor(algorithms, "RSA/ECB/PKCS1Padding", keyPair, SMALL_TEXT);

        assert KeyGenerator desKeyGen := algorithms.keyGeneratorFor("DES");
        CryptoKey desKey = desKeyGen.generateSecretKey("hello");
        testDecryptor(algorithms, "DES", desKey, BIG_TEXT);
        testDecryptor(algorithms, "DES/ECB/PKCS5Padding", desKey, BIG_TEXT);

        testHasher(algorithms, "MD5",     BIG_TEXT);
        testHasher(algorithms, "SHA-1",   BIG_TEXT);
        testHasher(algorithms, "SHA-256", BIG_TEXT);

        testSigner(algorithms, "SHA1withRSA"  , keyPair, BIG_TEXT);
        testSigner(algorithms, "SHA256withRSA", keyPair, BIG_TEXT);
        }

    void testDecryptor(Algorithms algorithms, String name, CryptoKey key, String text)
        {
        if (Decryptor decryptor := algorithms.decryptorFor(name, key))
            {
            console.print($"*** {decryptor} for {key}");

            Byte[] bytes = decryptor.encrypt(text.utf8());
            console.print($"{bytes.size} bytes: {bytes.slice(0..<16).toHexDump(16)}");

            Byte[] data = decryptor.decrypt(bytes);
            assert data.unpackUtf8() == text;
            }
        else
            {
            console.print($"Cannot find decryptor for {name.quoted()} with {key}");
            }
        }

    void testHasher(Algorithms algorithms, String name, String text)
        {
        if (Signer hasher := algorithms.hasherFor(name))
            {
            console.print($"*** {hasher}");

            Signature hash = hasher.sign(text.utf8());
            console.print(hash.bytes.toHexDump());

            assert hasher.verify(hash, text.utf8());
            }
        else
            {
            console.print($"Cannot find signer for {name.quoted()}");
            }
        }

    void testSigner(Algorithms algorithms, String name, CryptoKey key, String text)
        {
        if (Signer signer := algorithms.signerFor(name, key))
            {
            console.print($"*** {signer}");

            Signature sig = signer.sign(text.utf8());
            console.print(sig.bytes.toHexDump());

            assert signer.verify(sig, text.utf8());
            }
        else
            {
            console.print($"Cannot find signer for {name.quoted()}");
            }
        }

    static String SMALL_TEXT =
            \|The RSA encryption is meant to be used only for small data chunks; \
             |primary use - a symmetric key (less than 256 bytes)
             ;

    static String BIG_TEXT = $./crypto.x;
    }