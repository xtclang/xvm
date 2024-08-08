/**
 * A `Credential` represents a means of authentication.
 */
@Abstract const Credential {

    /**
     * Construct a `Credential`.
     *
     * @param scheme  identifies the form of authentication (and potentially additional specifics)
     *                that this credential is intended to be used with; for example, "password"
     *                might indicate a plain text password, while "digest:SHA-512-256" might
     *                indicate a digest of a password using a specific hash function
     */
    construct(String scheme) {
        this.scheme = scheme;
    }

    /**
     * The form of authentication that this credential is intended to be used with, which may
     * contain specific parameters of that authentication mechanism. For example, "password" may
     * indicates a plain text password, while "digest:SHA-512-256" may indicate a digest of a
     * password using a specific hash function.
     */
    String scheme;
}