import libcrypto.Algorithms;
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
    construct(String name, Int blockSize, KeyForm? form, Int|Int[] keySize, Object cipher)
        {
        this.name      = name;
        this.blockSize = blockSize;
        this.keyForm   = form;
        this.keySize   = keySize;
        this.cipher    = cipher;
        }

    /**
     * The KeyForm; Null if the key is not required by this Algorithm.
     */
    private KeyForm? keyForm;

    /**
     * The supported key size(s) for this algorithm.
     */
    private Int|Int[] keySize;

    /**
     * The native cipher or signature.
     */
    private Object cipher;


    // ----- Algorithm API -------------------------------------------------------------------------

    @Override
    @RO Category category.get()
        {
        return Signing;
        }

    @Override
    conditional (KeyForm form, Int|Int[] keySize) keyRequired()
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
        if ((KeyForm form, Int|Int[] keySize) := keyRequired())
            {
            if (key == Null)
                {
                throw new IllegalArgument("Key is required");
                }

            assert key.form == form && Algorithms.validSize(keySize, key.size)
                    as $"Invalid key for {this}";

           switch (form)
                {
                case Public:
                    return new RTVerifier(this, key, cipher);

                case Pair:
                    assert key.is(KeyPair);
                    return new RTSigner(this, key.publicKey, key.privateKey, cipher);

                case Secret:
                    assert as "a key pair is required";
                }
            }
        TODO transformer, no key is necessary
        }

    @Override
    String toString()
        {
        if ((KeyForm form, Int|Int[] keySize) := keyRequired())
            {
            return $"{name} with {form} of {keySize} bytes";
            }
        return $"{name}";
        }
    }