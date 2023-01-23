import libcrypto.Algorithm;
import libcrypto.Annotations;
import libcrypto.CryptoKey;
import libcrypto.Encryptor;

/**
 * The native [Encryptor] implementation.
 */
service RTEncryptor(Algorithm algorithm, Int blockSize)
        implements Encryptor
    {
    construct(Algorithm algorithm, Int blockSize, CryptoKey? publicKey, CryptoKey? privateKey, Object cipher)
        {
        this.algorithm  = algorithm;
        this.blockSize  = blockSize;
        this.publicKey  = publicKey;
        this.privateKey = privateKey;
        this.cipher     = cipher;
        }

    /**
     * The native cipher.
     */
    protected Object cipher;

    /**
     * The private key used by the encryptor in a "symmetrical" case.
     */
    public/protected CryptoKey? privateKey;

    @Override
    CryptoKey? publicKey;

    @Override
    Byte[] encrypt(Byte[] data)
        {
        Object secret;
        if (CryptoKey publicKey ?= this.publicKey)
            {
            if (publicKey.is(RTKeyStore.RTPublicKey))
                {
                secret = publicKey.secret;
                }
            else if (Byte[] rawKey := publicKey.isVisible())
                {
                secret = rawKey;
                }
            else
                {
                throw new IllegalState($"Unsupported key {publicKey}");
                }
            }
        else
            {
            assert CryptoKey privateKey ?= this.privateKey;

            if (privateKey.is(RTKeyStore.RTPrivateKey))
                {
                secret = privateKey.secret;
                }
            else if (Byte[] rawKey := privateKey.isVisible())
                {
                secret = rawKey;
                }
            else
                {
                throw new IllegalState($"Unsupported key {privateKey}");
                }
            }

        return encrypt(cipher, secret, data);
        }

    @Override
    (Int bytesRead, Int bytesWritten) encrypt(BinaryInput source, BinaryOutput destination)
        {
        TODO
        }

    @Override
    BinaryOutput createOutputEncryptor(BinaryOutput destination,
                                       Annotations? annotations=Null)
        {
        TODO
        }

    @Override
    void close(Exception? cause = Null)
        {
        }

    @Override
    String toString()
        {
        return $"{algorithm.name.quoted()} encryptor";
        }


    // ----- native helpers ------------------------------------------------------------------------

    private Byte[] encrypt(Object cipher, Object secret, Byte[] data) {TODO("Native");}
    }