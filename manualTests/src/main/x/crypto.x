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

        @Inject Algorithms algorithms;

        KeyPair keyPair = new KeyPair("hello", publicKey, privateKey);
        testDecryptor(algorithms, "RSA/ECB/PKCS1Padding", keyPair, SMALL_TEXT);

        assert KeyGenerator desKeyGen := algorithms.keyGeneratorFor("DES");
        CryptoKey desKey = desKeyGen.generateSecretKey("hello");
        testDecryptor(algorithms, "DES/ECB/PKCS5Padding", desKey, BIG_TEXT);
        }

    void testDecryptor(Algorithms algorithms, String name, CryptoKey key, String text)
        {
        if (Decryptor decryptor := algorithms.decryptorFor(name, key))
            {
            console.print($"*** decryptor={decryptor} for {key}");

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

    void testSigner(Algorithms algorithms, String name, CryptoKey key, String text)
        {
        assert Signer signer := algorithms.signerFor(name, key);
        console.print($"*** signer={signer}");

        Signature sig = signer.sign(text.utf8());
        console.print(sig.bytes.toHexDump());
        }

    static String SMALL_TEXT =
            \|The RSA encryption is meant to be used only for small data chunks; \
             |primary use - a symmetric key (less than 256 bytes)
             ;

    static String BIG_TEXT = $./crypto.x;
    }