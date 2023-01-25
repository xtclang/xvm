import libcrypto.PrivateKey;

const RTPrivateKey
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