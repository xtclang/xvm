import libcrypto.Algorithm;
import libcrypto.Annotations;
import libcrypto.CryptoKey;
import libcrypto.Signature;
import libcrypto.Verifier;

/**
 * The native [Verifier] implementation.
 */
service RTVerifier
        implements Verifier {

    construct(Algorithm algorithm, CryptoKey publicKey, Int signatureSize, Object signer) {
        assert publicKey.form == Public;

        this.algorithm     = algorithm;
        this.publicKey     = publicKey;
        this.signatureSize = signatureSize;
        this.signer        = signer;
    }

    /**
     * The native signature.
     */
    protected Object signer;


    // ----- Verifier API --------------------------------------------------------------------------

    @Override
    public/private Algorithm algorithm;

    @Override
    public/private CryptoKey publicKey;

    @Override
    Boolean verify(Digest signature, Byte[] data) {
        Object secret;
        if (!(secret := RTKeyStore.extractSecret(publicKey))) {
            throw new IllegalState($"Unsupported key {publicKey}");
        }

        Byte[] signatureBytes;
        if (signature.is(Signature)) {
            assert signature.algorithm == algorithm.name;
            signatureBytes = signature.bytes;
        } else {
            signatureBytes = signature;
        }
        return verify(signer, secret, signatureBytes, data);
    }

    @Override
    OutputVerifier createOutputVerifier(Digest        signature,
                                        BinaryOutput? destination = Null,
                                        Annotations?  annotations = Null) {
        TODO
    }

    @Override
    InputVerifier createInputVerifier(BinaryInput  source,
                                      Annotations? annotations = Null) {
        TODO
    }

    @Override
    void close(Exception? cause = Null) {}

    @Override
    String toString() {
        return $"{algorithm.name.quoted()} verifier for {publicKey}";
    }


    // ----- native helpers ------------------------------------------------------------------------

    protected Boolean verify(Object signer, Object secret, Byte[] signature, Byte[] data) {TODO("Native");}
}