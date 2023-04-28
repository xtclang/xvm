import crypto.Signer;

import oodb.Connection;
import oodb.DBSchema;

import User.HashInfo;


/**
 * A DBRealm is a realm implementation on top of an [AuthSchema].
 */
const DBRealm
        implements Realm
    {
    /**
     * Construct a `DBRealm` from plain text user names and passwords, using an optional list
     * of [hashing algorithms](Signer).
     *
     * @param realmName  the human readable name of the realm
     * @param userPwds   the user names and passwords, in plain text form
     * @param hashers    (optional) the [hashing algorithms](Signer) to support, which allow a
     *                   client to never send either a user name or password in plain text, and
     *                   which simultaneously allow the server to store the user names and passwords
     *                   in a cryptographically secure digest form
     */
    construct(String realmName, String? connectionName=Null)
        {
        // obtain a connection to the database, and find the AuthSchema inside the database
        @Inject(resourceName=connectionName) Connection dbc;
        String?     path       = Null;
        AuthSchema? authSchema = Null;
        for ((String pathStr, DBSchema schema) : dbc.sys.schemas)
            {
            // find the AuthSchema; it must occur exactly-once
            if (schema.is(AuthSchema))
                {
                assert path == Null as $|Ambiguous "AuthSchema" instances found at multiple\
                                        | locations within the database:\
                                        | {pathStr.quoted()} and {path.as(String).quoted()}
                                       ; // TODO GG get rid of .as(String)
                path       = pathStr;
                authSchema = schema;
                }
            }
        assert path != Null && authSchema != Null as "The database does not contain an \"AuthSchema\"";

        Configuration cfg = authSchema.config.get();
        if (!cfg.configured)
            {
            // the database has not yet been configured, so we need an initial configuration to be
            // injected, and we'll configure the database in the constructor's finally block
            // (because we need the realm to exist in order to configure the database)
            @Inject Configuration startingCfg;
            this.createCfg = startingCfg;
            cfg            = startingCfg;
            }

        @Inject crypto.Algorithms algorithms;
        Signer[]  hashers          = new Signer[];
        Signer?[] supportedHashers = new Signer?[3];
        Signer?   weakestHasher    = Null;

        if (cfg.useSHA512_256)
            {
            Signer hasher = hasherByName("SHA-512-256");
            hashers            += hasher;
            supportedHashers[2] = hasher;
            weakestHasher       = hasher;
            }

        if (cfg.useSHA256)
            {
            Signer hasher = hasherByName("SHA-256");
            hashers            += hasher;
            supportedHashers[1] = hasher;
            weakestHasher       = hasher;
            }

        if (cfg.useMD5)
            {
            Signer hasher = hasherByName("MD5");
            hashers            += hasher;
            supportedHashers[0] = hasher;
            weakestHasher       = hasher;
            }

        assert weakestHasher != Null as "No hasher configured; at least one is required";

        this.name             = realmName;
        this.authSchema       = authSchema;
        this.hashers          = hashers;
        this.supportedHashers = supportedHashers;
        this.weakestHasher    = weakestHasher;
        }
    finally
        {
        if (Configuration cfg ?= this.createCfg)
            {
            using (authSchema.dbConnection.createTransaction())
                {
                // create the user roles
                Roles                 roles         = authSchema.roles;
                Map<String, String[]> initUserRoles = new HashMap();
                for ((String roleName, String[] userNames) : cfg.initRoleUsers)
                    {
                    assert roles.createRole(roleName);
                    for (String userName : userNames)
                        {
                        if (!initUserRoles.putIfAbsent(userName, [roleName]))
                            {
                            initUserRoles[userName] = initUserRoles.getOrDefault(userName, []) + roleName;
                            }
                        }
                    }

                // create the user records
                Users users = authSchema.users;
                for ((String userName, String password) : cfg.initUserPass)
                    {
                    assert users.createUser(this, userName, password,
                            initUserRoles.getOrDefault(userName, []));
                    }
                }
            }
        }

    /**
     * The configuration to write to the database the first time the database and realm are created.
     */
    protected Configuration? createCfg;

    /**
     * The part of the database where the authentication information is stored.
     */
    protected AuthSchema authSchema;

    /**
     * An array of three elements, containing up to the three supported signers, specifically:
     *
     * * Element `[0]` contains either an MD5 hasher or `Null`
     * * Element `[1]` contains either an SHA-256 hasher or `Null`
     * * Element `[2]` contains either an SHA-512-256 hasher or `Null`
     */
    protected Signer?[] supportedHashers;

    /**
     * The default [hashing algorithm](Signer) is the weakest one provided to this DBRealm, or
     * the default MD5 algorithm if none was provided.
     */
    protected Signer weakestHasher;


    // ----- Realm interface -----------------------------------------------------------------------

    @Override
    conditional Set<String> validUser(String userName) = loadUserRoles(userName);

    @Override
    conditional Set<String> authenticate(String user, String password)
        {
        if ((_, Set<String> roles) := authenticateHash(
                user, passwordHash(user, name, password, weakestHasher), weakestHasher))
            {
            return True, roles;
            }
        return False;
        }

    @Override
    Signer[] hashers;

    @Override
    Hash[] hashesFor(UserId userId, Signer hasher)
        {
        Int hashIndex;
        if (!(hashIndex := this.hashIndex(hasher)))
            {
            return [];
            }

        if (userId.is(String))
            {
            Hash? hash = Null;
            if (User user := loadUserByName(userId))
                {
                hash := user.passwordHashes.hashFor(hasher);
                }
            return [hash?] : [];
            }

        User[] users = loadUsersByHash(userId);
        switch (users.size)
            {
            case 0:
                return [];

            case 1:
                return [users[0].passwordHashes.hashFor(hasher)?] : [];

            default:
                Hash[] pwdHashes = new Hash[];
                for (User user : users)
                    {
                    if (Hash pwdHash := user.passwordHashes.hashFor(hasher))
                        {
                        pwdHashes += pwdHash;
                        }
                    }
                return pwdHashes.freeze(inPlace=True);
            }
        }

    @Override
    conditional (String, Set<String>) authenticateHash(UserId userId, Hash pwdHash, Signer hasher)
        {
        conditional (String, Set<String>) authenticateUser(User user, Hash pwdHash, Signer hasher)
            {
            if (Hash actualHash := user.passwordHashes.hashFor(hasher),
                    pwdHash == actualHash)
                {
                return True, user.userName, loadUserRoles(user) ?: assert;
                }

            return False;
            }

        if (userId.is(String))
            {
            if (User user := loadUserByName(userId))
                {
                return authenticateUser(user, pwdHash, hasher);
                }

            return False;
            }

        // look up the user by the user hash
        User[] users = loadUsersByHash(userId);
        for (User user : users)
            {
            if ((String userName, Set<String> roleNames) := authenticateUser(user, pwdHash, hasher))
                {
                return True, userName, roleNames;
                }
            }

        return False;
        }


    // ----- internal ------------------------------------------------------------------------------

    /**
     * TODO
     */
    static Signer hasherByName(String algorithm)
        {
        @Inject crypto.Algorithms algorithms;
        return algorithms.hasherFor(algorithm)?;
        TODO CP alternative if algorithm unavailable
        }

    /**
     * Obtain the index within the values (arrays of hashed passwords) stored in `pwdsByUser` that
     * corresponds to a particular [hasher](Signer).
     *
     * @param hasher  the [hasher](Signer) that indicates which digested passwords to hash or verify
     *
     * @return the index within the arrays of hashed passwords stored as the values of the
     *         `pwdsByUser` map
     */
    protected conditional Int hashIndex(Signer hasher)
        {
        Loop: for (Signer? eachHasher : supportedHashers)
            {
            if (eachHasher.as(Object) == hasher.as(Object))
                {
                return True, Loop.count;
                }
            }

        return switch (hasher.algorithm.name)
            {
            case "SHA-512-256": (True, 0);
            case "SHA-256"    : (True, 1);
            case "MD5"        : (True, 2);
            default           : False;
            };
        }

    /**
     * TODO
     */
    static Hash? extractHash(HashInfo hashes, Int index)
        {
        return switch (index)
            {
            case 0: hashes.md5;
            case 1: hashes.sha256;
            case 2: hashes.sha512_256;
            default: assert;
            };
        }

    /**
     * TODO
     */
    protected conditional User loadUserByName(String userName)
        {
        using (authSchema.dbConnection.createTransaction(readOnly=True))
            {
            return authSchema.users.findByName(userName);
            }
        }

    /**
     * TODO
     */
    protected User[] loadUsersByHash(Hash hash)
        {
        using (authSchema.dbConnection.createTransaction(readOnly=True))
            {
            return authSchema.users.findByUserHash(hash);
            }
        }

    /**
     * TODO
     */
    protected conditional Set<String> loadUserRoles(User|String user)
        {
        using (authSchema.dbConnection.createTransaction(readOnly=True))
            {
            if (user.is(String))
                {
                if (!(user := authSchema.users.findByName(user)))
                    {
                    return False;
                    }
                }

            Int[] roleIds = user.roleIds; // TODO GG add to end: [] (asserts at NTE #988)
            if (roleIds.empty)
                {
                return True, [];
                }

            Roles roles = authSchema.roles;
            HashSet<String> roleNames = new HashSet();
            for (Int roleId : roleIds)
                {
                if (Role role := roles.get(roleId))
                    {
                    roleNames += role.roleName;
                    // TODO GG roleNames += role.altNames;
                    roleNames.addAll(role.altNames);
                    }
                }
            return True, roleNames.freeze(True);
            }
        }
    }