import libcrypto.Algorithm;
import libcrypto.Algorithm.Category;
import libcrypto.Algorithms;
import libcrypto.KeyForm;

/**
 * Helper service for native functionality.
 */
service RTAlgorithms
    {
    /**
     * Compute the Algorithms object.
     */
    Algorithms createAlgorithms()
        {
        Algorithm[] algorithms = new Array<Algorithm>(algorithmNames.size);
        for (String name : algorithmNames)
            {
            (AlgorithmMethod method, Int|Int[] keySize, Int sigSize) = computeMethod(name);

            (Int blockSize, Object implementation) = getAlgorithmInfo(name, method);

            Algorithm alg;
            switch (method)
                {
                case Hasher:
                    alg = new RTHasher(name, sigSize, implementation);
                    break;

                case SymmetricCipher:
                    alg = new RTEncryptionAlgorithm(name, blockSize, keySize, Secret, implementation);
                    break;

                case AsymmetricCipher:
                    alg = new RTEncryptionAlgorithm(name, blockSize, keySize, Pair, implementation);
                    break;

                case Signature:
                    alg = new RTSigningAlgorithm(name, blockSize, keySize, sigSize, implementation);
                    break;

                case KeyGen:
                    alg = new RTKeyGenerator(name, sigSize, implementation);
                    break;

                }
            algorithms.add(&alg.maskAs(Algorithm));
            }

        return new Algorithms(algorithms.freeze(True));
        }

    /**
     * Supported algorithm names.
     */
    static String[] algorithmNames =
            [
            // symmetrical key ciphers require a symmetrical secret key (not private)
            // "NoPadding" means that the data size must be a multiple of 8
            "AES/CBC/NoPadding",
            "AES/CBC/PKCS5Padding",
            "AES/ECB/NoPadding",
            "AES/ECB/PKCS5Padding",

            "DES/CBC/NoPadding",
            "DES/CBC/PKCS5Padding",
            "DES/ECB/NoPadding",
            "DES/ECB/PKCS5Padding",

            // asymmetrical key ciphers require a key pair
            "RSA/ECB/PKCS1Padding", // used only for small chunks of data, such as shared secret key

            // key generators
            "DES",
            "DESede",
            ];

    enum AlgorithmMethod {Hasher, SymmetricCipher, AsymmetricCipher, Signature, KeyGen}

    /**
     * @see [Java Security Standard Algorithm Names](https://docs.oracle.com/javase/9/docs/specs/security/standard-names.html)
     */
    static (AlgorithmMethod category, Int|Int[] keySize, Int sigSize) computeMethod(String name)
        {
        String[] parts = name.split('/');

        if (parts.size == 1)
            {
            switch (name)
                {
                case "DES":
                    return KeyGen, 0, 8;

                case "DESede":
                    return KeyGen, 0, 24;

                case "MD5":
                    return Hasher, 0, 128 >> 3;

                case "SHA-1":
                    return Hasher, 0, 160 >> 3;

                case "SHA-256":
                    return Hasher, 0, 256 >> 3;

                case "SHA1withDSA":
                    return Signature, 1024 >> 3, 160 >> 3;

                case "SHA1withRSA":
                    return Signature, RSA_SIZES, 160 >> 3;

                case "SHA256withDSA":
                    return Signature, 1024 >> 3, 256 >> 3;

                case "SHA256withRSA":
                    return Signature, RSA_SIZES, 256 >> 3;
                }
            }
        else
            {
            String cipherAlgName = parts[0];
            if (cipherAlgName.startsWith("AES"))
                {
                // Quote:
                //   Advanced Encryption Standard as specified by NIST in FIPS 197. AES is a 128-bit
                //   block cipher supporting keys of 128, 192, and 256 bits. To use the AES cipher with
                //   only one valid key size, use the format AES_<n>, where <n> can be 128, 192 or 256.

                return SymmetricCipher, (cipherAlgName.size > 4
                        ? new Int(cipherAlgName[4 ..< cipherAlgName.size])
                        : [128 >> 3, 192 >> 3, 256 >> 3]), 0;
                }

            switch (cipherAlgName)
                {
                case "DES":
                    // The Digital Encryption Standard.
                    // Despite the doc claim of the key size of 56 bits, the Java implementation
                    // requires 64 (DESKeySpec.DES_KEY_LEN = 8)
                    return SymmetricCipher, 8, 0;

                case "DESede":
                    // Triple DES Encryption.
                    // Despite the doc claim of the key size of 168 bits, the Java implementation
                    // requires 192 (DESedeKeySpec.DES_EDE_KEY_LEN = 24)
                    return SymmetricCipher, 24, 0;

                case "RSA":
                    // The RSA encryption algorithm.
                    return AsymmetricCipher, RSA_SIZES, 0;
                }
            }

        throw new IllegalArgument($"Unsupported algorithm {name.quoted()}");
        }


    // ----- native methods ------------------------------------------------------------------------

    (Int blockSize, Int formId, Int keySize, Object implementation)
        getAlgorithmInfo(String name, AlgorithmMethod method) {TODO("Native");}


    // ----- constants -----------------------------------------------------------------------------

    private static Int[] RSA_SIZES = [1024 >> 3, 2048 >> 3, 4096 >> 3];
    }