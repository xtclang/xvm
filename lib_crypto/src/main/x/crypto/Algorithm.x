/**
 * A representation of a crypto algorithm.
 *
 * An `Algorithm` is used to create a cryptographic engine using cryptographic material:
 *
 * * A _message digest algorithm_ uses a cryptographic key to create [Signature]s from input data.
 *   Using either a symmetric key or a private key, the `Algorithm` can produce a [Signer] object
 *   (which is also a [Verifier] object). Using a public key, the `Algorithm` can only produce a
 *   [Verifier] object. An example algorithm is the `AEAD` algorithm used by TLS 1.3.
 *
 * * A _hashing algorithm_ is a simpler form that requires no cryptographic material to produce
 *   [Signature]s, so it can always create a [Signer] object (which is also a [Verifier] object);
 *   an example is the well-known `CRC32` algorithm.
 *
 * * An _encryption algorithm_ uses a cryptographic key to _encrypt_ data, and a cryptographic key
 *   to _decrypt_ data. If the same key is used for both encryption and decryption, then the
 *   algorithm is a form of _symmetric key encryption_. If a key for encrypting data can be safely
 *   shared with anyone, that is called _public key encryption_. Another key, which must not be
 *   shared, is used to decrypt that encrypted data; it is called a _private key_, or a _key pair_.
 *   Using either a symmetric key or a private key, the `Algorithm` can produce a [Decryptor]
 *   object, (which is also an [Encryptor] object). Using a public key, the `Algorithm` can only
 *   produce an [Encryptor] object. An example of an encryption algorithm is the `ChaCha20-Poly1305`
 *   algorithm used by TLS 1.3 (and no, we did not just make that name up).
 */
interface Algorithm
    {
    /**
     * The name of the algorithm. The algorithm names are expected to be both unique and obvious (at
     * least to those acquainted with the dark arts of cryptography).
     */
    @RO String name;

    /**
     * The category of the algorithm:
     *
     * * `Signing` algorithms produce a "hash", "signature", or "message digest" output from an
     *   input "message", and can later use that digest to verify that the data used as an input
     *   has not been modified. There are three sub-categories supported by this module:
     *
     * * * a _hashing_ implementation uses only the information in the input to produce the digest;
     *     the classical example is a Cyclical Redundancy Check (CRC) algorithm, such as "CRC32".
     *     As a result, these algorithms do not require any key(s).
     *
     * * * a [Signer] uses either a `Symmetric` key or a public/private key `Pair` to produce a
     *     signature based on the information in the input. A `Signer` is also naturally able to
     *     act as a `Verifier`.
     *
     * * * a [Verifier] uses a key (of any `KeyType`) to verify a signature based on the information
     *     in the input.
     *
     * * `Encryption` algorithms support the transformation of an input message to an encrypted
     *   form, and the decryption of that encrypted form back to the original message content.
     *
     * * * an [Encryptor] uses a key (of any `KeyType`) to encrypt the information in the input.
     *
     * * * an [Decryptor] uses either a `Symmetric` key or a public/private key `Pair` to decrypt
     *     the information in the input.
     */
    enum Category {Signing, Encryption}

    /**
     * The category of the cryptographic algorithm.
     */
    @RO Category category;

    /**
     * The size of a block of data that the algorithm operates on; if the algorithm is not block-
     * oriented, then the `blockSize` will be `1` (i.e. byte-at-a-time).
     */
    @RO Int blockSize;

    /**
     * For a `Signing` algorithm, this is the size in bytes of the resulting signature. (A signature
     * is also called a "_hash_" or a "_message digest_".)
     */
    @RO Int signatureSize;

    /**
     * Describes the type of key that an algorithm requires:
     *
     * * `Symmetric` - a shared key, which needs to be protected like a "private key";
     * * `Public` - a public key only, with the private key portion of the "public/private key pair"
     *   unavailable within the current context;
     * * `Pair` - both the public key and the corresponding private key.
     */
    enum KeyType {Symmetric, Public, Pair}

    /**
     * Determine if a key is required by the algorithm, and what the details of that key are.
     *
     * The returned `publicSize` and `privateSize` values will always add up to the returned `size`
     * value:
     *
     * * A `Symmetric` key will have `publicSize == 0` and `privateSize > 0`
     *
     * * A `Pair` key will have `publicSize > 0` and `privateSize > 0`
     *
     * Note: A `Public` key _would_ have `publicSize > 0` and `privateSize == 0`, but this method
     * will return `Pair` if the algorithm uses a public/private key pair, and will never return
     * `Public`.
     *
     * @return True iff the algorithm requires a key
     * @return (conditional) the [KeyType] of the key for this algorithm
     * @return (conditional) the number of bytes in the key for this algorithm
     * @return (conditional) the size of the public key portion of the key
     * @return (conditional) the size of the private key portion of the key
     */
    conditional (KeyType keyType, Int size, Int publicSize, Int privateSize) keyRequired();

    /**
     * Factory method: Produce a configured engine that implements this algorithm for the specified
     * key.
     *
     * If the key is only the public key portion of a key pair, then the return type will be a
     * `Verifier` or `Encryptor`; otherwise, the return type will be a `Signer` or `Decryptor`.
     * In other words, signing and decrypting require either a private key or a symmetric key.
     *
     * @param key  either a valid key if the algorithm requires a key, or `Null` if the algorithm
     *             does not require a key
     *
     * @return True if the key is valid and an engine was successfully allocated
     * @return (conditional) the configured crypto engine, which is an `Encryptor`, a `Decryptor`,
     *         a `Verifier`, or a `Signer`, as indicated by a combination of the [category] and
     *         [keyRequired] of the `Algorithm`, and the contents of the passed key
     */
    conditional Encryptor|Decryptor|Verifier|Signer allocate(Key? key);
    }