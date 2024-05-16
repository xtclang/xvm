/**
 * A pass-through Decryptor implementation that doesn't change the content.
 */
static const NullDecryptor
    implements Decryptor {

    // ----- Decryptor interface -------------------------------------------------------------------

    @Override
    CryptoKey? privateKey;

    @Override
    Byte[] decrypt(Byte[] bytes) = bytes;

    @Override
    (Int bytesRead, Int bytesWritten) decrypt(BinaryInput source, BinaryOutput destination) {
        TODO
    }

    @Override
    BinaryInput createInputDecryptor(BinaryInput source, Annotations? annotations = Null) {
        TODO
    }

    // ----- Encryptor interface -------------------------------------------------------------------

    @Override
    String algorithm = "PassThrough";

    @Override
    CryptoKey? publicKey;

    @Override
    Byte[] encrypt(Byte[] data) = data;

    @Override
    (Int bytesRead, Int bytesWritten) encrypt(BinaryInput source, BinaryOutput destination) {
        TODO
    }

    @Override
    BinaryOutput createOutputEncryptor(BinaryOutput destination, Annotations? annotations = Null) {
        TODO
    }
}