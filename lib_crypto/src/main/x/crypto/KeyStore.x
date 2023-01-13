/**
 * A representation of a store of cryptographic keys and certificates.
 */
interface KeyStore
    {
    /**
     * The keys in the `KeyStore`.
     */
    @RO Collection<CryptoKey> keys;

    /**
     * The certificates in the `KeyStore`.
     */
    @RO Collection<Certificate> certificates;
    }