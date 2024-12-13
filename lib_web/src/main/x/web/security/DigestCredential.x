import crypto.Signer;

import sec.Credential;

/**
 * A `DigestCredential` represents a user name and password, with the password (and potentially the
 * user name) stored as hashes.
 */
const DigestCredential
        extends Credential {

    /**
     * "md" == Message Digest
     */
    static String Scheme = "md";

    /**
     * Construct a `DigestCredential` for the specified user with the specified password.
     *
     * @param realmName  the realm name (note: not stored on the DigestCredential)
     * @param userName   the user name in plain text
     * @param password   the user password in plain text (note: not stored on the DigestCredential)
     */
    construct(String realmName, String userName, String password) {
        construct Credential(Scheme);
        name                = userName;
        name_md5            = userHash(userName, realmName, md5);
        name_sha256         = userHash(userName, realmName, sha256);
        name_sha512_256     = userHash(userName, realmName, sha512_256);
        password_md5        = passwordHash(userName, realmName, password, md5);
        password_sha256     = passwordHash(userName, realmName, password, sha256);
        password_sha512_256 = passwordHash(userName, realmName, password, sha512_256);
    }

    /**
     * Internal constructor for [with] method and subclasses.
     */
    protected construct(
            String  scheme,
            Time?   validFrom,
            Time?   validUntil,
            Status? status,
            String  name,
            Byte[]  name_md5,
            Byte[]  name_sha256,
            Byte[]  name_sha512_256,
            Byte[]  password_md5,
            Byte[]  password_sha256,
            Byte[]  password_sha512_256,
            ) {
        construct Credential(scheme, validFrom, validUntil, status);
        this.name                = name;
        this.name_md5            = name_md5;
        this.name_sha256         = name_sha256;
        this.name_sha512_256     = name_sha512_256;
        this.password_md5        = password_md5;
        this.password_sha256     = password_sha256;
        this.password_sha512_256 = password_sha512_256;
    }

    // ----- construction --------------------------------------------------------------------------


    /**
     * Create a copy of this `DigestCredential`, but with specific attributes modified.
     *
     * @param scheme               the new `scheme` name, or pass `Null` to leave unchanged
     * @param validFrom            the new `validFrom` value to use, or `Null` to leave unchanged
     * @param validUntil           the new `validUntil` value to use, or `Null` to leave unchanged
     * @param status               the new `status` value to use, or `Null` to leave unchanged; the
     *                             only legal [Status] values to pass are `Active`, `Suspended`, and
     *                             `Revoked`; passing `Active` will result in the [status] of `Null`
     * @param realmName            the realm name, which is required if changing the user name
     *                             and/or password
     * @param userName             the new [name] value, or pass `Null` to leave unchanged; if the
     *                             user name is changed, then the realm name and plain text password
     *                              must also be passed
     * @param password             the plain text password value, or pass `Null` to leave unchanged;
     *                             if the password is changed, then the realm name must also be
     *                             passed
     * @param name_md5             the new value for [name_md5], or pass `Null` to leave unchanged
     * @param name_sha256          the new value for [name_sha256], or pass `Null` to leave
     *                             unchanged
     * @param name_sha512_256      the new value for [name_sha512_256], or pass `Null` to leave
     *                             unchanged
     * @param password_md5         the new value for [password_md5], or pass `Null` to leave
     *                             unchanged
     * @param password_sha256      the new value for [password_sha256], or pass `Null` to leave
     *                             unchanged
     * @param password_sha512_256  the new value for [password_sha512_256], or pass `Null` to leave
     *                             unchanged
     */
    @Override
    DigestCredential with(
            String? scheme              = Null,
            Time?   validFrom           = Null,
            Time?   validUntil          = Null,
            Status? status              = Null,
            String? realmName           = Null,
            String? userName            = Null,
            String? password            = Null,
            Byte[]? name_md5            = Null,
            Byte[]? name_sha256         = Null,
            Byte[]? name_sha512_256     = Null,
            Byte[]? password_md5        = Null,
            Byte[]? password_sha256     = Null,
            Byte[]? password_sha512_256 = Null,
           ) {
        if (realmName != Null || password != Null) {
            assert password == Null || realmName != Null;
            if (realmName != Null) {
                userName      ?:= name;
                name_md5        = userHash(userName, realmName, md5);
                name_sha256     = userHash(userName, realmName, sha256);
                name_sha512_256 = userHash(userName, realmName, sha512_256);
                if (password == Null) {
                    assert name_md5        == this.name_md5
                        && name_sha256     == this.name_sha256
                        && name_sha512_256 == this.name_sha512_256;
                } else {
                    password_md5        = passwordHash(userName, realmName, password, md5);
                    password_sha256     = passwordHash(userName, realmName, password, sha256);
                    password_sha512_256 = passwordHash(userName, realmName, password, sha512_256);
                }
            }
        }
        return new DigestCredential(
                scheme              = scheme              ?: this.scheme,
                validFrom           = validFrom           ?: this.validFrom,
                validUntil          = validUntil          ?: this.validUntil,
                status              = status              ?: this.status,
                name                = userName            ?: this.name,
                name_md5            = name_md5            ?: this.name_md5,
                name_sha256         = name_sha256         ?: this.name_sha256,
                name_sha512_256     = name_sha512_256     ?: this.name_sha512_256,
                password_md5        = password_md5        ?: this.password_md5,
                password_sha256     = password_sha256     ?: this.password_sha256,
                password_sha512_256 = password_sha512_256 ?: this.password_sha512_256,
                );
    }

    // ----- properties ----------------------------------------------------------------------------

    String name;
    Byte[] name_md5;
    Byte[] name_sha256;
    Byte[] name_sha512_256;
    Byte[] password_md5;
    Byte[] password_sha256;
    Byte[] password_sha512_256;

    @Override
    String[] locators.get() = [
            name.quoted(),
            name_md5.toString(pre=""),
            name_sha256.toString(pre=""),
            name_sha512_256.toString(pre=""),
            ];

    // ----- API -----------------------------------------------------------------------------------
    
    /**
     * Test if this `DigestCredential` refers to the specified [UserId].
     *
     * @param userId  a user name or user name hash
     *
     * @return `True` iff this credential refers to the specified user
     */
    Boolean isUser(UserId userId) {
        if (userId.is(String)) {
            return name == userId;
        } else {
            return name_md5 == userId || name_sha256 == userId || name_sha512_256 == userId;
        }
    }

    /**
     * Given a "hasher" aka [Signer], obtain the corresponding password hash from this `Credential`.
     *
     * @param hasher  TODO
     *
     * @return True if the `Signer` is recognized by this `DigestCredential`
     * @return (conditional) the corresponding password hash
     */
    conditional Hash findPasswordHash(Signer hasher) {
        return switch (hasher.algorithm.name) {
            case "MD5":         (True, password_md5);
            case "SHA-256":     (True, password_sha256);
            case "SHA-512-256": (True, password_sha512_256);
            default: False;
        };
    }

    // ----- internal ------------------------------------------------------------------------------

    static Signer md5 = {
        @Inject crypto.Algorithms algorithms;
        return algorithms.hasherFor("MD5") ?: assert as "MD5 Signer required";
    };

    static Signer sha256 = {
        @Inject crypto.Algorithms algorithms;
        return algorithms.hasherFor("SHA-256") ?: assert as "SHA-256 Signer required";
    };

    static Signer sha512_256 = {
        @Inject crypto.Algorithms algorithms;
        return algorithms.hasherFor("SHA-512-256") ?: assert as "SHA-512-256 Signer required";
    };

    /**
     * A `Hash` is an immutable array of bytes.
     */
    typedef immutable Byte[] as Hash;

    /**
     * A `UserId` is either a plain text user name, or the hash specified in section 3.4.4 of the
     * [HTTP Digest Access Authentication](https://datatracker.ietf.org/doc/html/rfc7616) standard:
     *
     *     username = H( unq(username) ":" unq(realm) )
     */
    typedef String | Hash as UserId;

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
     * @return the digest that represents the user name
     */
    static Hash userHash(String user, String realm, Signer hasher) {
        return hasher.sign($"{user}:{realm}".utf8()).bytes.freeze(inPlace=True);
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
     * @return the digest that represents the password information
     */
    static Hash passwordHash(String user, String realm, String password, Signer hasher) {
        return hasher.sign($"{user}:{realm}:{password}".utf8()).bytes.freeze(inPlace=True);
    }
}