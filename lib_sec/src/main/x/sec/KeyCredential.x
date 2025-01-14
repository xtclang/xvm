import crypto.Signer;

/**
 * A `KeyCredential` represents a secret key, such as an "API key".
 */
const KeyCredential
        extends Credential(Scheme) {

    /**
     * "ak" == API Key
     */
    static String Scheme = "ak";

    construct(String realmName, String key) {
        construct Credential(Scheme);

        this.key_sha256 = createLocator(realmName, key);
    }

    /**
     * Internal constructor for [with] method and subclasses.
     */
    protected construct(
            String  scheme,
            Time?   validFrom,
            Time?   validUntil,
            Status? status,
            String  key_sha256,
            ) {
        construct Credential(scheme, validFrom, validUntil, status);
        this.key_sha256 = key_sha256;
    }

    /**
     * Create a copy of this `KeyCredential`, but with specific attributes modified.
     *
     * @param scheme      the new `scheme` name, or pass `Null` to leave unchanged
     * @param validFrom   the new `validFrom` value to use, or `Null` to leave unchanged
     * @param validUntil  the new `validUntil` value to use, or `Null` to leave unchanged
     * @param status      the new `status` value to use, or `Null` to leave unchanged; the only
     *                    legal [Status] values to pass are `Active`, `Suspended`, and `Revoked`;
     *                    passing `Active` will result in the [status] of `Null`
     * @param key_sha256  the new [key_sha256] value, or `Null` to leave unchanged
     */
    @Override
    KeyCredential with(
            String? scheme     = Null,
            Time?   validFrom  = Null,
            Time?   validUntil = Null,
            Status? status     = Null,
            String? key_sha256 = Null,
            ) {
        return new KeyCredential(
                scheme     = scheme     ?: this.scheme,
                validFrom  = validFrom  ?: this.validFrom,
                validUntil = validUntil ?: this.validUntil,
                status     = status     ?: this.status,
                key_sha256 = key_sha256 ?: this.key_sha256,
                );
    }

    // ----- properties ----------------------------------------------------------------------------

    /**
     * The hashed key.
     */
    String key_sha256;

    @Override
    String[] locators.get() = [key_sha256];

    // ----- internal ------------------------------------------------------------------------------

    import convert.formats.Base64Format;
    static String createLocator(String realmName, String key) =
            Base64Format.Instance.encode(sha256.sign($"{realmName}:{key}".utf8()).bytes);

    static Signer sha256 = {
        @Inject crypto.Algorithms algorithms;
        return algorithms.hasherFor("SHA-256") ?: assert as "SHA-256 Signer required";
    };
}
