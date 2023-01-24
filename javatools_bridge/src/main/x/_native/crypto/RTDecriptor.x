import libcrypto.Algorithm;
import libcrypto.Annotations;
import libcrypto.CryptoKey;
import libcrypto.Decryptor;

import RTKeyStore.RTPrivateKey;


/**
 * The native [Encryptor] implementation.
 */
service RTDecryptor
        extends RTEncryptor
        implements Decryptor
    {
    construct(Algorithm algorithm, Int blockSize, CryptoKey? publicKey, CryptoKey? privateKey, Object cipher)
        {
        construct RTEncryptor(algorithm, blockSize, publicKey, privateKey, cipher);
        }

    @Override
    Byte[] decrypt(Byte[] bytes)
        {
        assert CryptoKey privateKey ?= this.privateKey;

        Object secret;
        if (privateKey := &privateKey.revealAs(RTPrivateKey))
            {
            secret = privateKey.as(RTPrivateKey).secret;
            }
        else if (Byte[] rawKey := privateKey.isVisible())
            {
            secret = rawKey;
            }
        else
            {
            throw new IllegalState($"Unsupported key {publicKey}");
            }

        return decrypt(cipher, secret, bytes);
        }

    @Override
    (Int bytesRead, Int bytesWritten) decrypt(BinaryInput source, BinaryOutput destination)
        {
        TODO
        }

    @Override
    BinaryInput createInputDecryptor(BinaryInput  source,
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
        return $"{algorithm.name.quoted()} decryptor";
        }


    // ----- native helpers ------------------------------------------------------------------------

    protected Byte[] decrypt(Object cipher, Object secret, Byte[] bytes) {TODO("Native");}
    }