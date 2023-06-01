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
        Object secret;

        if (RTCryptoKey key := &privateKey.revealAs(RTCryptoKey)) {
            secret = key.secret;
        } else if (Byte[] rawKey := privateKey.isVisible()) {
            secret = rawKey;
        } else {
            throw new IllegalState($"Unsupported key {privateKey}");
        }
        return new Signature(algorithm.name, sign(signer, secret, data));
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