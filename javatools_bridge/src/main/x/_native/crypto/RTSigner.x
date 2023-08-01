import libcrypto.Algorithm;
import libcrypto.Annotations;
import libcrypto.CryptoKey;
import libcrypto.Signature;
import libcrypto.Signer;

/**
 * The native [Signer] implementation.
 */
service RTSigner
        extends RTVerifier
        implements Signer {

    construct(Algorithm algorithm, CryptoKey publicKey, CryptoKey privateKey, Int signatureSize, Object signer) {
        construct RTVerifier(algorithm, publicKey, signatureSize, signer);

        assert privateKey.form == Secret;

        this.privateKey = privateKey;
    }


    // ----- Signer API ----------------------------------------------------------------------------

    @Override
    public/private CryptoKey privateKey;

    @Override
    Signature sign(Byte[] data) {
        if (Object secret := RTKeyStore.extractSecret(privateKey)) {
            return new Signature(algorithm.name, sign(signer, secret, data));
        }

        throw new IllegalState($"Unsupported key {privateKey}");
    }

    @Override
    OutputSigner createOutputSigner(BinaryOutput? destination=Null,
                                    Annotations?  annotations=Null) {
        TODO
    }

    @Override
    String toString() {
        return $"{algorithm.name.quoted()} signer for {privateKey}";
    }


    // ----- native helpers ------------------------------------------------------------------------

    protected Byte[] sign(Object signer, Object secret, Byte[] data) {TODO("Native");}
}