import libcrypto.PrivateKey;

const RTPrivateKey
        extends PrivateKey
        incorporates RTCryptoKey
    {
    construct(String name, String algorithm, Int size, Object secret)
        {
        construct PrivateKey(name, algorithm, size, []);
        construct RTCryptoKey(secret);
        }
    }