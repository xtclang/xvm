import libcrypto.Algorithm;
import libcrypto.Algorithm.Category;
import libcrypto.Algorithms;
import libcrypto.KeyForm;

/**
 * Helper service for native functionality.
 */
service RTAlgorithms
    {
    typedef Int|Int[] as KeySize;

    enum AlgorithmMethod {Hasher, SymmetricCipher, AsymmetricCipher, Signature, KeyGen}

    /**
     * Create the Algorithms object based on the supported algorithm information.
     *
     * @see [Java Security Standard Algorithm Names](https://docs.oracle.com/javase/9/docs/specs/security/standard-names.html)
     */
    Algorithms createAlgorithms()
        {
        Algorithm[] algorithms = new Array<Algorithm>
                (hashers.size + encryptions.size + signers.size + keyGenerators.size);

        for ((String name, Int hashSize) : hashers)
            {
            (_, Object implementation) = getAlgorithmInfo(name, Hasher);

            Algorithm alg = new RTHasher(name, hashSize, implementation);
            algorithms.add(&alg.maskAs(Algorithm));
            }

        for ((AlgorithmMethod method, String name, KeySize keySize) : encryptions)
            {
            (Int blockSize, Object implementation) = getAlgorithmInfo(name, method);

            KeyForm   keyForm = method == SymmetricCipher ? Secret : Pair;
            Algorithm alg     = new RTEncryptionAlgorithm(name, blockSize, keySize, keyForm, implementation);
            algorithms.add(&alg.maskAs(Algorithm));
            }

        for ((String name, KeySize keySize, Int sigSize) : signers)
            {
            (Int blockSize, Object implementation) = getAlgorithmInfo(name, Signature);

            Algorithm alg = new RTSigningAlgorithm(name, blockSize, keySize, sigSize, implementation);
            algorithms.add(&alg.maskAs(Algorithm));
            }

        for ((String name, Int seedSize) : keyGenerators)
            {
            (_, Object implementation) = getAlgorithmInfo(name, KeyGen);

            Algorithm alg = new RTKeyGenerator(name, seedSize, implementation);
            algorithms.add(&alg.maskAs(Algorithm));
            }

        return new Algorithms(algorithms.freeze(True));
        }


    // ----- native methods ------------------------------------------------------------------------

    (Int blockSize, Int formId, Int keySize, Object implementation)
        getAlgorithmInfo(String name, AlgorithmMethod method) {TODO("Native");}


    // ----- constants -----------------------------------------------------------------------------

    /**
     * Supported hasher algorithms (name, hash size).
     */
    static Tuple<String, Int>[] hashers =
        [
        ("MD5"    , 128 >> 3),
        ("SHA-1"  , 160 >> 3),
        ("SHA-256", 256 >> 3),
        ("SHA-512", 512 >> 3),
        ];

    /**
     * Supported encryption algorithms (name, key sizes).
     */
    static Tuple<AlgorithmMethod, String, KeySize>[] encryptions =
        [
        // Symmetrical key ciphers require a symmetrical secret key (not private)
        // (we don't include "NoPadding", since it requires that data size to be a multiple of 8)

        // Advanced Encryption Standard as specified by NIST in FIPS 197.
        // AES is a 128-bit block cipher supporting keys of 128, 192, and 256 bits.
        (SymmetricCipher, "AES"                 , AES_SIZES),
        (SymmetricCipher, "AES/CBC/PKCS5Padding", AES_SIZES),
        (SymmetricCipher, "AES/ECB/PKCS5Padding", AES_SIZES),

        // The Digital Encryption Standard.
        // Despite the doc claim of the key size of 56 bits, the Java implementation
        // requires 64 (DESKeySpec.DES_KEY_LEN = 8)
        (SymmetricCipher, "DES"                 , 8),
        (SymmetricCipher, "DES/CBC/PKCS5Padding", 8),
        (SymmetricCipher, "DES/ECB/PKCS5Padding", 8),

        // Triple DES Encryption.
        // Despite the doc claim of the key size of 168 bits, the Java implementation
        // requires 192 (DESedeKeySpec.DES_EDE_KEY_LEN = 24)
        (SymmetricCipher, "DESede"                 , 24),
        (SymmetricCipher, "DESede/CBC/PKCS5Padding", 24),
        (SymmetricCipher, "DESede/ECB/PKCS5Padding", 24),

        // Asymmetrical key ciphers require a key pair and used only for small chunks of data,
        // such as shared secret key
        (AsymmetricCipher, "RSA"                 , RSA_SIZES),
        (AsymmetricCipher, "RSA/ECB/PKCS1Padding", RSA_SIZES),
        ];

    /**
     * Supported signing algorithms (name, key sizes, signature size).
     */
    static Tuple<String, KeySize, Int>[] signers =
        [
        ("SHA1withDSA"  , 1024 >> 3, 160 >> 3),
        ("SHA256withDSA", 1024 >> 3, 256 >> 3),
        ("SHA1withRSA"  , RSA_SIZES, 160 >> 3),
        ("SHA256withRSA", RSA_SIZES, 256 >> 3),
        ];

    /**
     * Supported key generation algorithms (name, seed size).
     */
    static Tuple<String, Int>[] keyGenerators =
        [
        ("DES"   , 8),
        ("DESede", 24),
        ];

    static Int[] AES_SIZES = [128 >> 3, 192 >> 3, 256 >> 3];
    static Int[] RSA_SIZES = [1024 >> 3, 2048 >> 3, 4096 >> 3];
    }