/**
 * A `KeyPair` holds a public/private key pair.
 */
const KeyPair(String name, CryptoKey publicKey, CryptoKey privateKey)
        implements CryptoKey
    {
    assert()
        {
        assert:arg publicKey .keyType == Public;
        assert:arg privateKey.keyType == Secret;
        }

    @Override
    @RO KeyForm keyType.get()
        {
        return Pair;
        }

    @Override
    @RO Int size.get()
        {
        // REVIEW
        return privateKey.size + publicKey.size;
        }

    @Override
    conditional Byte[] isVisible()
        {
        if (Byte[] priBytes := privateKey.isVisible(),
            Byte[] pubBytes := publicKey.isVisible())
            {
            return True, priBytes + pubBytes; // REVIEW CP
            }

        return False;
        }
    }
