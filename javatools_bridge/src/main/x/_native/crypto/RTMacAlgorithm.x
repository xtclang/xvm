import libcrypto.Algorithm;
import libcrypto.Algorithm.Category;
import libcrypto.CryptoKey;
import libcrypto.PrivateKey;
import libcrypto.Signer;
import libcrypto.Verifier;

/**
 * The native [Signing] [Algorithm] implementation that requires a symmetric key.
 */
service RTMacAlgorithm(String name, Int|Int[] keySize)
        implements Algorithm {

    construct(String name, Int|Int[] keySize, Object mac) {
        this.name    = name;
        this.keySize = keySize;
        this.mac     = mac;
    }

    /**
     * The native signature.
     */
    private Object mac;

    // ----- Algorithm API -------------------------------------------------------------------------

    @Override
    Category category.get() {
        return Signing;
    }

    @Override
    conditional Int|Int[] keyRequired() {
        return True, keySize;
    }

    @Override
    Verifier|Signer allocate(CryptoKey? key) {
        if (key == Null) {
            throw new IllegalArgument("Key is required");
        }

        switch (key.form) {
        case Secret:
            assert key.is(PrivateKey) as "key must be a PrivateKey";

            Signer signer = new RTSigner(this, key, key, 0, mac);
            return &signer.maskAs(Signer);

        default:
            throw new IllegalArgument("a Secret key is required");
        }
    }

    @Override
    String toString() {
        return $"{name.quoted()} MAC algorithm with {keySize} bytes keys";
    }
}