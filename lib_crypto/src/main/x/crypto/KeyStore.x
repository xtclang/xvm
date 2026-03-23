/**
 * A read-only representation of a store of cryptographic keys and certificates. Not only is it
 * read-only, but it is also likely to be a point-in-time snapshot. It is explicitly designed
 * to not expose any secrets; it represents these secrets (and the ability to pass around the
 * secrets) without exposing them. For example, while the KeyStore allows a [CryptoKey] to be
 * retrieved and used via the [getKey()] method, the contents of that key are generally not
 * [visible](CryptoKey.isVisible()) to the caller.
 *
 * A `KeyStore` is obtained from [CertificateManager.keystoreFor], or by injection if the container
 * supports it. The injection requires an [Info] to be provided; for example:
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
     * @return (conditional) the [CryptoKey], which is likely to be opaque (not
     *         [visible](CryptoKey.isVisible()))
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
     * @return (conditional) the [CryptoPassword], which is likely to be opaque (not
     *         [visible](CryptoPassword.isVisible()))
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