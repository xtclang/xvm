/**
 * A certificate is the combination of three things: a claimed identity, a cryptographic key, and an
 * optional indication (via a "certificate chain") of how the desired trust can be verified.
 * Additionally, the certificate may contain significant related metadata.
 */
interface Certificate
    {
    /**
     * The name of the standard that the certificate complies to. For example: "X.509".
     */
    @RO String standard;

    /**
     * The version of the standard that the certificate complies to. For example, in the case of
     * X.509 certificates, the supported versions are: `v:1`, `v:2`, and `v:3`.
     */
    @RO Version version;

    /**
     * The issuer name, in the format defined by the certificate standard (usually X.509).
     */
    @RO String issuer;

    /**
     * Possible usages of the certificate, as defined by X.509.
     */
    enum KeyUsage(Set<KeyUsage>? extendedCompat = Null)
        {
        // from x.509 via https://www.itu.int/rec/T-REC-X.509/en
        DigitalSignature,
        ContentCommitment, // aka "Non-Repudiation"
        KeyEncipherment,
        DataEncipherment,
        KeyAgreement,
        KeyCertSign,
        CRLSign,
        EncipherOnly,
        DecipherOnly,

        // from https://www.rfc-editor.org/rfc/rfc5280#page-44
        ServerAuth      (Set:[DigitalSignature, KeyEncipherment, KeyAgreement]),
        ClientAuth      (Set:[DigitalSignature, KeyAgreement]),
        CodeSigning     (Set:[DigitalSignature]),
        EmailProtection (Set:[KeyEncipherment, KeyAgreement]),
        TimeStamping    (Set:[DigitalSignature, ContentCommitment]),
        OcspSigning     (Set:[DigitalSignature, ContentCommitment]),
        ;

        /**
         * True iff the KeyUsage value is an "extended key usage" value.
         */
        @RO Boolean extended.get()
            {
            return extendedCompat != Null;
            }
        }

    /**
     * The purposes for which the certificate is intended to be used for; all other purposes are
     * assumed to be invalid uses of this certificate.
     */
    @RO Set<KeyUsage> keyUsage;

    /**
     * The range of dates that the certificate is valid for. It's required that each certificate
     * has a defined lifetime.
     */
    Range<Date> lifetime;

    /**
     * @return the certificate in the DER format.
     */
    Byte[] toDerBytes();

    /**
     * Obtain the key data from the Certificate.
     *
     * @return True iff the Certificate contains a key
     * @return (conditional) the key, iff the Certificate contains one
     */
    conditional CryptoKey containsKey();
    }