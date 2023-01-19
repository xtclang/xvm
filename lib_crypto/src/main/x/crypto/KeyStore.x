/**
 * A representation of a store of cryptographic keys and certificates.
 */
interface KeyStore
    {
    /**
     * The key names in this KeyStore.
     */
    @RO String[] keyNames;

    /**
     * @param name  the name this key is known by the KeyStore
     */
    conditional CryptoKey getKey(String name);

    /**
     * The certificates in the `KeyStore`.
     */
    @RO Collection<Certificate> certificates;

    /**
     * @param name  the name this certificate is known by the KeyStore
     */
    conditional Certificate getCertificate(String name);

    /**
     * The KeyStore resource information.
     */
    static const Info(Byte[] content, String password);
    }