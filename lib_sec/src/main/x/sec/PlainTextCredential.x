/**
 * A `PlainTextCredential` represents a user name and password.
 */
const PlainTextCredential(String name, String password)
        extends Credential("pwd") {

    /**
     * Internal constructor for [with] method and subclasses.
     */
    protected construct(
            String  scheme,
            Boolean revoked,
            String  name,
            String  password,
            ) {
        construct Credential(scheme, revoked);
        this.name     = name;
        this.password = password;
    }

    /**
     * Create a copy of this `KeyCredential`, but with specific attributes modified.
     *
     * @param scheme    the new [scheme] name, or pass `Null` to leave unchanged
     * @param revoked   the new value for the [revoked] flag, or pass `Null` to leave unchanged
     * @param name      the new [name] value, or pass `Null` to leave unchanged
     * @param password  the new [password] value, or pass `Null` to leave unchanged
     */
    @Override
    PlainTextCredential with(
            String?  scheme   = Null,
            Boolean? revoked  = Null,
            String?  name     = Null,
            String?  password = Null,
            ) {
        return new PlainTextCredential(
                scheme   = scheme   ?: this.scheme,
                name     = name     ?: this.name,
                password = password ?: this.password,
                revoked  = revoked  ?: this.revoked,
                );
    }
}
