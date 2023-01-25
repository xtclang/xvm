module TestSimple
    {
    @Inject Console console;

    package crypto import crypto.xtclang.org;

    import crypto.*;
    import crypto.KeyStore.Info;

    void run(String[] args = ["password"])
        {
        @Inject Directory curDir;
        File store = curDir.fileFor("data/hello/https.p12");

        @Inject(opts=new Info(store.contents, args[0])) KeyStore keystore;

        assert CryptoKey   privateKey := keystore.getKey("hello");
        assert Certificate cert       := keystore.getCertificate("hello");
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

    static String BIG_TEXT = $./TestSimple.x;
    }