/**
 * Represents a verifier of cryptographic signatures.
 */
interface Verifier
        extends Closeable {
    /**
     * The algorithm implemented by this verifier.
     */
    @RO Algorithm algorithm;

    /**
     * The public key used by the `Verifier` algorithm, if the `Verifier` has a public key. A `Null`
     * indicates either that the `Verifier` has no key (it is a keyless hash algorithm), or that it
     * only uses a symmetric private key.
     */
    @RO CryptoKey? publicKey;

    /**
     * A previously generated "message digest" can be passed to a `Verifier` as either a `Signature`
     * object or an array of bytes.
     */
    typedef Signature|Byte[] as Digest;

    /**
     * For a `Signing` algorithm, this is the size in bytes of the resulting signature. (A signature
     * is also called a "_hash_" or a "_message digest_".)
     */
    @RO Int signatureSize;

    /**
     * Verify that the provided signature is valid for the contents of the passed stream.
     *
     * @param signature  the previously generated [Signature] (or `Byte[]`) to verify
     * @param in         an [InputStream]
     *
     * @return True iff the signature is valid for the contents of the InputStream
     */
    Boolean verify(Digest signature, InputStream in) {
        return verify(signature, in.readBytes(in.remaining));
    }

    /**
     * Verify that the provided signature is valid for the contents of the passed `Byte` array.
     * To verify a signature for the contents of only a sub-section of an array, pass a slice.
     *
     * @param signature  the previously generated [Signature] (or `Byte[]`) that can be used to
     *                   verify that the passed data hasn't changed since the signature was
     *                   generated, and/or to verify that the signature was created from that data
     *                   using the associated key
     * @param data       an array of bytes that was supposedly used to create the signature
     *
     * @return True iff the signature is valid for the contents of the `Byte` array
     */
    Boolean verify(Digest signature, Byte[] data);

    /**
     * Create an output stream that will verify a signature using all of the data written to (or
     * through) the stream. In theory, this allows a massive amount of signed data to be streamed
     * and its signature verified, without having to buffer all of the data in memory.
     *
     * @param signature    the previously generated [Signature] (or `Byte[]`) to verify
     * @param destination  (optional) an underlying stream that the [OutputSigner] will use to write
     *                     through all of the data that is written to the `OutputSigner`
     * @param annotations  (optional) one or more mixins to include in the [OutputSigner]
     */
    OutputVerifier createOutputVerifier(Digest        signature,
                                        BinaryOutput? destination=Null,
                                        Annotations?  annotations=Null);

    /**
     * A stateful output stream that collects information as it is written to (or through) the
     * stream, and then uses that information to verify a previously-provided Signature.
     */
    static interface OutputVerifier
            extends BinaryOutput {
        /**
         * Determine if the signature being verified matches the data that has been written to (or
         * through) the `OutputVerifier`.
         */
        Boolean signatureMatches();
    }

    /**
     * Create an input stream that will verify a signature using all of the data read through it.
     * The reason that the signature is not passed to this method is that it may be located in the
     * stream _after_ the signed data.
     *
     * @param destination  (optional) an underlying stream that the [InputVerifier] will use to read
     *                     through all of the data that is read from the `InputVerifier`
     * @param annotations  (optional) one or more mixins to include in the [InputVerifier]
     */
    InputVerifier createInputVerifier(BinaryInput  source,
                                      Annotations? annotations=Null);

    /**
     * A stateful input stream that collects information as data is read through the stream,  and
     * then uses that information to verify the passed Signature.
     */
    static interface InputVerifier
            extends BinaryInput {
        /**
         * Determine if the signature being verified matches the data that has been read through the
         * InputVerifier.
         *
         * @param signature    the previously generated [Signature] (or `Byte[]`) to verify
         *
         * @return True iff the provided signature verifies that the data read through this
         *         `InputVerifier` has not been tampered with
         */
        Boolean signatureMatches(Digest signature);
    }
}