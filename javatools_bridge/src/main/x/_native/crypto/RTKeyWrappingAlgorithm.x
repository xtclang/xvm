import libcrypto.Algorithm;
import libcrypto.Algorithm.Category;
import libcrypto.Algorithms;
import libcrypto.CryptoKey;
import libcrypto.KeyUnwrapper;
import libcrypto.KeyForm;

/**
 * The native [KeyWrapping] [Algorithm] implementation.
 */
service RTKeyWrappingAlgorithm(String name)
        implements Algorithm {

    construct(String name, Int blockSize, Int|Int[] keySize) {
        this.name       = name;
        this.blockSize_ = blockSize;
        this.keySize    = keySize;
    }

    /**
     * The block size reported by the native provider.
     */
    private Int blockSize_;

    /**
     * The supported key-encryption-key size(s).
     */
    private Int|Int[] keySize;


    // ----- Algorithm API -------------------------------------------------------------------------

    @Override
    Category category.get() {
        return KeyWrapping;
    }

    @Override
    Int blockSize.get() {
        return blockSize_;
    }

    @Override
    conditional Int|Int[] keyRequired() {
        return True, keySize;
    }

    @Override
    KeyUnwrapper allocate(CryptoKey? key) {
        if (key == Null) {
            throw new IllegalArgument("Key is required");
        }

        assert key.form == Secret as $"Invalid key form for {this}";
        assert key.algorithm == "AES" as $"Invalid key algorithm for {this}";
        assert Algorithms.validSize(keySize, key.size) as $"Invalid key size for {this}";

        KeyUnwrapper unwrapper = new RTKeyUnwrapper(name, key);
        return &unwrapper.maskAs(KeyUnwrapper);
    }

    @Override
    String toString() {
        return $"{name.quoted()} key-wrapping algorithm with {keySize} bytes key";
    }
}
