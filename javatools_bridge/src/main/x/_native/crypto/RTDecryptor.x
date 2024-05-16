import libcrypto.Algorithm;
import libcrypto.Annotations;
import libcrypto.CryptoKey;
import libcrypto.Decryptor;


/**
 * The native [Encryptor] implementation.
 */
service RTDecryptor
        extends RTEncryptor
        implements Decryptor {

    construct(String algorithm, Int blockSize, CryptoKey? publicKey, CryptoKey privateKey, Object cipher) {
        construct RTEncryptor(algorithm, blockSize, publicKey, privateKey, cipher);
    }

    @Override
    Byte[] decrypt(Byte[] bytes) {
        assert CryptoKey privateKey ?= this.privateKey;

        if (Object secret := RTKeyStore.extractSecret(privateKey)) {
            return decrypt(cipher, secret, bytes);
        }

        throw new IllegalState($"Unsupported key {privateKey}");
    }

    @Override
    (Int bytesRead, Int bytesWritten) decrypt(BinaryInput source, BinaryOutput destination) {
        TODO
    }

    @Override
    BinaryInput createInputDecryptor(BinaryInput  source, Annotations? annotations = Null) {
        TODO
    }

    @Override
    void close(Exception? cause = Null) {}

    @Override
    String toString() {
        return $"{algorithm.quoted()} decryptor";
    }


    // ----- native helpers ------------------------------------------------------------------------

    protected Byte[] decrypt(Object cipher, Object secret, Byte[] bytes) {TODO("Native");}
}