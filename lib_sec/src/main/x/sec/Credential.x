/**
 * A `Credential` represents a means of authentication.
 */
@Abstract const Credential {

    // ----- construction --------------------------------------------------------------------------

    /**
     * Construct a `Credential`.
     *
     * @param scheme   identifies the form of authentication (and potentially additional specifics)
     *                 that this credential is intended to be used with; for example, "password"
     *                 might indicate a plain text password, while "digest:SHA-512-256" might
     *                 indicate a digest of a password using a specific hash function
     * @param revoked  `True` iff the credentials themselves have been revoked, for example if they
     *                 are known to have been compromised
     */
    construct(
            String  scheme,
            Boolean revoked = False,
            ) {
        this.scheme  = scheme;
        this.revoked = revoked;
    }

    /**
     * Create a copy of this `Credential`, but with specific attributes modified.
     *
     * @param scheme   the new [scheme] name, or pass `Null` to leave unchanged
     * @param revoked  the new value for the [revoked] flag, or pass `Null` to leave unchanged
     */
    @Abstract Credential with(
            String?  scheme  = Null,
            Boolean? revoked = Null,
            );

    /**
     * The form of authentication that this credential is intended to be used with, which may
     * contain specific parameters of that authentication mechanism. For example, "password" may
     * indicates a plain text password, while "digest:SHA-512-256" may indicate a digest of a
     * password using a specific hash function.
     */
    String scheme;

    /**
     * Tracks whether the credentials themselves have been revoked, for example if the secrecy of
     * the credentials may have been compromised.
     */
    Boolean revoked;

    /**
     * Create a revoked copy of these credentials.
     *
     * @return a copy of this
     */
    Credential revoke() = revoked ? this : with(revoked=False);
}