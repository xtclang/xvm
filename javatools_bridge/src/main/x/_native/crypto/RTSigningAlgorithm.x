import libcrypto.Algorithms;
import libcrypto.Algorithm;
import libcrypto.Algorithm.Category;
import libcrypto.CryptoKey;
import libcrypto.KeyForm;
import libcrypto.KeyPair;
import libcrypto.Signer;
import libcrypto.Verifier;

/**
 * The native [Signing] [Algorithm] implementation that requires a key.
 */
service RTSigningAlgorithm(String name, Int blockSize, Int signatureSize)
        implements Algorithm {

    construct(String name, Int blockSize, Int|Int[] keySize, Int signatureSize, Object signer) {
        this.name          = name;
        this.blockSize     = blockSize;
        this.keySize       = keySize;
        this.signatureSize = signatureSize;
        this.signer        = signer;
    }

    /**
     * The supported key size(s) for this algorithm.
     */
    private Int|Int[] keySize;

    /**
     * The native signature.
     */
    private Object signer;


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
        case Public:
            // verifier
            assert Algorithms.validSize(keySize, key.size) as $"Invalid key size for {this}";
            Verifier verifier = new RTVerifier(this, key, signatureSize, signer);
            return &verifier.maskAs(Verifier);

        case Pair:
            assert key.is(KeyPair) as "Key must be a KeyPair";
            assert Algorithms.validSize(keySize, key.privateKey.size) as $"Invalid key size for {this}";

            Signer signer = new RTSigner(this, key.publicKey, key.privateKey, signatureSize, signer);
            return &signer.maskAs(Signer);

        case Secret:
            throw new IllegalArgument("a KeyPair is required");
        }
    }

    @Override
    String toString() {
        return $|{name.quoted()} signing algorithm with {keySize} bytes key and \
                |{signatureSize} bytes signature
                ;
    }
}