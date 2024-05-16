/**
 * Represents a provider of hashes, message digests, and/or cryptographic signatures, based on
 * some input of binary data.
 */
interface Signer
        extends Verifier {
    /**
     * The private key used by the `Signer` algorithm, if the `Signer` has a private key and is
     * allowed to expose it. A `Null` indicates either that the `Signer` has no key (it is a keyless
     * hash algorithm), or that it is not permitted to expose the private key; the difference can
     * be determined via the [keyRequired()](Algorithm.keyRequired) method on the [Algorithm].
     */
    @RO CryptoKey? privateKey;

    /**
     * Produce a signature for the contents of the passed stream.
     *
     * @param in  an [InputStream]
     *
     * @return the [Signature] that corresponds to the contents of the InputStream
     */
    Signature sign(InputStream in) {
        return sign(in.readBytes(in.remaining));
    }

    /**
     * Produce a signature for the contents of the passed array. To produce a signature for the
     * contents of only a sub-section of an array, pass a slice.
     *
     * @param data  an array of bytes to produce a [Signature] for
     *
     * @return the [Signature] that corresponds to the contents of the `Byte` array
     */
    Signature sign(Byte[] data);

    /**
     * Create an output stream that will produce a signature from all of the data written to (or
     * through) it.
     *
     * @param destination  (optional) an underlying stream that the [OutputSigner] will use to write
     *                     through all of the data that is written to the `OutputSigner`
     * @param annotation   (optional) one or more mixins to include in the returned [OutputSigner]
     */
    OutputSigner createOutputSigner(BinaryOutput? destination = Null,
                                    Annotations?  annotations = Null);

    /**
     * A stateful output stream that collects information as it is written to (or thru) the stream,
     * and then uses that information to produce a Signature.
     */
    static interface OutputSigner
            extends BinaryOutput {
        /**
         * Produces a signature from the data that has cumulatively been written to (or thru) the
         * `OutputSigner`.
         *
         * @return the [Signature]
         */
        Signature sign();
    }
}