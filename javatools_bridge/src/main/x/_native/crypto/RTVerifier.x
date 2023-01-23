import libcrypto.Algorithm;
import libcrypto.Annotations;
import libcrypto.CryptoKey;
import libcrypto.Signature;
import libcrypto.Verifier;

/**
 * The native [Verifier] implementation.
 */
service RTVerifier
        implements Verifier
    {
    construct(Algorithm algorithm, CryptoKey publicKey, Object signer)
        {
        assert publicKey.form == Public;

        this.algorithm = algorithm;
        this.publicKey = publicKey;
        this.signer    = signer;
        }

    /**
     * The native signature.
     */
    protected Object signer;


    // ----- Verifier API --------------------------------------------------------------------------

    @Override
    public/private Algorithm algorithm;

    @Override
    public/private CryptoKey publicKey;

    @Override
    Boolean verify(Digest signature, Byte[] data)
        {
        Byte[] signatureBytes;
        if (signature.is(Signature))
            {
            assert signature.algorithm == algorithm.name;
            signatureBytes = signature.bytes;
            }
        else
            {
            signatureBytes = signature;
            }
        return verify(signer, publicKey, signatureBytes, data);
        }

    @Override
    OutputVerifier createOutputVerifier(Digest        signature,
                                        BinaryOutput? destination=Null,
                                        Annotations?  annotation=Null)
        {
        TODO
        }

    @Override
    InputVerifier createInputVerifier(BinaryInput  source,
                                      Annotations? annotations=Null)
        {
        TODO
        }

    @Override
    void close(Exception? cause = Null)
        {
        }


    // ----- native helpers ------------------------------------------------------------------------

    private Boolean verify(Object signer, CryptoKey publicKey, Byte[] signature, Byte[] data) {TODO("Native");}
    }