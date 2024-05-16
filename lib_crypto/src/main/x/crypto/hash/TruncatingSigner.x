/**
 * A [Signer] and [Verifier] that delegates the signing to an underlying `Signer`, and then
 * truncates the result to a smaller number of bytes. The prototypical example use case is the
 * `SHA-512-256` algorithm specified by the
 * ['Digest' HTTP Authentication Scheme](https://datatracker.ietf.org/doc/html/rfc7616).
 */
const TruncatingSigner
        delegates Signer(baseSigner)
        delegates Algorithm(baseSigner.algorithm) {
    /**
     * TODO
     *
     * @param baseSigner
     * @param truncateTo
     * @param fullName
     */
    construct(Signer baseSigner, Int truncateTo, String? fullName = Null) {
        assert 0 < truncateTo < baseSigner.signatureSize;

        this.baseSigner = baseSigner;
        this.truncateTo = truncateTo;
        this.name       = fullName ?: $"{baseSigner.algorithm.name}-{8*truncateTo}"; // REVIEW is '-' standard? or '/'?
    }

    protected/private Signer baseSigner;
    protected/private Int truncateTo;


    // ----- Algorithm interface -------------------------------------------------------------------

    @Override
    String name;

    @Override
    Signer allocate(CryptoKey? key = Null) {
        // it's not expected that this method will ever be used, so there's no reason to attempt to
        // optimize it by proving that we can return "this" instead of allocating a new Signer
        return new TruncatingSigner(baseSigner.algorithm.allocate(key).as(Signer), truncateTo, name);
    }


    // ----- Signer interface ----------------------------------------------------------------------

    @Override
    Signature sign(InputStream in) {
        Signature baseSignature = baseSigner.sign(in);
        return new Signature(name, baseSignature.bytes[0 ..< truncateTo]);
    }

    @Override
    Signature sign(Byte[] data) {
        Signature baseSignature = baseSigner.sign(data);
        return new Signature(name, baseSignature.bytes[0 ..< truncateTo]);
    }

    @Override
    OutputSigner createOutputSigner(BinaryOutput? destination = Null,
                                    Annotations?  annotations = Null) {
        OutputSigner baseOutputSigner = baseSigner.createOutputSigner(destination);
        // TODO apply annotations
        return new TruncatingOutputSigner(baseOutputSigner);
    }

    const TruncatingOutputSigner(OutputSigner baseOutputSigner, Byte[]? verifierBytes = Null)
            delegates OutputSigner(baseOutputSigner)
            implements OutputVerifier {

        protected/private OutputSigner baseOutputSigner;
        protected/private Byte[]? verifierBytes;

        @Override
        Signature sign() {
            Signature baseSignature = baseOutputSigner.sign();
            return new Signature(name, baseSignature.bytes[0 ..< truncateTo]);
        }

        @Override
        Boolean signatureMatches() {
            return sign().bytes == verifierBytes? : assert as "Not a Verifier";
        }
    }

    // ----- Verifier interface --------------------------------------------------------------------

    @Override
    @RO Algorithm algorithm.get() = this;

    @Override
    @RO Int signatureSize.get() = truncateTo;

    @Override
    Boolean verify(Digest signature, InputStream in) {
        return verify(signature, in.readBytes(in.remaining));
    }

    @Override
    Boolean verify(Digest signature, Byte[] data) {
        Signature proof = sign(data);
        return signature.is(Signature)
                ? signature == proof
                : signature == proof.bytes;
    }

    @Override
    OutputVerifier createOutputVerifier(Digest        signature,
                                        BinaryOutput? destination = Null,
                                        Annotations?  annotations = Null) {
        OutputSigner baseOutputSigner = baseSigner.createOutputSigner(destination);
        // TODO apply annotations
        Byte[] signatureBytes = signature.is(Signature) ? signature.bytes : signature;
        return new TruncatingOutputSigner(baseOutputSigner, signatureBytes);
    }

    @Override
    InputVerifier createInputVerifier(BinaryInput  source,
                                      Annotations? annotations = Null) {
        TODO
    }
}