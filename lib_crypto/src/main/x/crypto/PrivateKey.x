/**
 * A `PrivateKey` holds a symmetric (shared private) key, or the private portion of a public/private
 * key.
 */
const PrivateKey(String name, Byte[] bytes)
        implements CryptoKey
    {
    @Override
    @RO KeyForm keyType.get()
        {
        return Secret;
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
