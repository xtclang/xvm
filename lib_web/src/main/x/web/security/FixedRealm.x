import crypto.Signer;


/**
 * A FixedRealm is a realm implementation with a fixed number of named users, each with a fixed
 * password.
 */
const FixedRealm
        implements Realm
    {
    typedef immutable Byte[] as Hash;

    /**
     * Construct a `FixedRealm` from plain text user names and passwords, using an optional list
     * of [hashing algorithms](Signer).
     *
     * @param realmName  the human readable name of the realm
     * @param userPwds   the user names and passwords, in plain text form
     * @param hashers    (optional) the [hashing algorithms](Signer) to support, which allow a
     *                   client to never send either a user name or password in plain text, and
     *                   which simultaneously allow the server to store the user names and passwords
     *                   in a cryptographically secure digest form
     */
    construct(String              realmName,
              Map<String, String> userPwds,
              Signer[]            hashers = [])
        {
        // select a default signer; use the weakest of the signers by default (which should be at
        // the very end of the list)
        Signer defaultHasher;
        if (!(defaultHasher := hashers.last()))
            {
            @Inject crypto.Algorithms algorithms;
            if (!(defaultHasher := algorithms.hasherFor("MD5")))
                {
                defaultHasher = TODO create crypto.hashers.MD5 class
                }
            }

        // hash the passwords (and possibly the user names)
        Int hasherCount      = hashers.size.notLessThan(1);
        val userPwdsByHasher = new HashMap<Hash, Hash>[hasherCount](_ -> new HashMap(userPwds.size));

        for (Int i : 0 ..< hasherCount)
            {
            Signer hasher = i < hashers.size ? hashers[i] : defaultHasher;
            val hashedPwds = userPwdsByHasher[i];
            for ((String user, String pwd) : userPwds)
                {
                hashedPwds.put(userHash(user, realmName, hasher),
                               passwordDigest(user, realmName, pwd, hasher));
                }
            userPwdsByHasher[i] = hashedPwds.freeze(True);
            }

        this.name             = realmName;
        this.hashers          = hashers;
        this.defaultHasher    = defaultHasher;
        this.userPwdsByHasher = userPwdsByHasher.freeze(True);
        }

    /**
     * The default [hashing algorithm](Signer) is the weakest one provided to this FixedRealm, or
     * the default MD5 algorithm if none was provided.
     */
    protected/private Signer defaultHasher;

    /**
     * For each [hashing algorithm](Signer) provided to this FixedRealm (or the default MD5 if none
     * was provided), a map of user hash to password hash is held.
     */
    protected/private immutable Map<Hash, Hash>[] userPwdsByHasher;


    // ----- Realm interface -----------------------------------------------------------------------

    @Override
    Boolean validate(String user, String password)
        {
        Signer hasher  = defaultHasher;
        Hash   pwdHash = passwordDigest(user, name, password, hasher);
        return validateHash(user, pwdHash, hasher);
        }

    @Override
    Boolean validateHash(String | Hash user, Hash password, Signer hasher)
        {
        Hash userHash = userHash(user, name, hasher);
        return userPwds(hasher)[userHash] == password;
        }

    @Override
    Signer[] hashers;

    @Override
    Boolean userHashed.get()
        {
        return !hashers.empty;
        }


    // ----- internal ------------------------------------------------------------------------------

    typedef String | Hash as UserId;

    /**
     * Given a user either as a string or as a hash, obtain the key used by the FixedRealm to find
     * the user's password digest.
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
    static Hash passwordDigest(String user, String realm, String password, Signer hasher)
        {
        return hasher.sign($"{user}:{realm}:{password}".utf8()).bytes;
        }

    /**
     * Obtain the user/password lookup map that contains the user name and password information that
     * was hashed by a particular hasher.
     *
     * @param hasher  (optional) the hasher (a [Signer]) that indicates which set of digested
     *                passwords to obtain, i.e. passwords that were digested by that hasher; if
     *                none is specified, then the default hasher is assumed
     *
     * @return the map containing the user names and passwords hashed by the specified hasher
     */
    protected Map<Hash, Hash> userPwds(Signer? hasher = Null)
        {
        if (hasher? == defaultHasher : True)
            {
            return userPwdsByHasher[userPwdsByHasher.size-1];
            }

        assert hasher != Null; // TODO GG this should not be necessary

        Loop: for (val candidate : hashers)
            {
            if (hasher.algorithm == candidate.algorithm)
                {
                return userPwdsByHasher[Loop.count];
                }
            }

        assert as $"Unknown hasher: {hasher}";
        }
    }