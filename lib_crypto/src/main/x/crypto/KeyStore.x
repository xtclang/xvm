/**
 * A representation of a store of cryptographic keys and certificates.
 */
interface KeyStore {
    /**
     * The key names in this KeyStore.
     */
    @RO String[] keyNames;

    /**
     * Obtain a key by its name.
     *
     * @param name  the name the key is known by the KeyStore
     *
     * @return True iff the specified kay name is known by the KeyStore
     * @return (conditional) the [CryptoKey]
     */
    conditional CryptoKey getKey(String name);

    /**
     * The password names in this KeyStore.
     */
    @RO String[] passwordNames;

    /**
     * Obtain a password by its name.
     *
     * @param name  the name the password is known by the KeyStore
     *
     * @return True iff the specified password name is known by the KeyStore
     * @return (conditional) the [CryptoPassword]
     */
    conditional CryptoPassword getPassword(String name);

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
    static const Info(Byte[] content, Password password);
}