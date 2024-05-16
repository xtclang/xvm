import libcrypto.Algorithm;
import libcrypto.Algorithm.Category;
import libcrypto.Annotations;
import libcrypto.CryptoKey;
import libcrypto.Signature;
import libcrypto.Signer;
import libcrypto.Verifier;

/**
 * The native [Encryption] [Algorithm] implementation that doesn't require a key (also known as
 * a "hasher).
 */
service RTHasher(String name, Int signatureSize)
        implements Algorithm, Signer {

    construct(String name, Int signatureSize, Object hasher) {
        this.name          = name;
        this.signatureSize = signatureSize;
        this.hasher        = hasher;
    }

    /**
     * The native hasher.
     */
    private Object hasher;


    // ----- Algorithm API -------------------------------------------------------------------------

    @Override
    Category category.get() {
        return Signing;
    }

    @Override
    conditional Int|Int[] keyRequired() {
        return False;
    }

    @Override
    Verifier|Signer allocate(CryptoKey? key) {
        if (key != Null) {
            throw new IllegalArgument("Key is not used");
        }

        return &this.maskAs(Signer);
    }


    // ----- Signer API ----------------------------------------------------------------------------

    @Override
    CryptoKey? privateKey.get() {
        return Null;
    }

    @Override
    Signature sign(Byte[] data) {
        return new Signature(algorithm.name, digest(hasher, data));
    }

    @Override
    OutputSigner createOutputSigner(BinaryOutput? destination = Null,
                                    Annotations?  annotations = Null) {
        TODO
    }


    // ----- Verifier API --------------------------------------------------------------------------

    @Override
    @RO Algorithm algorithm.get() {
        return &this.maskAs(Algorithm);
    }

    @Override
    @RO CryptoKey? publicKey.get() {
        return Null;
    }

    @Override
    Boolean verify(Digest signature, Byte[] data) {
        Byte[] bytes = signature.is(Signature) ? signature.bytes : signature;
        return bytes == digest(hasher, data);
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


    // ----- Object API ----------------------------------------------------------------------------

    @Override
    String toString() {
        return $"{name.quoted()} algorithm with {signatureSize} bytes digest";
    }

    // ----- native methods ------------------------------------------------------------------------

    private Byte[] digest(Object digest, Byte[] data) {TODO("Native");}
}