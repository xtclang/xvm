/**
 * A `Principal` represents a named user or group for authentication and authorization purposes.
 */
const Principal
        extends Entity {

    // ----- construction --------------------------------------------------------------------------

    /**
     * Construct a `Principal`, which represents an individual or a group.
     *
     * @param principalId   the identity of the [Principal]
     * @param name          the name of the individual or group
     * @param permissions   explicit permissions (including revocations) for this `Principal`, in
     *                      order of precedence
     * @param validFrom     the point in time before which the `Principal` does not exist
     * @param validUntil    the point in time after which the `Principal` is expired
     * @param status        the explicit `Suspended` or `Revoked` status, or `Null`
     * @param credentials   the credentials for authenticating the `Principal`
     * @param groupIds      the list of `Principal` ids of groups that this `Principal` belongs to
     * @param group         `True` iff this `Principal` is a group
     * @param entitlements  the list `Entitlement` ids
     */
    construct(
            Int          principalId,
            String       name,
            Permission[] permissions,
            Time         validFrom,
            Time?        validUntil     = Null,
            Status?      status         = Null,
            Credential[] credentials    = [],
            Int[]        groupIds       = [],
            Boolean      group          = False,
            Int[]        entitlementIds = [],
            ) {
        construct Entity(principalId, permissions, validFrom, validUntil, status, credentials);
        this.name           = name;
        this.groupIds       = groupIds;
        this.group          = group;
        this.entitlementIds = entitlementIds;
    }

    assert() {
        assert entitlementIds.empty || !group as "Groups cannot issue Entitlements";
        assert credentials.empty || !group as "Groups cannot have Credentials";
    }

    /**
     * Create a new `Principal` that is a clone of this `Principal`, but with the specific changes
     * specified.
     *
     * @param principalId     the new `principalId` value, or `Null` to leave unchanged
     * @param permissions     the new permissions to use, or `Null` to leave unchanged
     * @param validFrom       the new `validFrom` value to use, or `Null` to leave unchanged
     * @param validUntil      the new `validUntil` value to use, or `Null` to leave unchanged
     * @param status          the new `status` value to use, or `Null` to leave unchanged; the only
     *                        legal [Status] values to pass are `Active`, `Suspended`, and
     *                        `Revoked`; passing `Active` will result in the [status] of `Null`
     * @param credentials     the new credentials to use, or `Null` to leave unchanged
     * @param name            the new `name` value, or `Null` to leave unchanged
     * @param groupIds        the new `groupIds` value, or `Null` to leave unchanged
     * @param group           the new `group` value, or `Null` to leave unchanged
     * @param entitlementIds  the new `entitlementIds` value, or `Null` to leave unchanged
     *
     * @return the new `Principal` copied from this `Principal` but with the specified changes
     */
    @Override
    Principal with(
            Int?          principalId    = Null,
            Permission[]? permissions    = Null,
            Time?         validFrom      = Null,
            Time?         validUntil     = Null,
            Status?       status         = Null,
            Credential[]? credentials    = Null,
            String?       name           = Null,
            Int[]?        groupIds       = Null,
            Boolean?      group          = Null,
            Int[]?        entitlementIds = Null,
            ) {
        return new Principal(
                principalId    = principalId    ?: this.principalId,
                name           = name           ?: this.name,
                permissions    = permissions    ?: this.permissions,
                validFrom      = validFrom      ?: this.validFrom,
                validUntil     = validUntil     ?: this.validUntil,
                status         = status         ?: this.status,
                credentials    = credentials    ?: this.credentials,
                groupIds       = groupIds       ?: this.groupIds,
                group          = group          ?: this.group,
                entitlementIds = entitlementIds ?: this.entitlementIds,
        );
    }

    // ----- properties ----------------------------------------------------------------------------

    /**
     * The principal's name, which is useful for auditing, debugging, etc.
     */
    String name;

    /**
     * True iff this `Principal` object represents a group of `Principal`s, and not an "individual".
     */
    Boolean group;

    /**
     * Groups that this user (or group) belongs to.
     */
    Int[] groupIds;

    /**
     * Entitlement ids created by (or on behalf of) this Principal. Only an individual (i.e. not a
     * group) can create an entitlement.
     */
    Int[] entitlementIds = [];

    // ----- API -----------------------------------------------------------------------------------

    @Override
    Status calcStatus(Realm? realm, Time? at = Null) {
        Status status = super(Null, at);
        if (realm == Null || status != Active) {
            return status;
        }

        Int[] groupIds = this.groupIds;
        FindAtLeastOneActiveGroup: if (!groupIds.empty) {
            // at least one of the groups that this Principal derives from must be active
            Status? groupStatus = Null;
            for (Int groupId : groupIds) {
                if (Principal group := realm.loadPrincipal(groupId)) {
                    Status curStatus = group.calcStatus(realm, at);
                    if (curStatus == Active) {
                        break FindAtLeastOneActiveGroup;
                    }
                    groupStatus = maxOf(groupStatus?, curStatus) : curStatus;
                }
            }
            return groupStatus ?: NotYet;   // NotYet is reported if we failed every load
        }

        return Active;
    }

    @Override
    Boolean permitted(Realm realm, Permission permission, Time? at = Null) {
        if (calcStatus(realm, at) != Active) {
            return False;
        }

        if (super(realm, permission, at)) {
            return True;
        }

        // check groups; at least one group must give permission
        for (Int groupId : groupIds) {
            if (Principal group := realm.loadPrincipal(groupId)) {
                if (group.permitted(realm, permission, at)) {
                    return True;
                }
            }
        }

        return False;
    }
}
