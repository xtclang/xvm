import libcrypto.PublicKey;

const RTPublicKey
        extends PublicKey
        incorporates RTCryptoKey
    {
    construct(String name, String algorithm, Int size, Byte[] bytes, Object secret)
        {
        construct PublicKey(name, algorithm, size, bytes);
        construct RTCryptoKey(secret);
        }
    }