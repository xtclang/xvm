import libcrypto.Algorithm;
import libcrypto.Algorithms;
import libcrypto.KeyForm;

/**
 * Helper service for native functionality.
 */
service RTAlgorithms
    {
    static Algorithms createAlgorithms(String[] names)
        {
        RTAlgorithms instance   = new RTAlgorithms();
        Algorithm[]  algorithms = new Array<Algorithm>(names.size);
        for (String name : names)
            {
            (Int blockSize, Object cipher) = instance.getAlgorithmInfo(name);
            (KeyForm form, Int|Int[] keySize) = computeKeyInfo(name);

            Algorithm alg = new RTSigningAlgorithm(name, blockSize, form, keySize, cipher);
            algorithms.add(&alg.maskAs(Algorithm));
            }

        return new Algorithms(algorithms.freeze(True));
        }

    /**
     * @see [cipher-algorithm-names](https://docs.oracle.com/javase/9/docs/specs/security/standard-names.html#cipher-algorithm-names)
     */
    static (KeyForm form, Int|Int[] keySize) computeKeyInfo(String name)
        {
        String[] parts = name.split('/');

        String cipherAlgName = parts[0];
        if (cipherAlgName.startsWith("AES"))
            {
            // Quote:
            //   Advanced Encryption Standard as specified by NIST in FIPS 197. AES is a 128-bit
            //   block cipher supporting keys of 128, 192, and 256 bits. To use the AES cipher with
            //   only one valid key size, use the format AES_<n>, where <n> can be 128, 192 or 256.

            Int|Int[] keySize = cipherAlgName.size > 4
                    ? new Int(cipherAlgName[4 ..< cipherAlgName.size])
                    : [128, 192, 256];
            return Secret, keySize;
            }
        else
            {
            switch (cipherAlgName)
                {
                case "DES":
                    // The Digital Encryption Standard.
                    return Secret, 56;

                case "DESede":
                    // Triple DES Encryption
                    return Secret, 168;

                case "RSA":
                    // The RSA encryption algorithm
                    return Secret, [1024, 2048];

                default:
                    throw new IllegalArgument("Unsupported algorithm");
                }
            }
        }


    // ----- native methods ------------------------------------------------------------------------

    (Int blockSize, Int formId, Int keySize, Object cipher)
        getAlgorithmInfo(String name) {TODO("Native");}
    }