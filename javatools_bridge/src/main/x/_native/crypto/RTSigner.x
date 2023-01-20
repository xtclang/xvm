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
    construct(Algorithm algorithm, CryptoKey publicKey, CryptoKey privateKey, Object cipher)
        {
        construct RTVerifier(algorithm, publicKey, cipher);

        assert privateKey.form == Secret;

        this.privateKey = privateKey;
        }


    // ----- Signer API ----------------------------------------------------------------------------

    @Override
    public/private CryptoKey privateKey;

    @Override
    public/private Int signatureSize;

    @Override
    Signature sign(Byte[] data)
        {
        return new Signature(algorithm.name, sign(cipher, privateKey, data));
        }

    @Override
    OutputSigner createOutputSigner(BinaryOutput? destination=Null,
                                    Annotations?  annotation=Null)
        {
        TODO
        }


    // ----- native helpers ------------------------------------------------------------------------

    private Byte[] sign(Object cipher, CryptoKey privateKey, Byte[] data) {TODO("Native");}
    }