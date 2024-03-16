import crypto.Signer;

import ecstasy.collections.CollectImmutableArray;

import oodb.DBMap;

import DBRealm.HashInfo;

/**
 * A DBMap of User objects. There are conceptually three "primary keys" for a User:
 *
 * * The (unique) user name (the login name)
 * * An internal unique integer identifier assigned to the user
 * *
 *
 * TODO need to protect uniqueness of name
 */
mixin Users
        into DBMap<Int, User> {
    /**
     * Create a user with the specified attributes.
     *
     * @param realm     the [DBRealm] that is using this database for its auth data
     * @param userName  the user name to register
     * @param password  (optional) the initial password for the user
     * @param roleName  (optional) the role or roles that the user is initially assigned
     *
     * @return True iff the new user could be successfully created
     * @return (conditional) the new User object
     */
    conditional User createUser(DBRealm         realm,
                                String          userName,
                                String?         password = Null,
                                String|String[] roleName = []) {
        if (findByName(userName)) {
            return False;
        }

        (HashInfo userHashes, HashInfo pwdHashes) = realm.createHashes(userName, password);

        // look up the role names and convert them to role IDs
        Int[]      roleIds;
        String[]   roleNames = [roleName.is(String)?] : roleName;
        AuthSchema schema    = dbParent.as(AuthSchema);
        if (Role[] roleList := schema.roles.findByNames(roleNames)) {
            roleIds = roleList.map(r -> r.roleId, CollectImmutableArray.of(Int));
        } else {
            return False;
        }

        Int  userId = schema.userId.next();
        User user   = new User(userId, userName, userHashes, pwdHashes, roleIds);
        return putIfAbsent(userId, user)
                ? (True, user)
                : False;
    }

    /**
     * Find the user with the specified name:
     *
     *     if (User user := users.findByName(loginId)) ...
     *
     * @param name  the User's `userName`
     */
    conditional User findByName(String name) {
        return values.any(u -> u.userName == name);
    }

    /**
     * Find the users with the specified user hash:
     *
     *     User[] users := users.findByHash(bytes);
     *
     * @param name  a hash that must be present in the User's `userHashes`
     */
    immutable User[] findByUserHash(Hash hash) {
        return switch(hash.size) {
            case 128 / 8: values.filter(u -> u.userHashes.md5 == hash, CollectImmutableArray.of(User));
            case 256 / 8: values.filter(u -> u.userHashes.sha256     == hash
                                          || u.userHashes.sha512_256 == hash, CollectImmutableArray.of(User));
            default: [];
        };
    }
}