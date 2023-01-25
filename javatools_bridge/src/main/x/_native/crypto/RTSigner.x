import libcrypto.Algorithm;
import libcrypto.Annotations;
import libcrypto.CryptoKey;
import libcrypto.Signature;
import libcrypto.Signer;

/**
 * The native [Signer] implementation.
 */
service RTSigner
        extends RTVerifier
        implements Signer
    {
    construct(Algorithm algorithm, CryptoKey publicKey, CryptoKey privateKey, Int signatureSize, Object signer)
        {
        construct RTVerifier(algorithm, publicKey, signer);

        assert privateKey.form == Secret;

        this.privateKey    = privateKey;
        this.signatureSize = signatureSize;
        }


    // ----- Signer API ----------------------------------------------------------------------------

    @Override
    public/private CryptoKey privateKey;

    @Override
    public/private Int signatureSize;

    @Override
    Signature sign(Byte[] data)
        {
        CryptoKey privateKey = this.privateKey;
        Object    secret;

        if (privateKey.is(RTPrivateKey))
            {
            secret = privateKey.secret;
            }
        else if (Byte[] bytes := privateKey.isVisible())
            {
            secret = bytes;
            }
        else
            {
            throw new IllegalState($"Unsupported key {privateKey}");
            }
        return new Signature(algorithm.name, sign(signer, secret, data));
        }

    @Override
    OutputSigner createOutputSigner(BinaryOutput? destination=Null,
                                    Annotations?  annotation=Null)
        {
        TODO
        }

    @Override
    String toString()
        {
        return $"{algorithm.name.quoted()} signer";
        }


    // ----- native helpers ------------------------------------------------------------------------

    private Byte[] sign(Object signer, Object privateKey, Byte[] data) {TODO("Native");}
    }