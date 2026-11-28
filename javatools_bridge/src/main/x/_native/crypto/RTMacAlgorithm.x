import libcrypto.Algorithm;
import libcrypto.Algorithm.Category;
import libcrypto.CryptoKey;
import libcrypto.PrivateKey;
import libcrypto.Signer;
import libcrypto.Verifier;

/**
 * The native [Signing] [Algorithm] implementation that requires a symmetric key.
 */
service RTMacAlgorithm(String name, Object mac)
        implements Algorithm {

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
        return True, Algorithm.VariableKeySize;
    }

    @Override
    Verifier|Signer allocate(CryptoKey? key) {
        if (key == Null) {
            throw new IllegalArgument("Key is required");
        }

        switch (key.form) {
        case Secret:
            assert key.is(PrivateKey) as "key must be a PrivateKey";
            assert:arg key.size > 0 as "a non-empty key is required";

            Signer signer = new RTSigner(this, key, key, 0, mac);
            return &signer.maskAs(Signer);

        default:
            throw new IllegalArgument("a Secret key is required");
        }
    }

    @Override
    String toString() {
        return $"{name.quoted()} MAC algorithm with variable-length keys";
    }
}
