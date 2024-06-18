/**
 * A representation of a store of cryptographic keys and certificates.
 *
 * An injection of a KeyStore requires an Info object, for example:
 *
 *     KeyStore getKeystore(File keystoreFile, String password) {
 *         @Inject(opts=new KeyStore.Info(keystoreFile.contents, password)) KeyStore keystore;
 *         return keystore;
 *     }
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
     * @return True iff the specified key name is known by the KeyStore
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
     * Obtain a certificate by its name.
     *
     * @param name  the name the certificate is known by the KeyStore
     *
     * @return True iff the specified certificate name is known by the KeyStore
     * @return (conditional) the [Certificate]
     */
    conditional Certificate getCertificate(String name);

    /**
     * Obtain a certificate chain by its name.
     *
     * @param name  the name the certificate chain is known by the KeyStore
     *
     * @return the array of [Certificate] objects; empty if not found
     */
    Certificate[] getCertificateChain(String name);

    /**
     * The KeyStore resource information.
     */
    static const Info(Byte[] content, Password password);
}