/**
 * A `KeyCredential` represents a secret key, such as an "API key".
 */
const KeyCredential(String key)
        extends Credential("key") {

    /**
     * Internal constructor for [with] method and subclasses.
     */
    protected construct(
            String  scheme,
            Boolean revoked,
            String  key,
            ) {
        construct Credential(scheme, revoked);
        this.key = key;
    }

    /**
     * Create a copy of this `KeyCredential`, but with specific attributes modified.
     *
     * @param scheme   the new [scheme] name, or pass `Null` to leave unchanged
     * @param revoked  the new value for the [revoked] flag, or pass `Null` to leave unchanged
     * @param key      the new [key] value, or pass `Null` to leave unchanged
     */
    @Override
    KeyCredential with(
            String?  scheme  = Null,
            Boolean? revoked = Null,
            String?  key     = Null,
            ) {
        return new KeyCredential(
                scheme  = scheme  ?: this.scheme,
                revoked = revoked ?: this.revoked,
                key     = key     ?: this.key,
                );
    }
}
