/**
 * An `Entitlement` represents a set of permissions granted to a particular user.
 *
 * The `Entitlement` holds references to [Permission] objects, which are themselves "terminals",
 * i.e. the `Permissions` do not ), but otherwise acts as a data "terminal", in that it
 */
const Entitlement {

    // ----- construction --------------------------------------------------------------------------

    /**
     * Construct an Entitlement by associating a user with some permissions.
     *
     * @param user         the user id
     * @param permissions  one or more permissions as an Iterable object (such as an array)
     */
    construct(String user, Permission|Iterable<Permission> permissions) {
        this.user        = user;
        this.permissions = normalize(permissions);
    }

    assert() {
        assert:arg checkId(user);
    }

    // ----- properties ----------------------------------------------------------------------------

    /**
     * The identity of the user on whose behalf this entitlement was created.
     */
    String user;

    /**
     * One or more permissions that are specified as part of this entitlement.
     */
    Permission|Set<Permission> permissions;

    // ----- helpers -------------------------------------------------------------------------------

    /**
     * Helper to validate the high level structure of a user identity.
     *
     * @param id  a String containing a user identity
     *
     * @return `True` iff the `id` has a valid format
     */
    static Boolean checkId(String id) = Permission.checkId(id) && id != Permission.All;

    /**
     * Helper to normalize the structure of the permissions that make up this entitlement.
     *
     * @param permissions  either a single [Permission] or a Iterable container (such as a list,
     *                     array, or set) of at least one [Permission] objects
     *
     * @return either a `Permission` or a `ListSet<Permission>`
     */
    static Permission|Set<Permission> normalize(Permission|Iterable<Permission> permissions) {
        // a single permission is allowed as-is
        if (permissions.is(Permission)) {
            return permissions;
        }

        // 0 permissions is not allowed
        assert:arg !permissions.empty;

        // 1 permissions does not require a Set to be wrapped around it
        if (permissions.size == 1) {
            return permissions.iterator().take();
        }

        // >1 permissions are held in a ListSet
        return permissions.is(ListSet) ? permissions : new ListSet(permissions).freeze(True);
    }

    // ----- API -----------------------------------------------------------------------------------

    /**
     * Determine if `this` Entitlement covers `that` Permission.
     *
     * @param that  a required Permission
     *
     * @return True iff the scope of `this` Entitlement fully covers `that` Permission
     */
    Boolean covers(Permission that) {
        return permissions.is(Permission)
                ? permissions.covers(that)
                : permissions.any(p -> p.covers(that));
    }
}
