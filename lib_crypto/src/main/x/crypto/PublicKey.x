/**
 * A `PublicKey` holds the public portion of a public/private key.
 */
const PublicKey(String name, String algorithm, Int size, Byte[] bytes)
        implements CryptoKey
    {
    @Override
    @RO KeyForm form.get()
        {
        return Public;
        }

    @Override
    conditional Byte[] isVisible()
        {
        return True, bytes;
        }

    @Override
    String toString()
        {
        return $"PublicKey({name.quoted()}, {algorithm}, {size*8} bits)";
        }
    }