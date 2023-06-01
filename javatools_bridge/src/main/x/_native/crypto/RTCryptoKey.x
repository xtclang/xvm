import libcrypto.CryptoKey;

mixin RTCryptoKey(Object secret)
        into CryptoKey {
    /**
     * The crypto material.
     */
    public/protected Object secret;
}