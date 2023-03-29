/**
 * This test assumes that the keystore (test_store.p12) contains two keys:
 *  - a public/private pair created by the command like:
 *
 *    keytool -genkeypair -alias test_pair -keyalg RSA -keysize 2048 -validity 365
 *            -dname "cn=xqiz.it, ou=manual_test"\
 *            -keystore src/main/x/resources/test_store.p12 -storepass password -storetype PKCS12\
 *
 *  - a symmetrical key created by the command like:
 *
 *    keytool -genseckey -alias test_sym -keyalg AES -keysize 256\
 *            -keystore src/main/x/resources/test_store.p12 -storepass password
 *
 */
module TestCrypto
    {
    @Inject Console console;

    package crypto import crypto.xtclang.org;

    import crypto.*;

    void run(String[] args = ["password"])
        {
        File   store    = File:./resources/test_store.p12;
        String pairName = "test_pair";
        String symName  = "test_sym";

        @Inject(opts=new KeyStore.Info(store.contents, args[0])) KeyStore keystore;

        assert CryptoKey   privateKey := keystore.getKey(pairName);
        assert Certificate cert       := keystore.getCertificate(pairName);
        assert CryptoKey   publicKey  := cert.containsKey();
        assert CryptoKey   symKey     := keystore.getKey(symName);

        KeyPair keyPair = new KeyPair(pairName, publicKey, privateKey);

        @Inject Algorithms algorithms;
        @Inject Random random;

        testDecryptor(algorithms, "RSA", keyPair, SMALL_TEXT);
        testDecryptor(algorithms, "RSA/ECB/PKCS1Padding", keyPair, SMALL_TEXT);

        testDecryptor(algorithms, "AES", symKey, BIG_TEXT);

        assert KeyGenerator desKeyGen := algorithms.keyGeneratorFor("DES");
        CryptoKey tempKey = desKeyGen.generateSecretKey("test-temp");
        testDecryptor(algorithms, "DES", tempKey, BIG_TEXT);
        testDecryptor(algorithms, "DES/ECB/PKCS5Padding", tempKey, BIG_TEXT);

        testHasher(algorithms, "MD5",     BIG_TEXT);
        testHasher(algorithms, "SHA-1",   BIG_TEXT);
        testHasher(algorithms, "SHA-256", BIG_TEXT);
        testHasher(algorithms, "SHA-512", BIG_TEXT);

        testSigner(algorithms, "SHA1withRSA"  , keyPair, BIG_TEXT);
        testSigner(algorithms, "SHA256withRSA", keyPair, BIG_TEXT);

        // manual key creation
        assert Byte[] publicBytes := publicKey.isVisible();
        PublicKey publicKeyM = new PublicKey("test-copy", "RSA", publicKey.size, publicBytes);
        KeyPair keyPairM = new KeyPair("test-copy", publicKeyM, privateKey);
        testDecryptor(algorithms, "RSA", keyPairM, SMALL_TEXT);

        PrivateKey privateKeyM = new PrivateKey("test-copy", "DES", 8, random.fill(new Byte[8]));
        testDecryptor(algorithms, "DES", privateKeyM, BIG_TEXT);
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
            console.print($"Cannot find hasher for {name.quoted()}");
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