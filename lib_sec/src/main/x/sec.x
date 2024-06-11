/**
 * The security module defines a small set of security primitives that can be used as the basis for
 * authentication and permission-based authorization, including support for principals, groups, and
 * entitlements.
 */
module sec.xtclang.org {

    /**
     * A `PlainTextCredential` represents a user name and password.
     */
    const PlainTextCredential(String name, String password)
            extends Credential("pwd");

    /**
     * A `KeyCredential` represents a secret key, such as an "API key".
     */
    const KeyCredential(String key)
            extends Credential("key");
}