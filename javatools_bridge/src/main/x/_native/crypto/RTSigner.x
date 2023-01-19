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
    construct(Algorithm algorithm, CryptoKey publicKey, CryptoKey privateKey, Object cypher)
        {
        construct RTVerifier(algorithm, publicKey, cypher);

        assert privateKey.form == Secret;

        this.privateKey = privateKey;
        }


    // ----- Signer API ----------------------------------------------------------------------------

    @Override
    public/private CryptoKey privateKey;

    @Override
    Signature sign(Byte[] data)
        {
        return new Signature(algorithm.name, sign(cypher, privateKey, data));
        }

    @Override
    OutputSigner createOutputSigner(BinaryOutput? destination=Null,
                                    Annotations?  annotation=Null)
        {
        TODO
        }


    // ----- native helpers ------------------------------------------------------------------------

    private Byte[] sign(Object cypher, CryptoKey privateKey, Byte[] data) {TODO("Native");}
    }