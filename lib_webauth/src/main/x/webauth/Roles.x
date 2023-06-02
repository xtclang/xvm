import oodb.DBMap;

/**
 * Represents a table of user Role objects.
 */
mixin Roles
        into DBMap<Int, Role> {
    /**
     * TODO
     */
    conditional Role createRole(String|String[] roleName,
                                String?         description = Null
                               ) {
        // split the passed in names into a primary and alternatives
        String   primaryName;
        String[] altNames = [];
        if (roleName.is(String)) {
            primaryName = roleName;
        } else {
            switch (Int n = roleName.size) {
            default:
                altNames = roleName[1..<n];
                continue;
            case 1:
                primaryName = roleName[0];
                break;

            case 0:
                return False;
            }
        }

        // make sure none of the names is already taken
        if (findByName(primaryName) || !altNames.empty && findByNames(altNames)) {
            return False;
        }

        AuthSchema schema = dbParent.as(AuthSchema);
        Int        roleId = schema.roleId.next();
        Role       role   = new Role(roleId, primaryName, altNames, description ?: $"{primaryName} role");
        return putIfAbsent(roleId, role)
                ? (True, role)
                : False;
    }

    /**
     * Find the role with the specified name:
     *
     *     if (Role role := roles.findByName(roleName)) ...
     *
     * @param name  the Roles's `roleName`
     *
     * @return True iff the specified role name was found
     * @return (conditional) the Role with the specified name
     */
    conditional Role findByName(String name) {
        return values.any(r -> r.roleName == name || r.altNames.contains(name));
    }

    /**
     * Find the roles with the specified names:
     *
     *     if (Role[] roles := roles.findByNames(roleNames)) ...
     *
     * @param names  one more more `roleName` or `altNames` of Roles
     *
     * @return True iff the specified role names were found
     * @return (conditional) an array with one Role for each specified name
     */
    conditional Role[] findByNames(Iterable<String> names) {
        Role[]  roles = new Role[];
        Boolean any   = False;
        for (String name : names) {
            any = True;

            if (Role role := findByName(name)) {
                roles += role;
            } else {
                return False;
            }
        }

        return any
                ? (True, roles)
                : False;
    }
}

