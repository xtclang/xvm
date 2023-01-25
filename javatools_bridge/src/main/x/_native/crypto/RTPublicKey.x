import libcrypto.PublicKey;

const RTPublicKey
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