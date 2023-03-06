import crypto.Signer;


/**
 * A Realm is a named security domain, and an implementation of this interface is responsible
 * for the validation of user credentials, specifically the user name and password.
 */
interface Realm
    {
    typedef immutable Byte[] as Hash;

    typedef String | Hash as UserId;

    /**
     * The name of the Realm is intended to be human-readable and short-yet-descriptive.
     */
    @RO String name;

    /**
     * Validate the passed user name and password.
     *
     * @param user      the user's identity, as provided by the client
     * @param password  the user's password, as provided by the client
     *
     * @return True iff the user identity is verified to exist, and the provided password is
     *         correct for that user
     */
    Boolean validate(String user, String password);

    /**
     * Validate the passed user name, which may be hashed, and password, which is hashed.
     *
     * @param user      the user's identity, which may be in plain text or as a hash, as provided by
     *                  the client
     * @param password  the user's password, as provided by the client
     *
     * @return True iff the user identity is verified to exist, and the provided password is
     *         correct for that user
     * @return (conditional) the user identity in plain text
     */
    conditional String validateHash(UserId user, Hash password, Signer hasher)
        {
        TODO User identity and password hashing are not supported by this Realm
        }

    /**
     * A realm may support user id and password hashes directly, which allows the realm to not store
     * the credential information in plain text. (Supporting the hash of the user id also avoids the
     * user id being _transmitted_ as plain text.)
     *
     * An empty array indicates that no hashes are supported by the realm.
     *
     * Hashing algorithms are expected to included some subset (or none) of the following:
     *
     * * SHA-512-256
     * * SHA-256
     * * MD5
     *
     * It is possible that a custom Realm implementation may choose to support other hashing
     * algorithms, or not support any of those listed above. The implementations listed above are
     * specified by the
     * [HTTP Digest Access Authentication](https://datatracker.ietf.org/doc/html/rfc7616) standard.
     *
     * Using the HTTP Digest Access Authentication method, the server may specify several of these
     * to the client as being acceptable; the client should use the first one in the list (in the
     * `401 Unauthorized` response message) that it supports; this is why MD5 is listed last, since
     * it is the weakest hash, and fairly easy to defeat using a brute force attack. Unfortunately,
     * many clients still only support MD5 because it has only been a few decades since the other
     * options were added.
     */
    @RO Signer[] hashers.get()
        {
        return [];
        }

    /**
     * `True` iff the user identity is passed from the client to the server in a hashed form, which
     * prevents the user identity from being in plain text form in the message.
     *
     * The approach specified in the
     * [HTTP Digest Access Authentication](https://datatracker.ietf.org/doc/html/rfc7616) standard
     * serves as the basis for this feature:
     *
     *     3.4.4.  Username Hashing
     *
     *     To protect the transport of the username from the client to the
     *     server, the server SHOULD set the userhash parameter with the value
     *     of "true" in the WWW-Authentication header field.
     *
     *     If the client supports the userhash parameter, and the userhash
     *     parameter value in the WWW-Authentication header field is set to
     *     "true", then the client MUST calculate a hash of the username after
     *     any other hash calculation and include the userhash parameter with
     *     the value of "true" in the Authorization header field.  If the client
     *     does not provide the username as a hash value or the userhash
     *     parameter with the value of "true", the server MAY reject the
     *     request.
     *
     *     The following is the operation that the client will perform to hash
     *     the username, using the same algorithm used to hash the credentials:
     *
     *     username = H( unq(username) ":" unq(realm) )
     */
    @RO Boolean userHashed.get()
        {
        return False;
        }


    // ----- helpers -------------------------------------------------------------------------------

    /**
     * Produce a user hash.
     *
     * The approach specified in section 3.4.4 of the
     * [HTTP Digest Access Authentication](https://datatracker.ietf.org/doc/html/rfc7616) standard
     * serves as the basis for this feature:
     *
     *     username = H( unq(username) ":" unq(realm) )
     *
     * @param user    the user name in plain text, or the hash of the user name as specified by
     *                [RFC7616](https://datatracker.ietf.org/doc/html/rfc7616#section-3.4.4)
     * @param realm   the realm name in plain text
     * @param hasher  the hasher (a [Signer]) to use
     *
     * @return the digest that represents the user information that is used by the FixedRealm as a
     *         key to the password digest
     */
    static Hash userHash(UserId user, String realm, Signer hasher)
        {
        return user.is(String)
                ? hasher.sign($"{user}:{realm}".utf8()).bytes
                : user;
        }

    /**
     * Produce a password digest.
     *
     * The [HTTP Digest Access Authentication](https://datatracker.ietf.org/doc/html/rfc7616)
     * standard specifies the password digest that the client and server are each aware of as
     * `H(user:realm:pwd)`, where `H` is the hash function.
     *
     * @param user      the user name in plain text
     * @param realm     the realm name in plain text
     * @param password  the password in plain text
     * @param hasher    the hasher (a [Signer]) to use
     *
     * @return the digest that represents the password information as it is held by the FixedRealm
     */
    static Hash passwordHash(String user, String realm, String password, Signer hasher)
        {
        return hasher.sign($"{user}:{realm}:{password}".utf8()).bytes;
        }
    }
