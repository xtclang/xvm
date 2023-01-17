/**
 * A `PrivateKey` holds a symmetric (shared private) key, or the private portion of a public/private
 * key.
 */
const PrivateKey(String name, String algorithm, Int size, Byte[] bytes)
        implements CryptoKey
    {
    @Override
    @RO KeyForm form.get()
        {
        return Secret;
        }

    @Override
    conditional Byte[] isVisible()
        {
        return True, bytes;
        }

    @Override
    String toString()
        {
        return $"{algorithm} private key, {size*8} bits";
        }
    }