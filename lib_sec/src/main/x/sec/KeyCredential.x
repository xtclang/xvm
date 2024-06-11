/**
 * A `KeyCredential` represents a secret key, such as an "API key".
 */
const KeyCredential(String key)
        extends Credential(Scheme) {

    /**
     * "ak" == API Key
     */
    static String Scheme = "ak";

    /**
     * Internal constructor for [with] method and subclasses.
     */
    protected construct(
            String  scheme,
            Time?   validFrom,
            Time?   validUntil,
            Status? status,
            String  key,
            ) {
        construct Credential(scheme, validFrom, validUntil, status);
        this.key = key;
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
     * @param key         the new [key] value, or pass `Null` to leave unchanged
     */
    @Override
    KeyCredential with(
            String? scheme     = Null,
            Time?   validFrom  = Null,
            Time?   validUntil = Null,
            Status? status     = Null,
            String? key        = Null,
            ) {
        return new KeyCredential(
                scheme     = scheme     ?: this.scheme,
                validFrom  = validFrom  ?: this.validFrom,
                validUntil = validUntil ?: this.validUntil,
                status     = status     ?: this.status,
                key        = key        ?: this.key,
                );
    }

    @Override
    String[] locators.get() = [key];
}
