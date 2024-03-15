import crypto.Signer;


/**
 * A Realm is a named security domain, and an implementation of this interface is responsible
 * for the validation of user credentials, specifically the user name and password.
 */
interface Realm {
    typedef immutable Byte[] as Hash;

    /**
     * A group of hashes, one for each of the supported hash algorithms. If a hash algorithm is not
     * configured to be used, then its hash will be a zero-length byte array.
     */
    static const HashInfo
            (
            Time created,
            Hash md5,
            Hash sha256,
            Hash sha512_256,
            ) {
        conditional Hash hashFor(Signer hasher) {
            Hash hash;
            switch (hasher.algorithm.name) {
            case "MD5":
                hash = md5;
                break;

            case "SHA-256":
                hash = sha256;
                break;

            case "SHA-512-256":
                hash = sha512_256;
                break;

            default:
                return False;
            }

            return hash.empty
                    ? False
                    : (True, hash);
        }
    }

    /**
     * A `UserId` is either a plain text user name, or the hash specified in section 3.4.4 of the
     * [HTTP Digest Access Authentication](https://datatracker.ietf.org/doc/html/rfc7616) standard:
     *
     *     username = H( unq(username) ":" unq(realm) )
     */
    typedef String | Hash as UserId;

    /**
     * The name of the Realm is intended to be human-readable and short-yet-descriptive.
     */
    @RO String name;

    /**
     * Verify that the passed user name is valid, for example that the account is not disabled, and
     * obtain the roles associated with the user.
     *
     * @param user      the user's identity, as provided by the client
     * @param password  the user's password, as provided by the client
     *
     * @return True iff the user identity is verified to exist, and is active (e.g. can log in)
     * @return (conditional) the role names associated with the user, if any, otherwise `[]`
     */
    conditional Set<String> validUser(String user);

    /**
     * Authenticate the passed user name and password.
     *
     * @param user      the user's identity, as provided by the client
     * @param password  the user's password, as provided by the client
     *
     * @return True iff the user identity is verified to exist, and the provided password is
     *         correct for that user
     * @return (conditional) the role names associated with the user, if any, otherwise `[]`
     */
    conditional Set<String> authenticate(String user, String password);

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
    @RO Signer[] hashers.get() {
        return [];
    }

    /**
     * Given a user (either plain text or hashed) and a [hasher](Signer), obtain the corresponding
     * hashed password information, if any.
     *
     * The forms of the hashes are specified by the
     * [HTTP Digest Access Authentication](https://datatracker.ietf.org/doc/html/rfc7616)
     * standard.
     *
     * If the `userId` is hashed, the hash is in the form:
     *
     *     username = H( unq(username) ":" unq(realm) )
     *
     * The returned hashes are in the form:
     *
     *     A1 = unq(username) ":" unq(realm) ":" passwd
     *
     * @param userId  the plain text or hashed user identity
     * @param hasher  the specific [hasher](Signer) which corresponds to the resulting password
     *                hashes
     *
     * @return an array of zero or more password hashes that correspond to the specified `userId`
     */
    Hash[] hashesFor(UserId userId, Signer hasher);

    /**
     * Authenticate the passed user name, which may be hashed, and password, which is hashed.
     *
     * The forms of the hashes are specified by the
     * [HTTP Digest Access Authentication](https://datatracker.ietf.org/doc/html/rfc7616)
     * standard.
     *
     * If the `userId` is hashed, the hash is in the form:
     *
     *     username = H( unq(username) ":" unq(realm) )
     *
     * The `pwdHash` is in the form:
     *
     *     A1 = unq(username) ":" unq(realm) ":" passwd
     *
     * @param userId   the user's identity, which may be either plain text or a hash
     * @param pwdHash  the user's password, which is in a hashed form
     *
     * @return True iff the user identity is verified to exist, and the provided password is
     *         correct for that user
     * @return (conditional) the user identity in plain text
     * @return (conditional) the role names associated with the user, if any, otherwise `[]`
     */
    conditional (String, Set<String>) authenticateHash(UserId userId, Hash pwdHash, Signer hasher) {
        TODO User identity and password hashing are not supported by this Realm
    }

    /**
     * Hash the user and password data; this method is performing the same work as [userHash()]
     * and [passwordHash()] for each of the (up to) three supported algorithms.
     *
     * @param userName  the user name
     * @param password  (optional) password
     */
    (HashInfo userHashes, HashInfo pwdHashes) createHashes(String userName, String? password) {
        Hash userBytes  = $"{userName}:{name}".utf8();
        Hash pwdBytes   = $"{userName}:{name}:{password ?: ""}".utf8();

        Signer[] hashers    = hashers;
        Signer?  md5        = Null;
        Signer?  sha256     = Null;
        Signer?  sha512_256 = Null;
        for (Signer signer : hashers) {
            switch (signer.algorithm.name) {
            case "MD5":
                md5 = signer;
                break;
            case "SHA-256":
                sha256 = signer;
                break;
            case "SHA-512-256":
                sha512_256 = signer;
                break;
            }
        }

        @Inject Clock clock;
        Time creation = clock.now;

        HashInfo userHashes = new HashInfo(creation,
                md5?       .sign(userBytes).bytes : [],
                sha256?    .sign(userBytes).bytes : [],
                sha512_256?.sign(userBytes).bytes : [],
                );

        HashInfo pwdHashes  = new HashInfo(creation,
                md5?       .sign(pwdBytes).bytes : [],
                sha256?    .sign(pwdBytes).bytes : [],
                sha512_256?.sign(pwdBytes).bytes : [],
                );

        return (userHashes, pwdHashes);
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
    static Hash userHash(UserId user, String realm, Signer hasher) {
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
    static Hash passwordHash(String user, String realm, String password, Signer hasher) {
        return hasher.sign($"{user}:{realm}:{password}".utf8()).bytes;
    }
}
