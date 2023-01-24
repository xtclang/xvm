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

        assert CryptoKey privateKey := keystore.getKey("hello");
        console.print($"privateKey={privateKey}");

        assert Certificate cert := keystore.getCertificate("hello");
        assert CryptoKey publicKey := cert.containsKey();

        @Inject Algorithms algorithms;

        testDecryptor(algorithms, "RSA/ECB/PKCS1Padding", new KeyPair("hello", publicKey, privateKey));
        }

    void testDecryptor(Algorithms algorithms, String name, CryptoKey key)
        {
        assert Decryptor decryptor := algorithms.decryptorFor(name, key);
        console.print($"*** decryptor={decryptor}");

        String text = \|The RSA encryption is meant to be used only for small data chunks; \
                       |primary use - a symmetric key (less than 256 bytes)
                       ;

        Byte[] bytes = decryptor.encrypt(text.utf8());
        console.print(bytes.toHexDump());

        Byte[] data = decryptor.decrypt(bytes);
        assert data.unpackUtf8() == text;
        console.print(text);
        }

    void testSigner(Algorithms algorithms, String name, CryptoKey key)
        {
        assert Signer signer := algorithms.signerFor(name, key);
        console.print($"*** signerr={signer}");

        String text = $./TestSimple.x;
        Signature sig = signer.sign(text.utf8());
        console.print(sig.bytes.toHexDump());
        }
    }