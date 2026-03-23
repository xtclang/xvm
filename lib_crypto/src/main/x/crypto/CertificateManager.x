/**
 * A representation of a [Certificate] and [KeyStore] management facility, which is a fundamental
 * security service of an underlying host, and by extension, a representation of a certificate
 * authority. As such, an instance of CertificateManager is generally expected to be obtainable only
 * via injection.
 *
 * Within a [Container] that supports injection of a CertificateManager, it is possible to obtain
 * the CertificateManager by specifying a provider name; the default provider name is "self", for
 * self-signed (locally generated) certificates:
 *
 *     @Inject(opts="self") CertificateManager mgr;
 *
 * The currently supported providers for injection are:
 *
 * * `self` - for creating self-signed certificates;
 * * `certbot` - for production use of [https://letsencrypt.org/] via certbot;
 * * `certbot-staging` - for non-productions use of [https://letsencrypt.org/] via certbot, which
 *   is useful for testing an account with LetsEncrypt; the necessary test-mode indicators are
 *   provided to LetsEncrypt to avoid having the account suspended or banned if an error occurs.
 */
interface CertificateManager {
    /**
     * Obtain the [KeyStore] for the specified `KeyStore` file.
     *
     * @param keystore  the [File] representing the store ('PKCS12' type)
     * @param pwd       the [Password] for the [KeyStore] in the specified `File`
     *
     * @return the [KeyStore]
     */
    KeyStore keystoreFor(File keystore, Password pwd);

    /**
     * Change the password for the specified `KeyStore` file. As a result of this operation, the
     * [File] will be overwritten using the new [Password].
     *
     * @param keystore  the [File] object representing the store, or the [KeyStore] itself
     * @param oldPwd    the old [Password] for the `KeyStore`
     * @param newPwd    the new [Password] for the `KeyStore`
     *
     * @return the [KeyStore] encrypted using the new [Password]
     *
     * @throws IOException if anything goes wrong
     */
    KeyStore encryptKeyStore(File keystore, Password oldPwd, Password newPwd);

    /**
     * Create a certificate, sign it and save to the specified keystore file.
     *
     * See the (X.509 certificate spec)[https://www.rfc-editor.org/rfc/rfc5280] for the format and
     * semantics of the distinguished name and its parts.
     *
     * The "Common Name" part of the distinguished name should be the domain name or sub-domain name
     * that is managed by (i.e. routed to) the corresponding Ecstasy container.
     *
     * This operation consists of a number of steps:
     *  - create a Certificate Signing Request (CSR) for the specified distinguished name
     *  - send the CSR to Signing Authority (SA)
     *  - provide the proof of the domain ownership during the SA verification phase
     *  - receive the certificate from the SA
     *  - save the private key and the certificate in the specified file using the provided password
     *
     * @param keystore  the [File] representing the store ('PKCS12' type)
     * @param pwd       the password for the keystore
     * @param name      the name the certificate is known by the keystore
     * @param dName     the distinguished name string, which is comma-delimited string of X.509
     *                  certificate attributes (e.g.: C=US,ST=MA,O=XQIZ.IT Corp.,CN=host.xqiz.it)
     *
     * @throws IOException if anything goes wrong
     */
    void createCertificate(File keystore, Password pwd, String name, String dName);

    /**
     * Revoke a certificate.
     *
     * This operation consists of a number of steps:
     *  - create a Certificate Revocation Request (CRR)
     *  - send the CRR to Signing Authority (SA)
     *  - provide the proof of the domain ownership during the SA verification phase
     *  - receive the revocation confirmation from the SA
     *  - remove the private key and the certificate from the specified keystore
     *
     * @param keystore  the [File] representing the store ('PKCS12' type)
     * @param pwd       the password for the keystore
     * @param name      the name the certificate is known by the keystore
     *
     * @throws IOException if anything goes wrong
     */
    void revokeCertificate(File keystore, Password pwd, String name);

    /**
     * Create a secret (symmetric) key.
     *
     * @param keystore  the [File] representing the store ('PKCS12' type)
     * @param pwd       the password for the keystore
     * @param name      the name the symmetric key is known by the keystore
     *
     * @throws IOException if anything goes wrong
     */
    void createSymmetricKey(File keystore, Password pwd, String name);

    /**
     * Create a password entry.
     *
     * @param keystore  the [File] representing the store ('PKCS12' type)
     * @param pwd       the password for the keystore
     * @param name      the name the password entry is known by the keystore
     * @param pwdValue  the value of the password entry
     *
     * @throws IOException if anything goes wrong
     */
    void createPassword(File keystore, Password pwd, String name, String pwdValue);

    /**
     * Extract a key (private or secret).
     *
     * Note: Unlike [KeyStore] methods that return [CryptoKey] objects which can be opaque to avoid
     * exposing the underlying crypto material, this method explicitly returns the raw bytes of the
     * key, and therefore the data obtained from this method must be handled with extreme caution.
     *
     * @param keystore  the [File] object representing the store, or the [KeyStore] itself
     * @param pwd       the [Password] to use to access the contents of the `KeyStore`
     * @param name      the name the key is known by the `KeyStore`
     *
     * @return the content of the key in the DER format
     *
     * @throws IOException if anything goes wrong
     */
    Byte[] extractKey(File|KeyStore keystore, Password pwd, String name);

    /**
     * Helper function to create a distinguished name in the format prescribed by the (X.509
     * certificate spec)[https://www.rfc-editor.org/rfc/rfc5280]
     *
     * @param domain    the fully qualified domain name such as "acme.com.xqiz.it"
     * @param org       (optional) Organization (the legal company name)
     * @param orgUnit   (optional) Organizational Unit such as division or department of company
     * @param locality  (optional) Locality or City name
     * @param state     (optional) State or Province (must be spelled out completely such as "New York")
     * @param country   (optional) two character ISO country code (such as "US")
     *
     * @return the distinguished name in the X.509 spec format
     */
    static String distinguishedName(
        String domain,
        String org      = "",
        String orgUnit  = "",
        String locality = "",
        String state    = "",
        String country  = ""
        ) {
        assert domain.size > 0 as "Domain must be specified";

        return $|{{if (country.size  > 0) {$.addAll($"C={country}," );}}}\
                |{{if (state.size    > 0) {$.addAll($"S={state},"   );}}}\
                |{{if (locality.size > 0) {$.addAll($"L={locality},");}}}\
                |{{if (org.size      > 0) {$.addAll($"O={org},"     );}}}\
                |{{if (orgUnit.size  > 0) {$.addAll($"OU={orgUnit},");}}}\
                |CN={domain}
                ;
    }
}