/**
 * Represents the result of a cryptographic signing process.
 */
const Signature(String algorithm, Byte[] bytes)
    {
    /**
     * The name of the algorithm used to produce the signature.
     */
    String algorithm;

    /**
     * The raw bytes of the signature.
     */
    Byte[] bytes;
    }
