/**
 * An Authority is a representation of _trust_. For example, when a `Certificate` is used to verify
 * a signature, who verifies the `Certificate`? Another `Certificate` (the next `Certificate` in the
 * "certificate chain") is used, and so on, but what about the top certificate in the chain, which
 * is self-signed and comes from a "CA" (Certificate Authority)? This interface represents the
 * answer to that question.
 *
 * Normally, the authority is delegated to the conceptual "host". On both the client and the server,
 * the host is _usually_ the operating system (from Apple, Microsoft, Google, or the provider of a
 * Linux distribution), but several notable exceptions exist: Mozilla (for the Firefox browser) and
 * Oracle (for the JVM in OpenJDK and many other Java distributions) maintain their own trust
 * authorization implementations and their own list of "root" certificates that are assumed to be
 * trustworthy.
 *
 * While it is possible (and inevitable) that this interface could be implemented entirely within
 * Ecstasy, and using a custom (or configurable) set of trusted roots, the general case is expected
 * to be that the `Authority` available by injection is likely to be one that delegates trust
 * decisions and verification to "the host".
 */
interface Authority
    {
    /**
     * A human-comprehensible name or short description of the Authority. Not intended as a unique
     * key or any other reliable mechanism of identification. For example, "host-based" could
     * indicate that the host provides a service that can validate certificates using some
     * combination of local trusted certificate data and/or internet protocols.
     */
    @RO String name;

    /**
     * Potential reasons why a certificate may be untrusted.
     */
    enum TrustFailure
        {
        /**
         * The certificate is not active at the specified time.
         */
        Inactive,
        /**
         * The certificate has expired.
         */
        Expired,
        /**
         * The certificate has been revoked.
         */
        Revoked,
        /**
         * The certificate does not follow this authority's rules.
         */
        BadStructure,
        /**
         * The certificate signature is not cryptographically valid.
         */
        BadSignature,
        /**
         * The trust failure is caused by a certificate in the certificate chain.
         */
        BadChain,
        /**
         * Cannot verify for some other reason, e.g. network connectivity.
         */
        Unverifiable,
        /**
         * The vouching CA is not trusted by this Authority, for any of a number of reasons.
         */
        UntrustedIssuer,
        /**
         * No CA to vouch for the certificate.
         */
        SelfCertified,
        /**
         * Invalid or missing dates that indicate when the certificate is valid. A certificate
         * without a specified begin and expiry date should be considered invalid for most uses.
         */
        NoExpiry,
        }

    /**
     * Test a certificate to determine if it can be trusted.
     *
     * @param cert  the [Certificate] to evaluate
     * @param asOf  (optional) a date/time value at which the certificate needs to be valid
     *
     * @return True iff there is at least one known reasons why the `Certificate` is untrustworthy
     * @return (conditional) the most serious of all known reasons why the `Certificate` cannot
     *         be assumed to be trustworthy
     */
    conditional TrustFailure untrusted(Certificate cert, Time? asOf = Null);

    /**
     * The Authority can (but is not required to) expose any/all of its "trusted root" certificates,
     * and any cached Certificates that chain back to those roots, via this `KeyStore`.
     */
    @RO KeyStore trustedRoots;
    }
