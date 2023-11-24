import crypto.Signer;


/**
 * A FixedRealm is a realm implementation with a fixed number of named users, each with a fixed
 * password.
 */
const FixedRealm
        implements Realm {
    /**
     * Construct a `FixedRealm` from plain text user names and passwords, using an optional list
     * of [hashing algorithms](Signer).
     *
     * @param realmName  the human readable name of the realm
     * @param userPwds   the user names and passwords, in plain text form
     * @param userRoles  (optional) the user names and roles, in plain text form
     * @param hashers    (optional) the [hashing algorithms](Signer) to support, which allow a
     *                   client to never send either a user name or password in plain text, and
     *                   which simultaneously allow the server to store the user names and passwords
     *                   in a cryptographically secure digest form
     */
    construct(String                realmName,
              Map<String, String>   userPwds,
              Map<String, String[]> userRoles = [],
              Signer[]              hashers   = [],
             ) {
        // select a default signer; use the weakest of the signers by default (which should be at
        // the very end of the list)
        Signer defaultHasher;
        if (!(defaultHasher := hashers.last())) {
            @Inject crypto.Algorithms algorithms;
            if (!(defaultHasher := algorithms.hasherFor("MD5"))) {
                defaultHasher = TODO create crypto.hashers.MD5 class
            }
        }

        // incorporate the default hasher into the array of hashers if it wasn't already present
        if (hashers.empty) {
            hashers = [defaultHasher];
        }

        // hash the passwords (and possibly the user names)
        Int                              userCount   = userPwds.size;
        Int                              hasherCount = hashers.size;
        HashMap<String, Hash|Hash[]>     pwdsByUser  = new HashMap(userCount);
        HashMap<String, HashSet<String>> rolesByUser = new HashMap(userRoles.size);
        HashMap<Hash, String|String[]>   usersByHash = new HashMap(userCount * hasherCount);

        static Boolean addUser(HashMap<Hash, String|String[]>.Entry entry, String user) {
            if (entry.exists) {
                String|String[] users = entry.value;
                entry.value = users.is(String[]) ? users + user : [users, user];
            } else {
                entry.value = user;
            }
            return True;
        }

        if (hasherCount > 1) {
            for ((String user, String pwd) : userPwds) {
                pwdsByUser.put(user, new Hash[hasherCount]
                        (i -> passwordHash(user, realmName, pwd, hashers[i]))
                        .freeze(inPlace=True));
            }

            for (Signer hasher : hashers) {
                for (String user : userPwds) {
                    usersByHash.process(userHash(user, realmName, hasher), addUser(_, user));
                }
            }
        } else {
            for ((String user, String pwd) : userPwds) {
                usersByHash.process(userHash(user, realmName, defaultHasher), addUser(_, user));
                pwdsByUser.put(user, passwordHash(user, realmName, pwd, defaultHasher));
            }
        }

        // build the lookup from user to roles
        Map<HashSet<String>, HashSet<String>> intern = new HashMap();
        for ((String user, String[] roles) : userRoles) {
            if (pwdsByUser.contains(user) && !roles.empty) {
                HashSet<String> roleSet = new HashSet(roles).freeze(True);
                if (!(roleSet := intern.get(roleSet))) {
                    intern.put(roleSet, roleSet);
                }
                rolesByUser.put(user, new HashSet<String>(roles));
            }
        }

        this.name          = realmName;
        this.hashers       = hashers;
        this.defaultHasher = defaultHasher;
        this.pwdsByUser    = pwdsByUser.freeze(True);
        this.rolesByUser   = rolesByUser.freeze(True);
        this.usersByHash   = usersByHash.freeze(True);
    }

    /**
     * The default [hashing algorithm](Signer) is the weakest one provided to this FixedRealm, or
     * the default MD5 algorithm if none was provided.
     */
    protected/private Signer defaultHasher;

    /**
     * For each user, a password hash from each [hashing algorithm](Signer) is held.
     */
    protected/private immutable Map<String, Hash|Hash[]> pwdsByUser;

    /**
     * For each user, a list of roles for that user.
     */
    protected/private immutable Map<String, Set<String>> rolesByUser;

    /**
     * For each user hash, there is typically only one user name; in the rare case that the hashes
     * collide for each [hashing algorithm](Signer) provided to this realm (or the default MD5
     * if none was provided), a map of user hash to user(s) is held.
     */
    protected/private immutable Map<Hash, String|String[]> usersByHash;


    // ----- Realm interface -----------------------------------------------------------------------

    @Override
    conditional Set<String> validUser(String user) {
        return pwdsByUser.contains(user)
                ? (True, rolesByUser.get(user) ?: [])
                : False;
    }

    @Override
    conditional Set<String> authenticate(String user, String password) {
        Hash hash = passwordHash(user, name, password, defaultHasher);
        if ((_, Set<String> roles) := authenticateHash(user, hash, defaultHasher)) {
            return True, roles;
        }
        return False;
    }

    @Override
    Signer[] hashers;

    @Override
    Hash[] hashesFor(UserId userId, Signer hasher) {
        if (userId.is(String)) {
            if (Hash|Hash[] pwdHashes := pwdsByUser.get(userId)) {
                return [pwdHashes.is(Hash) ? pwdHashes : pwdHashes[hashIndex(hasher)]];
            }

            return [];
        }

        String|String[] users = usersByHash.getOrDefault(userId, []);
        if (users.is(String)) {
            return hashesFor(users, hasher);
        }

        switch (users.size) {
        case 0:
            return [];

        case 1:
            return hashesFor(users[0], hasher);

        default:
            Hash[] pwdHashes = new Hash[];
            for (String user : users) {
                pwdHashes += hashesFor(user, hasher);
            }
            return pwdHashes.freeze(inPlace=True);
        }
    }

    @Override
    conditional (String, Set<String>) authenticateHash(UserId userId, Hash pwdHash, Signer hasher) {
        if (userId.is(String)) {
            if (Hash|Hash[] pwdHashes := pwdsByUser.get(userId)) {
                Hash checkHash = pwdHashes.is(Hash) ? pwdHashes : pwdHashes[hashIndex(hasher)];
                return pwdHash == checkHash, userId, rolesByUser.get(userId) ?: [];
            }

            return False;
        }

        // look up the user by the user hash
        if (String|String[] plainTextUsers := usersByHash.get(userId)) {
            if (plainTextUsers.is(String)) {
                return authenticateHash(plainTextUsers, pwdHash, hasher);
            }

            for (String plainTextUser : plainTextUsers) {
                if ((_, Set<String> roles) := authenticateHash(plainTextUser, pwdHash, hasher)) {
                    return True, plainTextUser, roles;
                }
            }
        }

        return False;
    }


    // ----- internal ------------------------------------------------------------------------------

    /**
     * Obtain the index within the values (arrays of hashed passwords) stored in `pwdsByUser` that
     * corresponds to a particular [hasher](Signer).
     *
     * @param hasher  the [hasher](Signer) that indicates which digested passwords to hash or verify
     *
     * @return the index within the arrays of hashed passwords stored as the values of the
     *         `pwdsByUser` map
     */
    protected Int hashIndex(Signer hasher) {
        if (hasher == defaultHasher) {
            return hashers.size - 1;
        }

        Loop: for (val candidate : hashers) {
            if (hasher.algorithm == candidate.algorithm) {
                return Loop.count;
            }
        }

        assert as $"Unknown hasher: {hasher}";
    }
}