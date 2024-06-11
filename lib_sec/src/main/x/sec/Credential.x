/**
 * A `Credential` represents a means of authentication.
 */
@Abstract const Credential {

    /**
     * Construct a `Credential`.
     *
     * @param form  identifies the form of authentication (and potentially additional specifics)
     *              that this credential is intended to be used with; for example, "password"
     *              indicates a plain text password, while "digest:SHA-512-256" indicates a digest
     *              of a password using a specific hash function
     */
    construct(String form) {
        this.form = form;
    }

    /**
     * The form of authentication that this credential is intended to be used with, which may
     * contain specific parameters of that authentication mechanism. For example, "password" may
     * indicates a plain text password, while "digest:SHA-512-256" may indicate a digest of a
     * password using a specific hash function.
     */
    String form;
}