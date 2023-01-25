import libcrypto.Algorithm;
import libcrypto.Algorithm.Category;
import libcrypto.CryptoKey;
import libcrypto.KeyGenerator;
import libcrypto.PrivateKey;

/**
 * The native `KeyGenerator` [Algorithm] implementation.
 */
service RTKeyGenerator(String name, Int seedSize)
        implements Algorithm, KeyGenerator
    {
    construct(String name, Int seedSize, Object factory)
        {
        this.name     = name;
        this.seedSize = seedSize;
        this.factory  = factory;
        }

    /**
     * The native factory.
     */
    private Object factory;


    // ----- Algorithm API -------------------------------------------------------------------------

    @Override
    Category category.get()
        {
        return KeyGeneration;
        }

    @Override
    conditional Int|Int[] keyRequired()
        {
        return False;
        }

    @Override
    KeyGenerator allocate(CryptoKey? key)
        {
        if (key != Null)
            {
            throw new IllegalArgument("Key is not used");
            }

        return &this.maskAs(KeyGenerator);
        }


    // ----- KeyGenerator API ----------------------------------------------------------------------

    @Override
    @RO Algorithm algorithm.get()
        {
        return &this.maskAs(Algorithm);
        }

    @Override
    CryptoKey generateSecretKey(String name)
        {
        (Int keySize, Object secret) = generateSecret(factory, seedSize );

        CryptoKey key = new RTPrivateKey(name, this.name, keySize, secret);
        return &key.maskAs(PrivateKey);
        }


    // ----- Object API ----------------------------------------------------------------------------

    @Override
    String toString()
        {
        return $"{name.quoted()} key generator";
        }


    // ----- native methods ------------------------------------------------------------------------

    private (Int keySize, Object secret) generateSecret(Object factory, Int seedSize) {TODO("Native");}
    }