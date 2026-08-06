/**
 * A configured collection of curated crypto algorithms, alliteratively speaking.
 */
const Algorithms {
    construct(Algorithm[] algorithms) {
        Map<String, Algorithm>[] byCategory = new Map[Category.count](_ -> new HashMap());

        for (Algorithm algorithm : algorithms) {
            Map<String, Algorithm> byName = byCategory[algorithm.category.ordinal];

            assert byName.putIfAbsent(algorithm.name, algorithm)
                    as $"Duplicate {algorithm.category} Algorithm: {algorithm.name.quoted()}";
        }

        this.byCategory = byCategory.makeImmutable();
    }

    /**
     * The [Algorithm] objects managed by this object, indexed by Category and keyed by name.
     */
    Map<String, Algorithm>[] byCategory;

    /**
     * Algorithms can be specified either by name, or by an [Algorithm] object obtained from this
     * `Algorithms` object.
     */
    typedef Algorithm|String as Specifier;

    /**
     * Obtain a [Signer] for the keyless hashing algorithm of the specified name.
     *
     * @param specifier  the algorithm name, or the [Algorithm] object itself
     *
     * @return True iff the `Algorithms` is configured with the specified algorithm
     * @return (conditional) the [Verifier] and [Signer] for the specified algorithm
     */
    conditional Signer hasherFor(Specifier specifier) {
        if (Algorithm algorithm := findAlgorithm(specifier, Signing, Null, False)) {
            return True, algorithm.allocate(key=Null).as(Signer);
        }

        return False;
    }

    /**
     * Obtain a [Verifier] for the signature verification algorithm of the specified name.
     *
     * @param specifier  the algorithm name, or the [Algorithm] object itself
     * @param key        the key used to verify the signature
     *
     * @return True iff the `Algorithms` is configured with the specified algorithm, and the key
     *         is acceptable
     * @return (conditional) the signature [Verifier] for the specified algorithm
     */
    conditional Verifier verifierFor(Specifier specifier, CryptoKey key) {
        if (Algorithm algorithm := findAlgorithm(specifier, Signing, key, False)) {
            return True, algorithm.allocate(key).as(Verifier);
        }

        return False;
    }

    /**
     * Obtain a [Signer] for the signing algorithm of the specified name.
     *
     * @param specifier  the algorithm name, or the [Algorithm] object itself
     * @param key        the key used to sign
     *
     * @return True iff the `Algorithms` is configured with the specified algorithm, and the key
     *         is acceptable
     * @return (conditional) the [Verifier] and [Signer] for the specified algorithm
     */
    conditional Signer signerFor(Specifier specifier, CryptoKey key) {
        if (key.is(KeyPair),
                Algorithm algorithm := findAlgorithm(specifier, Signing, key.privateKey, True)) {
            return True, algorithm.allocate(key).as(Signer);
        } else if (key.is(PrivateKey),
                Algorithm algorithm := findAlgorithm(specifier, Signing, key, True)) {
            return True, algorithm.allocate(key).as(Signer);
        }

        return False;
    }

    /**
     * Obtain an [Encryptor] for the encryption algorithm of the specified name.
     *
     * @param specifier  the algorithm name, or the [Algorithm] object itself
     * @param key        the key used to encrypt
     *
     * @return True iff the `Algorithms` is configured with the specified algorithm, and the key
     *         is acceptable
     * @return (conditional) the [Verifier] and [Signer] for the specified algorithm
     */
    conditional Encryptor encryptorFor(Specifier specifier, CryptoKey key) {
        if (Algorithm algorithm := findAlgorithm(specifier, Encryption, key, False)) {
            return True, algorithm.allocate(key).as(Encryptor);
        }

        return False;
    }

    /**
     * Obtain a [Decryptor] for the encryption algorithm of the specified name.
     *
     * @param specifier  the algorithm name, or the [Algorithm] object itself
     * @param key        the key needed to encrypt and decrypt, which is either a symmetric key or
     *                   a public/private key pair
     *
     * @return True iff the `Algorithms` is configured with the specified algorithm, and the key
     *         is acceptable
     * @return (conditional) the [Verifier] and [Signer] for the specified algorithm
     */
    conditional Decryptor decryptorFor(Specifier specifier, CryptoKey key) {
        if (Algorithm algorithm := findAlgorithm(specifier, Encryption, key, True)) {
            return True, algorithm.allocate(key).as(Decryptor);
        }

        return False;
    }

    /**
     * Obtain an [AuthenticatedEncryptor] for the authenticated-encryption algorithm of the
     * specified name.
     *
     * @param specifier  the algorithm name, or the [Algorithm] object itself
     * @param key        the key used to encrypt and authenticate
     *
     * @return `True` iff the `Algorithms` is configured with the specified algorithm, and the key
     *         is acceptable
     * @return (conditional) the [AuthenticatedEncryptor] for the specified algorithm
     */
    conditional AuthenticatedEncryptor authenticatedEncryptorFor(
            Specifier specifier, CryptoKey key) {
        if (Algorithm algorithm := findAlgorithm(specifier, AuthenticatedEncryption, key, False),
                validSecretKeyFor(algorithm, key)) {
            return True, algorithm.allocate(key).as(AuthenticatedEncryptor);
        }

        return False;
    }

    /**
     * Obtain an [AuthenticatedDecryptor] for the authenticated-encryption algorithm of the
     * specified name.
     *
     * @param specifier  the algorithm name, or the [Algorithm] object itself
     * @param key        the private or symmetric key used to authenticate and decrypt
     *
     * @return `True` iff the `Algorithms` is configured with the specified algorithm, and the key
     *         is acceptable
     * @return (conditional) the [AuthenticatedDecryptor] for the specified algorithm
     */
    conditional AuthenticatedDecryptor authenticatedDecryptorFor(
            Specifier specifier, CryptoKey key) {
        if (Algorithm algorithm := findAlgorithm(specifier, AuthenticatedEncryption, key, True),
                validSecretKeyFor(algorithm, key)) {
            return True, algorithm.allocate(key).as(AuthenticatedDecryptor);
        }

        return False;
    }

    /**
     * Obtain a [KeyWrapper] for the specified key-wrapping algorithm.
     *
     * @param specifier  the algorithm name, or the [Algorithm] object itself
     * @param key        the secret key-encryption key used to wrap keys
     *
     * @return `True` iff the algorithm is configured and accepts the key-encryption key
     * @return (conditional) the [KeyWrapper] for the specified algorithm
     */
    conditional KeyWrapper keyWrapperFor(Specifier specifier, CryptoKey key) {
        if (Algorithm algorithm := findAlgorithm(specifier, KeyWrapping, key, False),
                validSecretKeyFor(algorithm, key)) {
            return True, algorithm.allocate(key).as(KeyWrapper);
        }

        return False;
    }

    /**
     * Obtain a [KeyUnwrapper] for the specified key-wrapping algorithm.
     *
     * @param specifier  the algorithm name, or the [Algorithm] object itself
     * @param key        the secret key-encryption key used to unwrap keys
     *
     * @return `True` iff the algorithm is configured and accepts the key-encryption key
     * @return (conditional) the [KeyUnwrapper] for the specified algorithm
     */
    conditional KeyUnwrapper keyUnwrapperFor(Specifier specifier, CryptoKey key) {
        if (Algorithm algorithm := findAlgorithm(specifier, KeyWrapping, key, True),
                validSecretKeyFor(algorithm, key)) {
            return True, algorithm.allocate(key).as(KeyUnwrapper);
        }

        return False;
    }

    /**
     * Obtain a [KeyGenerator] for the key generation algorithm of the specified name.
     *
     * @param specifier  the algorithm name, or the [Algorithm] object itself
     *
     * @return True iff the `Algorithms` is configured with the specified algorithm, and the key
     *         is acceptable
     * @return (conditional) the [KeyGenerator] for the specified algorithm
     */
    conditional KeyGenerator keyGeneratorFor(Specifier specifier) {
        if (Algorithm algorithm := findAlgorithm(specifier, KeyGeneration, Null, True)) {
            return True, algorithm.allocate(Null).as(KeyGenerator);
        }

        return False;
    }

    /**
     * Helper function that checks whether or not the actual size matched the supported size(s).
     *
     * @param  supportedSize  the supported size(s)
     * @param  actualSize     the actual size
     *
     * @return true if the actual size matches one of the supported sizes
     */
    static Boolean validSize(Int|Int[] supportedSize, Int actualSize) {
        return supportedSize.is(Int)
                ? actualSize == supportedSize
                : supportedSize.contains(actualSize);
    }


    // ----- internal ------------------------------------------------------------------------------

    /**
     * Find the specified algorithm, and verify that the passed key can be used with the specified
     * algorithm.
     *
     * @param specifier        the algorithm name, or the [Algorithm] object itself
     * @param category         the category of the algorithm to find
     * @param key              the key (if required) that will be provided to the algorithm
     * @param privateRequired  `True` iff a private or symmetric key is required
     *
     * @return `True` iff the passed key is valid for the combination of the `algorithm` and the
     *         `privateRequired`
     * @return (conditional) the algorithm
     */
    protected conditional Algorithm findAlgorithm(Specifier          specifier,
                                                  Algorithm.Category category,
                                                  CryptoKey?         key,
                                                  Boolean            privateRequired) {
        String                 name =  specifier.is(String) ? specifier : specifier.name;
        Map<String, Algorithm> byName = byCategory[category.ordinal];

        if (Algorithm algorithm := byName.get(name), algorithm.category == category) {
            if (Int|Int[] keySize := algorithm.keyRequired()) {
                return key != Null
                        && (!privateRequired || key.form != Public)
                        && validSize(keySize, key.size)
                        ? (True, algorithm)
                        : False;
            } else {
                return key == Null
                        ? (True, algorithm)
                        : False;
            }
        }
        return False;
    }

    /**
     * Verify the additional key constraints shared by authenticated-encryption algorithms.
     */
    private Boolean validSecretKeyFor(Algorithm algorithm, CryptoKey key) {
        String keyAlgorithm = algorithm.name.split('/')[0];
        return key.form == Secret && key.algorithm == keyAlgorithm;
    }
}
