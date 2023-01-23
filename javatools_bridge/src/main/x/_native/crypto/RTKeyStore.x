import libcrypto.Certificate;
import libcrypto.Certificate.KeyUsage;
import libcrypto.CryptoKey;
import libcrypto.KeyForm;
import libcrypto.KeyStore;
import libcrypto.PublicKey;
import libcrypto.PrivateKey;
import libcrypto.Signature;

/**
 * The native KeyStore service implementation.
 */
service RTKeyStore
        implements KeyStore
    {
    private construct()
        {
        certCache = new HashMap();
        keyCache  = new HashMap();
        }

    @Override
    @Lazy String[] keyNames.calc()
        {
        String[] names = new String[];
        for (String name : aliases)
            {
            if (isKey(name))
                {
                names += name;
                }
            }
        return names.freeze(True);
        }

    @Override
    conditional CryptoKey getKey(String name)
        {
        if (CryptoKey key := keyCache.get(name))
            {
            return True, key;
            }

        if ((String  algorithm,
             Int     size,
             Byte[]  bytes,
             Object  secret) := getKeyInfo(name))
            {
            CryptoKey key;
            if (bytes.size > 0)
                {
                key = new RTPublicKey(name, algorithm, size, bytes, secret);
                key = &key.maskAs(PublicKey);
                }
            else
                {
                key = new RTPrivateKey(name, algorithm, size, secret);
                key = &key.maskAs(PrivateKey);
                }
            keyCache.put(name, key);
            return True, key;
            }

        return False;
        }

    @Override
    @Lazy Collection<Certificate> certificates.calc()
        {
        Certificate[] certs = new Certificate[];
        for (String name : aliases)
            {
            if (Certificate cert := getCertificate(name))
                {
                certs += cert;
                }
            }
        return certs.freeze(True);
        }

    @Override
    conditional Certificate getCertificate(String name)
        {
        if (Certificate cert := certCache.get(name))
            {
            return True, &cert.maskAs(Certificate);
            }

        if ((String    issuer,
             Int       version,
             Int       notBeforeYear,
             Int       notBeforeMonth,
             Int       notBeforeDay,
             Int       notAfterYear,
             Int       notAfterMonth,
             Int       notAfterDay,
             Boolean[] usageFlags,
             String    signatureAlgorithm,
             Byte[]    signatureBytes,
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
            Signature  sig = new Signature(signatureAlgorithm, signatureBytes);
            CryptoKey? key = publicKeyBytes.size > 0
                    ? new PublicKey(name, publicKeyAlgorithm, publicKeySize, publicKeyBytes)
                    : Null;
            Certificate cert = new X509Certificate(
                                issuer, new Version(version.toString()), notBefore, notAfter,
                                keyUsage, sig, derValue, key);
            certCache.put(name, cert);
            return True, &cert.maskAs(Certificate);
            }
        return False;
        }

    @Override
    String toString()
        {
        return "KeyStore";
        }

    /**
     * The local cache of certificates (not masked).
     */
    private Map<String, Certificate> certCache;

    /**
     * The local cache of CryptoKeys (masked).
     */
    private Map<String, CryptoKey> keyCache;


    // ----- native methods ------------------------------------------------------------------------

    private String[] aliases.get() {TODO("Native");}

    private conditional (String    issuer,
                         Int       version,
                         Int       notBeforeYear,
                         Int       notBeforeMonth,
                         Int       notBeforeDay,
                         Int       notAfterYear,
                         Int       notAfterMonth,
                         Int       notAfterDay,
                         Boolean[] usageFlags,
                         String    signatureAlgorithm,
                         Byte[]    signatureBytes,
                         String    publicKeyAlgorithm,
                         Int       publicKeySize,
                         Byte[]    publicKeyBytes,
                         Byte[]    derValue
                         )
        getCertificateInfo(String name) {TODO("Native");}


    /**
     * @return True iff the name represents a key
     * #return (conditional) an ordinal of the corresponding KeyForm (0=Public, 1=Secret, 2=Pair)
     */
    private conditional Int isKey(String name) {TODO("Native");}

    private conditional (String  algorithm,
                         Int     size,
                         Byte[]  bytes, // only for public key
                         Object  secret
                         )
        getKeyInfo(String name) {TODO("Native");}


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
                  Signature     signature,
                  Byte[]        derValue,
                  CryptoKey?    key)
            {
            this.issuer    = issuer;
            this.version   = version;
            this.lifetime  = notBefore .. notAfter;
            this.keyUsage  = keyUsage;
            this.signature = signature;
            this.derValue  = derValue;
            this.key       = key;
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

        @Override
        Signature signature;

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
            return $|Standard: {standard}
                    |Version: {version}
                    |Issuer: {issuer}
                    |Validity: [{lifetime}]
                    |KeyUsage: {keyUsage}
                    |Signature: {signature}
                    |
                    ;
            }
        }

    static const RTPublicKey
            extends PublicKey
        {
        construct(String name, String algorithm, Int size, Byte[] bytes, Object secret)
            {
            construct PublicKey(name, algorithm, size, bytes);

            this.secret = secret;
            }

        /**
         * The crypto material.
         */
        Object secret;
        }

    static const RTPrivateKey
            extends PrivateKey
        {
        construct(String name, String algorithm, Int size, Object secret)
            {
            construct PrivateKey(name, algorithm, size, []);

            this.secret = secret;
            }

        /**
         * The crypto material.
         */
        Object secret;
        }
    }