/**
 * A `CryptoPassword` is a holder of a password whose internal state (such as the text of the
 * password) is potentially unavailable through this API. The `CryptoPassword` itself may be
 * provided via injection (or via an injected [KeyStore], for example); in such a case, the
 * `CryptoPassword` may be usable as an argument to a [KeyStore] that was also injected, despite
 * the password's internal value itself being locally unavailable.
 *
 * This follows a design goal of minimizing visibility to security secrets. The secrets are required
 * by the crypto machinery, but disabling visibility to the contents of the secrets allows the
 * necessary secrets to be provided to an application, without allowing the application to actually
 * "see" the secrets, which helps to prevent the accidental leak of the secrets' contents.
 */
interface CryptoPassword {
    /**
     * A human-comprehensible name or short description of the CryptoPassword. Not intended as a
     * unique key or any other reliable mechanism of identification, but useful for organization of
     * a keystore, or helpful in a log message. For example, "www.acme.com" would suggest that the
     * password is somehow related to that web address.
     */
    @RO String name;

    /**
     * Determine if the text of the `CryptoPassword` are accessible to the caller, and if so, obtain
     * that text.
     *
     * @return True if the caller is permitted to access the text of the password itself
     * @return (conditional) the password's text, as a String
     */
    conditional String isVisible();
}