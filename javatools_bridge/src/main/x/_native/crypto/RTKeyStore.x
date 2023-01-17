import libcrypto.Certificate;
import libcrypto.Certificate.KeyUsage;
import libcrypto.CryptoKey;
import libcrypto.KeyForm;
import libcrypto.KeyStore;
import libcrypto.PublicKey;

/**
 * The native KeyStore service implementation.
 */
service RTKeyStore
        implements KeyStore
    {
    @Override
    Collection<CryptoKey> keys.get()
        {
        TODO
        }

    @Override
    @Lazy Collection<Certificate> certificates.calc()
        {
        Certificate[] certs = new Certificate[];
        for (String name : aliases)
            {
            if ((String    issuer,
                 Int       version,
                 Int       notBeforeYear,
                 Int       notBeforeMonth,
                 Int       notBeforeDay,
                 Int       notAfterYear,
                 Int       notAfterMonth,
                 Int       notAfterDay,
                 Boolean[] usageFlags,
                 String    publicKeyAlgorithm,
                 Int       publicKeySize,
                 Byte[]    publicKeyBytes,
                 Byte[]    derValue) := getCertificateInfo(name))
                {
                Date notBefore = new Date(notBeforeYear, notBeforeMonth, notBeforeDay);
                Date notAfter  = new Date(notAfterYear,  notAfterMonth,  notAfterDay );

                Set<KeyUsage> keyUsage = new HashSet();
                for (Int i : 0 ..< usageFlags.size)
                    {
                    if (usageFlags[i])
                        {
                        keyUsage.add(KeyUsage.values[i]);
                        }
                    }

                CryptoKey? key = publicKeyBytes.size > 0
                        ? new PublicKey(name, publicKeyAlgorithm, publicKeySize, publicKeyBytes)
                        : Null;
                Certificate cert = new X509Certificate(
                        issuer, new Version(version.toString()), notBefore, notAfter,
                        keyUsage, derValue, key);
                certs += &cert.maskAs(Certificate);
                }
            }
        return certs.freeze(True);
        }

    @Override
    String toString()
        {
        return "KeyStore";
        }


    // ----- native methods ------------------------------------------------------------------------

    private String[] aliases.get()                                {TODO("Native");}

    private String getIssuer                        (String name) {TODO("Native");}
    private conditional (String    issuer,
                         Int       version,
                         Int       notBeforeYear,
                         Int       notBeforeMonth,
                         Int       notBeforeDay,
                         Int       notAfterYear,
                         Int       notAfterMonth,
                         Int       notAfterDay,
                         Boolean[] usageFlags,
                         String    publicKeyAlgorithm,
                         Int       publicKeySize,
                         Byte[]    publicKeyBytes,
                         Byte[]    derValue
                         )
        getCertificateInfo(String name) {TODO("Native");}


    // ----- natural helper classes  ---------------------------------------------------------------

    /**
     * X509Certificate.
     */
    static const X509Certificate
            implements Certificate
        {
        construct(String        issuer,
                  Version       version,
                  Date          notBefore,
                  Date          notAfter,
                  Set<KeyUsage> keyUsage,
                  Byte[]        derValue,
                  CryptoKey?    key)
            {
            this.issuer   = issuer;
            this.version  = version;
            this.keyUsage = keyUsage;
            this.lifetime = notBefore .. notAfter;
            this.derValue = derValue;
            this.key      = key;
            }

        @Override
        String standard = "X.509";

        @Override
        Version version;

        @Override
        String issuer;

        @Override
        Set<KeyUsage> keyUsage;

        @Override
        Range<Date> lifetime;

        Byte[] derValue;

        CryptoKey? key;

        @Override
        Byte[] toDerBytes()
            {
            return derValue;
            }

        @Override
        conditional CryptoKey containsKey()
            {
            return Nullable.notNull(key);
            }

        @Override
        String toString()
            {
            return $|
                    |Standard: {standard}
                    |Version: {version}
                    |Issuer: {issuer}
                    |Validity: [{lifetime}]
                    |Extensions: {keyUsage}
                    |
                    ;
            }
        }
    }