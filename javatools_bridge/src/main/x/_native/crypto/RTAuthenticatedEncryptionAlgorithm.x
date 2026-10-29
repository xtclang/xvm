import libcrypto.Algorithm;
import libcrypto.Algorithm.Category;
import libcrypto.Algorithms;
import libcrypto.AuthenticatedDecryptor;
import libcrypto.CryptoKey;
import libcrypto.KeyForm;

/**
 * The native [AuthenticatedEncryption] [Algorithm] implementation.
 */
service RTAuthenticatedEncryptionAlgorithm(String name)
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
     * The supported key size(s) for this algorithm.
     */
    private Int|Int[] keySize;


    // ----- Algorithm API -------------------------------------------------------------------------

    @Override
    Category category.get() {
        return AuthenticatedEncryption;
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
    AuthenticatedDecryptor allocate(CryptoKey? key) {
        if (key == Null) {
            throw new IllegalArgument("Key is required");
        }

        assert key.form == Secret as $"Invalid key form for {this}";
        assert key.algorithm == "AES" as $"Invalid key algorithm for {this}";
        assert Algorithms.validSize(keySize, key.size) as $"Invalid key size for {this}";

        AuthenticatedDecryptor decryptor = new RTAuthenticatedDecryptor(name, key);
        return &decryptor.maskAs(AuthenticatedDecryptor);
    }

    @Override
    String toString() {
        return $"{name.quoted()} authenticated-encryption algorithm with {keySize} bytes key";
    }
}
