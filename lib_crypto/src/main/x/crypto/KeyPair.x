/**
 * A `KeyPair` holds a public/private key pair.
 */
const KeyPair(String name, CryptoKey publicKey, CryptoKey privateKey)
        implements CryptoKey {

    assert() {
        assert:arg publicKey .form == Public;
        assert:arg privateKey.form == Secret;
        assert:arg publicKey.algorithm == privateKey.algorithm;
    }

    @Override
    String algorithm.get() = publicKey.algorithm;

    @Override
    @RO KeyForm form.get() = Pair;

    @Override
    @RO Int size.get() = publicKey.size;

    @Override
    conditional Byte[] isVisible() {
        if (Byte[] pubBytes := publicKey.isVisible()) {
            return True, pubBytes;
        }

        return False;
    }

    @Override
    String toString() = $"KeyPair({name.quoted()}, {publicKey}/{privateKey})";
}