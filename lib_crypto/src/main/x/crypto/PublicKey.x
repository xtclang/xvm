/**
 * A `PublicKey` holds the public portion of a public/private key.
 */
const PublicKey(String name, Byte[] bytes)
        implements CryptoKey
    {
    @Override
    @RO KeyForm keyType.get()
        {
        return Public;
        }

    @Override
    @RO Int size.get()
        {
        return bytes.size;
        }

    @Override
    conditional Byte[] isVisible()
        {
        return True, bytes;
        }
    }
