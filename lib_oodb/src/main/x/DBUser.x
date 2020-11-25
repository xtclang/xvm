/**
 * A database user or group.
 */
interface DBUser
    {
    /**
     * The perpetual-unique id of the DBUser within this database.
     */
    @RO UInt id;

    /**
     * The temporal-unique user or group name.
     */
    @RO String name;

    /**
     * True if this user is active.
     */
    @RO Boolean active;

    /**
     * True iff this `DBUser` object represents a group of users, and not an individual user.
     */
    @RO Boolean group;

    /**
     * Groups that this user (or group) belongs to.
     */
    @RO Set<DBUser> groups;

    /**
     * Explicit permissions for this user.
     */
    @RO Set<Permission> permissions;

    /**
     * Explicit permission revocations for this user.
     */
    @RO Set<Permission> revocations;

    /**
     * Determine whether the specified request is allowed to be made by this user.
     *
     * @param request  the [Permission] to test for
     *
     * @return `True` iff this `DBUser` is permitted to execute the specified request
     */
    Boolean isAllowed(Permission request)
        {
        // check the local permissions for an exact match
        if (permissions.contains(request))
            {
            return True;
            }

        // check the local revocations for an exact match
        if (revocations.contains(request))
            {
            return False;
            }

        // evaluate the local permissions
        NextPermission: for (Permission permission : permissions)
            {
            if (permission.covers(request))
                {
                // make sure that there is no even-more-specific revocation that would override the
                // permission (and note that permissions take precedence over identical revocations)
                for (Permission revocation : revocations)
                    {
                    if (revocation.covers(permission) && revocation != permission)
                        {
                        continue NextPermission;
                        }
                    }
                return True;
                }
            }

        // evaluate the local revocations
        if (revocations.any(revocation -> revocation.covers(request)))
            {
            return False;
            }

        // if this user is a member of any group that is allowed that permission, then this user
        // also is allowed that permission
        return groups.any(group -> group.isAllowed(request));
        }
    }
