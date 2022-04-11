/**
 * A configured collection of curated crypto algorithms, alliteratively speaking.
 */
const Algorithms
    {
    construct(Algorithm[] algorithms)
        {
        Map<String, Algorithm> byName = new ListMap();
        for (Algorithm algorithm : algorithms)
            {
            assert byName.putIfAbsent(algorithm.name, algorithm)
                    as $"Duplicate Algorithm: {algorithm.name.quoted()}";
            }

        this.byName = byName;
        }

    /**
     * The [Algorithm] objects managed by this object, keyed by name.
     */
    Map<String, Algorithm> byName;

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
    conditional Signer hasherFor(Specifier specifier)
        {
        if (Algorithm algorithm := findAlgorithm(specifier, Signing, Null, False))
            {
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
    conditional Verifier verifierFor(Specifier specifier, Key key)
        {
        if (Algorithm algorithm := findAlgorithm(specifier, Signing, key, False))
            {
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
    conditional Signer signerFor(Specifier specifier, Key key)
        {
        if (Algorithm algorithm := findAlgorithm(specifier, Signing, key, True))
            {
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
    conditional Encryptor encryptorFor(Specifier specifier, Key key)
        {
        if (Algorithm algorithm := findAlgorithm(specifier, Encryption, key, False))
            {
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
    conditional Decryptor decryptorFor(Specifier specifier, Key key)
        {
        if (Algorithm algorithm := findAlgorithm(specifier, Encryption, key, True))
            {
            return True, algorithm.allocate(key).as(Decryptor);
            }

        return False;
        }


    // ----- internal ------------------------------------------------------------------------------

    /**
     * Find the specified algorithm, and verify that the passed key can be used with the specified
     * algorithm.
     *
     * @param specifier        the algorithm name, or the [Algorithm] object itself
     * @param category
     * @param key              the key
     * @param privateRequired  `True` iff a private or symmetric key is required
     *
     * @return `True` iff the passed key is valid for the combination of the `algorithm` and the
     *         `privateRequired`
     * @return (conditional) the algorithm
     */
    protected conditional Algorithm findAlgorithm(Specifier          specifier,
                                                  Algorithm.Category category,
                                                  Key?               key,
                                                  Boolean            privateRequired)
        {
        String name = specifier.is(String) ? specifier : specifier.name;
        if (Algorithm algorithm := byName.get(name), algorithm.category == category)
            {
            if ((Algorithm.KeyType keyType, Int size, Int publicSize) := algorithm.keyRequired())
                {
                return key != Null && (key.size == size || !privateRequired && key.size == publicSize)
                        ? (True, algorithm)
                        : False;
                }
            else
                {
                return key == Null
                        ? (True, algorithm)
                        : False;
                }
            }

        return False;
        }
    }