import sec.Credential;

/**
 * A `OAuthCredential` represents a [Credential] obtained via an OAuth 2.0 identity provider.
 *
 * @param provider  the OAuth 2.0 provider name
 * @param name      the human readable name for this `Credential`
 * @param email     the email associated with this `Credential`
 */
const OAuthCredential(String provider, String name, String email)
        extends Credential(Scheme) {
    /**
     * "oa" == OAuth
     */
    static String Scheme = "oa";

    /**
     * Internal constructor for [with] method and subclasses.
     */
    protected construct(
            String  scheme,
            Time?   validFrom,
            Time?   validUntil,
            Status? status,
            String  provider,
            String  name,
            String  email,
            ) {
        construct Credential(scheme, validFrom, validUntil, status);
        this.provider = provider;
        this.name     = name;
        this.email    = email;
    }

    /**
     * Create a copy of this `OAuthCredential`, but with specific attributes modified.
     *
     * @param scheme      the new `scheme` name, or pass `Null` to leave unchanged
     * @param validFrom   the new `validFrom` value to use, or `Null` to leave unchanged
     * @param validUntil  the new `validUntil` value to use, or `Null` to leave unchanged
     * @param status      the new `status` value to use, or `Null` to leave unchanged; the only
     *                    legal [Status] values to pass are `Active`, `Suspended`, and `Revoked`;
     *                    passing `Active` will result in the [status] of `Null`
     * @param name        the human readable name for this `Credential`
     * @param email       the email associated with this `Credential`
     */
    @Override
    OAuthCredential with(
            String? scheme     = Null,
            Time?   validFrom  = Null,
            Time?   validUntil = Null,
            Status? status     = Null,
            String? provider   = Null,
            String? name       = Null,
            String? email      = Null,
            ) {
        return new OAuthCredential(
                scheme     = scheme     ?: this.scheme,
                validFrom  = validFrom  ?: this.validFrom,
                validUntil = validUntil ?: this.validUntil,
                status     = status     ?: this.status,
                provider   = provider   ?: this.provider,
                name       = name       ?: this.name,
                email      = email      ?: this.email,
                );
    }

    @Override
    String[] locators.get() = [email];

    @Override
    conditional String contains(Form form) {
        return switch (form) {
            case Name:  (True, name);
            case Email: (True, email);
            default:    False;
        };
    }
}