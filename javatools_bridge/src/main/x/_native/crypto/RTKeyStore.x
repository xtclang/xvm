import libcrypto.Certificate;
import libcrypto.CryptoKey;
import libcrypto.KeyStore;

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
            Date? dateNotBefore = Null;
            if ((Int year, Int month, Int day) := getNotBefore(name))
                {
                dateNotBefore = new Date(year, month, day);
                }
            Date? dateNotAfter = Null;
            if ((Int year, Int month, Int day) := getNotAfter(name))
                {
                dateNotAfter = new Date(year, month, day);
                }

            Certificate cert = new X509Certificate(
                        getIssuer(name), dateNotBefore, dateNotAfter, getTbsCert(name));
            certs += &cert.maskAs(Certificate);
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
    private conditional (Int, Int, Int) getNotBefore(String name) {TODO("Native");}
    private conditional (Int, Int, Int) getNotAfter (String name) {TODO("Native");}
    private Byte[] getTbsCert                       (String name) {TODO("Native");}


    // ----- natural helper classes  ---------------------------------------------------------------

    /**
     * X509Certificate.
     */
    static const X509Certificate
            implements Certificate
        {
        construct(String issuer, Date? notBefore, Date? notAfter, Byte[] tbsCert)
            {
            this.issuer   = issuer;
            this.keyUsage = [DigitalSignature];
            this.lifetime = notBefore .. notAfter;
            this.tbsCert  = tbsCert;
            }

        @Override
        String standard = "X.509";

        @Override
        Version version = v:1;

        @Override
        String issuer;

        @Override
        Set<KeyUsage> keyUsage;

        @Override
        Range<Date?> lifetime;

        Byte[] tbsCert;

        @Override
        Byte[] toDerBytes()
            {
            return tbsCert;
            }

        @Override
        conditional CryptoKey containsKey()
            {
            return False;
            }

        @Override
        String toString()
            {
            return $"{standard}: Issuer={issuer}; Valid={lifetime}";
            }
        }
    }