/**
 * Represents the ability to decrypt a previously cryptographically encrypted message.
 */
interface Decryptor
        extends Encryptor
    {
    /**
     * The private key used by the `Decryptor` algorithm. This is either a symmetric key or a
     * public/private key pair, as indicated by the [algorithm].
     *
     * A `Null` indicates either that the `Decryptor` is not permitted to expose the private key.
     */
    @RO Key? privateKey;

    /**
     * Decrypt the provided data. The resulting data may not be the same size as the provided data,
     * if block padding is used by the cryptographic algorithm.
     *
     * @param bytes  the data to decrypt
     *
     * @return the decrypted data
     */
    Byte[] decrypt(Byte[] bytes);

    /**
     * Decrypt the data from the provided source stream into the provided destination stream.
     *
     * @param source       the stream containing the data to decrypt
     * @param destination  the stream that the decrypted data will be written to
     *
     * @return the number of bytes read from the source stream
     * @return the number of bytes written to the destination stream
     */
    (Int bytesRead, Int bytesWritten) decrypt(BinaryInput source, BinaryOutput destination);

    /**
     * Create an input stream that will decrypt data as it is read.
     *
     * @param source       an underlying stream that the [Decryptor] will use to read the encrypted
     *                     data that needs to be decrypted
     * @param annotations  (optional) one or more mixins to include in the returned [BinaryInput]
     *
     * @return the `BinaryInput` to read the decrypted data from
     */
    BinaryInput createInputDecryptor(BinaryInput  source,
                                     Annotations? annotations=Null);
    }
