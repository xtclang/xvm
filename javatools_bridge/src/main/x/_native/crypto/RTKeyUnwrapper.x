import libcrypto.CryptoKey;
import libcrypto.KeyUnwrapper;
import libcrypto.PrivateKey;
import libcrypto.WrappedKey;

/**
 * The native [KeyUnwrapper] implementation.
 */
service RTKeyUnwrapper(String algorithm)
        implements KeyUnwrapper {

    construct(String algorithm, CryptoKey wrappingKey) {
        this.algorithm   = algorithm;
        this.wrappingKey = wrappingKey;
    }

    @Override
    CryptoKey wrappingKey;

    @Override
    WrappedKey wrap(CryptoKey key) {
        if (key.form != Secret) {
            throw new IllegalArgument("Only secret keys can be wrapped");
        }
        if (key.algorithm.empty || key.size <= 0) {
            throw new IllegalArgument("The key algorithm and size are required");
        }
        if (algorithm == "AES/KW/NoPadding" && (key.size < 16 || key.size % 8 != 0)) {
            throw new IllegalArgument(
                "AES-KW requires a key size of at least 16 bytes and a multiple of 8 bytes");
        }

        if (Object wrappingSecret := RTKeyStore.extractSecret(wrappingKey),
                Object keySecret := RTKeyStore.extractSecret(key)) {
            Byte[] bytes = wrap(algorithm, wrappingSecret, key.algorithm, keySecret);
            return new WrappedKey(algorithm, bytes);
        }

        throw new IllegalState("Unsupported key");
    }

    @Override
    conditional CryptoKey unwrap(
            WrappedKey wrapped, String keyName, String keyAlgorithm) {
        if (wrapped.algorithm != algorithm || keyAlgorithm.empty) {
            return False;
        }

        if (Object wrappingSecret := RTKeyStore.extractSecret(wrappingKey),
                (Int keySize, Object keySecret) :=
                    unwrap(algorithm, wrappingSecret, wrapped.bytes, keyAlgorithm)) {
            CryptoKey key = new RTPrivateKey(keyName, keyAlgorithm, keySize, keySecret);
            return True, &key.maskAs(PrivateKey);
        }

        return False;
    }

    @Override
    void close(Exception? cause = Null) {}

    @Override
    String toString() {
        return $"{algorithm.quoted()} key unwrapper";
    }


    // ----- native helpers ------------------------------------------------------------------------

    protected Byte[] wrap(
        String algorithm, Object wrappingSecret, String keyAlgorithm, Object keySecret) {
        TODO("Native");
    }

    protected conditional (Int keySize, Object keySecret) unwrap(
        String algorithm, Object wrappingSecret, Byte[] wrapped, String keyAlgorithm) {
        TODO("Native");
    }
}
