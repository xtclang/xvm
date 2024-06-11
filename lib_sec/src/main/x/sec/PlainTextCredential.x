/**
 * A `PlainTextCredential` represents a user name and password.
 */
const PlainTextCredential(String name, String password)
        extends Credential(Scheme) {

    /**
     * "pt" == Plain Text
     */
    static String Scheme = "pt";

    /**
     * Internal constructor for [with] method and subclasses.
     */
    protected construct(
            String  scheme,
            Time?   validFrom,
            Time?   validUntil,
            Status? status,
            String  name,
            String  password,
            ) {
        construct Credential(scheme, validFrom, validUntil, status);
        this.name     = name;
        this.password = password;
    }

    /**
     * Create a copy of this `PlainTextCredential`, but with specific attributes modified.
     *
     * @param scheme      the new `scheme` name, or pass `Null` to leave unchanged
     * @param validFrom   the new `validFrom` value to use, or `Null` to leave unchanged
     * @param validUntil  the new `validUntil` value to use, or `Null` to leave unchanged
     * @param status      the new `status` value to use, or `Null` to leave unchanged; the only
     *                    legal [Status] values to pass are `Active`, `Suspended`, and `Revoked`;
     *                    passing `Active` will result in the [status] of `Null`
     * @param name        the new [name] value, or pass `Null` to leave unchanged
     * @param password    the new [password] value, or pass `Null` to leave unchanged
     */
    @Override
    PlainTextCredential with(
            String? scheme     = Null,
            Time?   validFrom  = Null,
            Time?   validUntil = Null,
            Status? status     = Null,
            String? name       = Null,
            String? password   = Null,
            ) {
        return new PlainTextCredential(
                scheme     = scheme     ?: this.scheme,
                validFrom  = validFrom  ?: this.validFrom,
                validUntil = validUntil ?: this.validUntil,
                status     = status     ?: this.status,
                name       = name       ?: this.name,
                password   = password   ?: this.password,
                );
    }

    @Override
    String[] locators.get() = [name];
}
