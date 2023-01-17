/**
 * A `CryptoKey` is a key whose internal state (such as its byte array value) is potentially
 * unavailable through this API. The `CryptoKey` itself may be provided via injection (or via an
 * injected [KeyStore], for example); in such a case, the `CryptoKey` may be usable as an argument
 * to an [Algorithm] that was also injected, despite the key's internal value itself being locally
 * unavailable.
 */
interface CryptoKey
    {
    /**
     * A human-comprehensible name or short description of the CryptoKey. Not intended as a unique
     * key or any other reliable mechanism of identification, but useful for organization of a
     * keystore, or helpful in a log message. For example, "www.acme.com" would suggest that the key
     * is somehow related to that web address.
     */
    @RO String name;

    /**
     * The [KeyForm] of the `CryptoKey`.
     */
    @RO KeyForm form;

    /**
     * The name of the algorithm used to produce the key.
     */
    String algorithm;

    /**
     * The number of bytes in the key, as defined by the algorithm. For example, for the `RSA-2048`
     * algorithms, the key size would be 256.
     */
    @RO Int size;

    /**
     * Determine if the bytes of the `CryptoKey` are accessible to the caller, and if so, obtain
     * them.
     *
     * @return True if the caller is permitted to access the value of the key itself
     * @return (conditional) the key's value, as an array of bytes
     */
    conditional Byte[] isVisible();
    }