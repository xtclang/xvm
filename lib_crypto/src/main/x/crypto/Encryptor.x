/**
 * Represents the ability to cryptographically encrypt a message.
 */
interface Encryptor
        extends Closeable
    {
    /**
     * The algorithm implemented by this `Encryptor`.
     */
    @RO Algorithm algorithm;

    /**
     * The public key used by the `Encryptor` algorithm, if the `Encryptor` has a public key. A
     * `Null` indicates that the `Encryptor` uses a symmetric private key.
     */
    @RO Key? publicKey;

    /**
     * Encrypt the provided data. The resulting data may not be the same size as the provided data,
     * if block padding is used by the cryptographic algorithm.
     *
     * @param bytes  the data to encrypt
     *
     * @return the encrypted data
     */
    Byte[] encrypt(Byte[] bytes);

    /**
     * Encrypt the data from the provided source stream into the provided destination stream.
     *
     * @param source       the stream containing the data to encrypt
     * @param destination  the stream that the encrypted data will be written to
     *
     * @return the number of bytes read from the source stream
     * @return the number of bytes written to the destination stream
     */
    (Int bytesRead, Int bytesWritten) encrypt(BinaryInput source, BinaryOutput destination);

    /**
     * Create an output stream that will encrypt data as it is written.
     *
     * **IMPORTANT**: When the information to encrypt has all been written, the `BinaryOutput`
     * returned by this method **must be closed**, because the encryption algorithm may encrypt a
     * block at a time, and closing the `BinaryOutput` will perform the necessary padding on the
     * last block of data before encrypting and writing that data out. Note that closing the
     * `BinaryOutput` returned by this method will **not** close the `destination` stream.
     *
     * @param destination  (optional) an underlying stream that the [Encryptor] will write the
     *                     encrypted data to
     * @param annotations  (optional) one or more mixins to include in the returned [BinaryOutput]
     *
     * @return the `BinaryOutput` to write the data to that needs to be encrypted; remember to close
     *         this returned `BinaryOutput` as soon as all of the data to encrypt has been written
     *         to it
     */
    BinaryOutput createOutputEncryptor(BinaryOutput destination,
                                       Annotations? annotations=Null);
    }
