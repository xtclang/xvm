/**
 * An `OpaqueKey` is used when a [CryptoKey] needs to be injected into a container without allowing
 * the key material (the raw bytes) to be visible within that container.
 */
const OpaqueKey(CryptoKey actualKey)
        delegates CryptoKey(actualKey)
    {
    @Override
    conditional Byte[] isVisible()
        {
        return False;
        }
    }
