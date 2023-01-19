import libcrypto.Algorithm;
import libcrypto.CryptoKey;
import libcrypto.KeyForm;
import libcrypto.KeyPair;
import libcrypto.Signer;
import libcrypto.Verifier;

/**
 * The native "Signing" [Algorithm] implementation.
 */
const RTSigningAlgorithm(String name, Int blockSize, Int signatureSize)
        implements Algorithm
    {
    construct(String name, Int blockSize, Int signatureSize, Int formId, Int keySize, Object cypher)
        {
        this.name          = name;
        this.blockSize     = blockSize;
        this.signatureSize = signatureSize;
        this.keyForm       = formId < 0 ? Null : KeyForm.values[formId];
        this.keySize       = keySize;
        this.cypher        = cypher;
        }

    /**
     * The KeyForm; Null if the key is not required by this Algorithm.
     */
    private KeyForm? keyForm;

    /**
     * The number of bytes in the key for this algorithm, zero if the key is not required.
     */
    private Int keySize;

    /**
     * The native cipher or signature.
     */
    private Object cypher;


    // ----- Algorithm API -------------------------------------------------------------------------

    @Override
    @RO Category category.get()
        {
        return Signing;
        }

    @Override
    conditional (KeyForm form, Int size) keyRequired()
        {
        if (KeyForm form ?= keyForm)
            {
            return True, form, keySize;
            }
        return False;
        }

    @Override
    Verifier|Signer allocate(CryptoKey? key)
        {
        if ((KeyForm form, Int size) := keyRequired())
            {
            if (key == Null)
                {
                throw new IllegalArgument("Key is required");
                }

            assert key.form == form && key.size == size
                    as $"Invalid key for {this}";

           switch (form)
                {
                case Public:
                    return new RTVerifier(this, key, cypher);

                case Pair:
                    assert key.is(KeyPair);
                    return new RTSigner(this, key.publicKey, key.privateKey, cypher);

                case Secret:
                    assert as "a key pair is required";
                }
            }
        TODO transformer, no key is necessary
        }

    @Override
    String toString()
        {
        if ((KeyForm form, Int size) := keyRequired())
            {
            return $"{name} with {form} of {size} bytes";
            }
        return $"{name}";
        }
    }