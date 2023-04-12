import libcrypto.Algorithms;
import libcrypto.Algorithm;
import libcrypto.Algorithm.Category;
import libcrypto.CryptoKey;
import libcrypto.Decryptor;
import libcrypto.Encryptor;
import libcrypto.KeyForm;
import libcrypto.KeyPair;

/**
 * The native [Encryption] [Algorithm] implementation that requires a key.
 */
service RTEncryptionAlgorithm(String name, Int blockSize)
        implements Algorithm
    {
    construct(String name, Int blockSize, Int|Int[] keySize, KeyForm keyForm, Object cipher)
        {
        this.name      = name;
        this.blockSize = blockSize;
        this.keySize   = keySize;
        this.keyForm   = keyForm;
        this.cipher    = cipher;
        }

    /**
     * The supported key size(s) for this algorithm.
     */
    private Int|Int[] keySize;

    /**
     * The KeyForm (Secret for symmetrical, Pair for asymmetrical).
     */
    private KeyForm keyForm;

    /**
     * The native cipher.
     */
    private Object cipher;


    // ----- Algorithm API -------------------------------------------------------------------------

    @Override
    Category category.get()
        {
        return Encryption;
        }

    @Override
    conditional Int|Int[] keyRequired()
        {
        return True, keySize;
        }

    @Override
    Encryptor|Decryptor allocate(CryptoKey? key)
        {
        if (key == Null)
            {
            throw new IllegalArgument("Key is required");
            }

        switch (key.form)
            {
            case Public:
                // asymmetrical encryptor
                assert keyForm == Pair as $"Invalid key form for {this}";
                assert Algorithms.validSize(keySize, key.size) as $"Invalid key size for {this}";
                Encryptor encryptor = new RTEncryptor(name, blockSize, key, Null, cipher);
                return &encryptor.maskAs(Encryptor);

            case Pair:
                // asymmetrical decryptor
                assert keyForm == Pair as $"Invalid key form for {this}";
                assert key.is(KeyPair) as "Key must be a KeyPair";
                assert Algorithms.validSize(keySize, key.privateKey.size) as $"Invalid key size for {this}";

                Decryptor decryptor = new RTDecryptor(name, blockSize, key.publicKey, key.privateKey, cipher);
                return &decryptor.maskAs(Decryptor);

            case Secret:
                // symmetrical decryptor
                assert keyForm == Secret as $"Invalid key form for {this}";
                assert Algorithms.validSize(keySize, key.size) as $"Invalid key size for {this}";
                Decryptor decryptor = new RTDecryptor(name, blockSize, Null, key, cipher);
                return &decryptor.maskAs(Decryptor);
            }
        }

    @Override
    String toString()
        {
        return $"{name.quoted()} encryption algorithm with {keySize} bytes key";
        }
    }