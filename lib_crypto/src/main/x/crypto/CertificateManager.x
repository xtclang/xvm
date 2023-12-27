/**
 * A representation of a Certificate management facility.
 */
interface CertificateManager {

    /**
     * Create a certificate, sign it and save to the specified keystore file.
     *
     * For the format and semantics of the parts of the distinguished name please see
     * (X.509 certificate spec)[https://www.rfc-editor.org/rfc/rfc5280]
     *
     * Note, that the "Common Name" part of the distinguished name should represent a sub-domain
     * managed by the corresponding Ecstasy container, which is most likely to be a subdomain of
     * "users.xqiz.it".
     *
     * This operation consists of a number of steps:
     *  - create a Certificate Signing Request (CSR) for the specified distinguished name
     *  - send the CSR to Signing Authority (SA)
     *  - provide the proof of the domain ownership during the SA verification phase
     *  - receive the certificate from the SA
     *  - save the private key and the certificate in the specified file using the provided password
     *
     * @param file      the file object representing the store ('PKCS12' type)
     * @param password  the password for the keystore
     * @param name      the name the certificate is known by the KeyStore
     * @param dName     the distinguished name string, which is comma-delimited string of X.509
     *                  certificate attributes (e.g.: C=US,ST=MA,O=XQIZ.IT Corp.,CN=www.xqiz.it)
     *
     * @throws IOException if anything goes wrong
     */
    void createCertificate(File keystore, String password, String name, String dName);

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
     * @param keystore  the file object representing the store ('PKCS12' type)
     * @param password  the password for the keystore
     * @param name      the name the certificate is known by the KeyStore
     *
     * @throws IOException if anything goes wrong
     */
    void revokeCertificate(File keystore, String password, String name);

    /**
     * Create a secret (symmetric) key.
     *
     * @param keystore  the file object representing the store ('PKCS12' type)
     * @param password  the password for the keystore
     * @param name      the name the symmetric key is known by the KeyStore
     *
     * @throws IOException if anything goes wrong
     */
    void createSymmetricKey(File keystore, String password, String name);

    /**
     * Create a password entry.
     *
     * @param keystore       the file object representing the store ('PKCS12' type)
     * @param storePassword  the password for the keystore
     * @param name           the name the password entry is known by the KeyStore
     * @param passwordValue  the value of the password entry
     *
     * @throws IOException if anything goes wrong
     */
    void createPassword(File keystore, String storePassword, String name, String passwordValue);

    /**
     * Change the keystore password.
     *
     * @param keystore     the file object representing the store ('PKCS12' type)
     * @param password     the password for the keystore
     * @param newPassword  the new password for the keystore
     *
     * @throws IOException if anything goes wrong
     */
    void changeStorePassword(File keystore, String password, String newPassword);

    /**
     * Helper function to create a distinguished name in the format prescribed by the (X.509
     * certificate spec)[https://www.rfc-editor.org/rfc/rfc5280]
     *
     * @param domain   the fully qualified domain name such as "acme.users.xqiz.it"
     * @param org      (optional) Organization (the legal company name)
     * @param orgUnit  (optional) Organizational Unit such as division or department of company
     * @param locality (optional) Locality or City name
     * @param state    (optional) State or Province (must be spelled out completely such as "New York")
     * @param country  (optional) two character ISO country code (such as "US")
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
                |{{if (orgUnit.size  > 0) {$.addAll($"OU={orgUnit},");}}}\
                |{{if (org.size      > 0) {$.addAll($"OU={org},"    );}}}\
                |CN={domain}
                ;
    }
}