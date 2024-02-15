import libcrypto.Certificate;
import libcrypto.Certificate.KeyUsage;
import libcrypto.CryptoKey;
import libcrypto.CryptoPassword;
import libcrypto.KeyForm;
import libcrypto.KeyStore;
import libcrypto.KeyPair;
import libcrypto.NamedPassword;
import libcrypto.OpaqueKey;
import libcrypto.PublicKey;
import libcrypto.PrivateKey;
import libcrypto.Signature;

/**
 * The native KeyStore service implementation.
 */
service RTKeyStore
        implements KeyStore {

    private construct() {
        certCache = new HashMap();
        keyCache  = new HashMap();
    }

    @Override
    @Lazy String[] keyNames.calc() {
        String[] names = new String[];
        for (String name : aliases) {
            if (Int type := entryType(name), type == EntrySecret || type == EntryPair) {
                names += name;
            }
        }
        return names.freeze(True);
    }

    @Override
    conditional CryptoKey getKey(String name) {
        if (CryptoKey key := keyCache.get(name)) {
            return True, key;
        }

        if ((String  algorithm,
             Int     size,
             Object  secretHandle,
             Object? publicHandle,
             Byte[]  publicBytes) := getKeyInfo(name)) {
            CryptoKey key;
            if (publicHandle == Null) {
                key = new RTPrivateKey(name, algorithm, size, secretHandle);
                key = &key.maskAs(PrivateKey);
            } else {
                CryptoKey privateKey = new RTPrivateKey(name, algorithm, size, secretHandle);
                CryptoKey publicKey  = new RTPublicKey (name, algorithm, size, publicBytes, publicHandle);
                key = new KeyPair(name, &publicKey.maskAs(PublicKey), &privateKey.maskAs(PrivateKey));
            }
            keyCache.put(name, key);
            return True, key;
        }

        return False;
    }

    @Override
    @RO String[] passwordNames.get() {
        String[] names = new String[];
        for (String name : aliases) {
            if (Int type := entryType(name), type == EntryPassword) {
                names += name;
            }
        }
        return names.freeze(True);
    }

    @Override
    conditional CryptoPassword getPassword(String name) {
        if (String pwdString := getPasswordInfo(name)) {
            CryptoPassword pwd = new NamedPassword(name, pwdString);
            return True, &pwd.maskAs(CryptoPassword);
        }
        return False;
    }

    @Override
    @Lazy Collection<Certificate> certificates.calc() {
        Certificate[] certs = new Certificate[];
        for (String name : aliases) {
            if (Certificate cert := getCertificate(name)) {
                certs += cert;
            }
        }
        return certs.freeze(True);
    }

    @Override
    conditional Certificate getCertificate(String name) {
        if (Certificate cert := certCache.get(name)) {
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
             Object    publicSecret,
             Byte[]    derValue) := getCertificateInfo(name)) {
            Date notBefore = new Date(notBeforeYear, notBeforeMonth, notBeforeDay);
            Date notAfter  = new Date(notAfterYear,  notAfterMonth,  notAfterDay );

            Set<KeyUsage> keyUsage = new HashSet();
            for (Int i : 0 ..< usageFlags.size) {
                if (usageFlags[i]) {
                    keyUsage.add(KeyUsage.values[i]);
                }
            }
            Signature  sig = new Signature(signatureAlgorithm, signatureBytes);
            CryptoKey? key = publicKeyBytes.size > 0
                    ? new RTPublicKey(name, publicKeyAlgorithm, publicKeySize, publicKeyBytes, publicSecret)
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
    String toString() {
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

    /**
     * @return True iff the name represents an existing keystore entry
     * #return (conditional) any of the Entry* values
     */
    private conditional Int entryType(String name) {TODO("Native");}

    private static Int EntrySecret      = 0;
    private static Int EntryPair        = 1;
    private static Int EntryCertificate = 2;
    private static Int EntryPassword    = 3;

    /**
     * @return True iff the name represents a key
     * #return (conditional) the key info
     */
    private conditional (String  algorithm,
                         Int     size,
                         Object  secretHandle,
                         Object? publicHandle,  // Null for symmetrical key
                         Byte[]  publicBytes    // not empty for public/private pair
                         )
        getKeyInfo(String name) {TODO("Native");}

    /**
     * @return True iff the name represents an existing password
     * #return (conditional) the password
     */
    private conditional String getPasswordInfo(String name) {TODO("Native");}

    /**
     * @return True iff the name represents an existing certificate
     * #return (conditional) the certificate info
     */
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
                         Object    publicSecret,
                         Byte[]    derValue
                         )
        getCertificateInfo(String name) {TODO("Native");}


    // ----- natural helper methods  ---------------------------------------------------------------

    /**
     * Helper function to extract the native secret from the specified key.
     */
    static conditional Object extractSecret(CryptoKey key) {
        if (OpaqueKey opaqueKey := &key.revealAs(OpaqueKey)) {
            return extractSecret(opaqueKey);
        }
        if (RTCryptoKey rtKey := &key.revealAs(RTCryptoKey)) {
            return True, rtKey.secret;
        }
        if (Byte[] rawKey := key.isVisible()) {
            return True, rawKey;
        }
        return False;
    }


    // ----- natural helper classes  ---------------------------------------------------------------

    /**
     * X509Certificate.
     */
    static const X509Certificate
            implements Certificate {

        construct(String        issuer,
                  Version       version,
                  Date          notBefore,
                  Date          notAfter,
                  Set<KeyUsage> keyUsage,
                  Signature     signature,
                  Byte[]        derValue,
                  CryptoKey?    key) {
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
        Byte[] toDerBytes() {
            return derValue;
        }

        @Override
        conditional CryptoKey containsKey() {
            return Nullable.notNull(key);
        }

        @Override
        String toString() {
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
}