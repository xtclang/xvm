/**
 * An `Entity` represents a user or group for purposes of authorization.
 */
@Abstract const Entity
        extends Subject{

    // ----- construction --------------------------------------------------------------------------

    /**
     * Construct an `Entity` from an identity, a set of permissions, and a lifetime.
     *
     * @param entityId     the identity of the `Entity`
     * @param name         a human-readable name or description for the `Entity`
     * @param permissions  explicit permissions (including revocations) for this `Entity`, in order
     *                     of precedence
     * @param validFrom    the point in time before which the `Entity` does not exist; defaults to
     *                     the current time
     * @param validUntil   the point in time after which the `Entity` is expired
     * @param status       the explicit `Suspended` or `Revoked` status, or `Null`
     * @param groupIds     the list of [Group] ids that this `Entity` belongs to
     */
    construct(
            Int          entityId,
            String       name,
            Permission[] permissions = [],
            Time?        validFrom   = Null,
            Time?        validUntil  = Null,
            Status?      status      = Null,
            Int[]        groupIds    = [],
            ) {
        construct Subject(entityId, name, permissions, validFrom, validUntil, status);
        this.groupIds = groupIds.distinct().toArray(Constant);
    }

    /**
     * Create a new `Entity` that is a clone of this `Entity`, but with the specific changes
     * specified.
     *
     * @param subjectId    the new `subjectId` value, or `Null` to leave unchanged
     * @param name         the new `name` value, or `Null` to leave unchanged
     * @param permissions  the new permissions to use, or `Null` to leave unchanged
     * @param validFrom    the new `validFrom` value to use, or `Null` to leave unchanged
     * @param validUntil   the new `validUntil` value to use, or `Null` to leave unchanged
     * @param status       the new `status` value to use, or `Null` to leave unchanged; the only
     *                     legal [Status] values to pass are `Active`, `Suspended`, and `Revoked`;
     *                     passing `Active` will result in the [status] of `Null`
     * @param credentials  the new credentials to use, or `Null` to leave unchanged
     * @param groupIds     the new `groupIds` value, or `Null` to leave unchanged
     *
     * @return the new `Entity` copied from this `Entity` but with the specified changes
     */
    @Override
    @Abstract Entity with(
            Int?          subjectId   = Null,
            String?       name        = Null,
            Permission[]? permissions = Null,
            Time?         validFrom   = Null,
            Time?         validUntil  = Null,
            Status?       status      = Null,
            Credential[]? credentials = Null,
            Int[]?        groupIds    = Null,
            );

    // ----- properties ----------------------------------------------------------------------------

    /**
     * The identity of the `Entity`.
     */
    @RO Int entityId.get() = subjectId;

    /**
     * [Group]s that this `Entity` belongs to.
     */
    Int[] groupIds;

    // ----- API -----------------------------------------------------------------------------------

    @Override
    Status calcStatus(Realm? realm, Time? at = Null) {
        Status status = super(Null, at);
        if (realm == Null || status != Active) {
            return status;
        }

        Int[] groupIds = this.groupIds;
        if (!groupIds.empty) {
            // at least one of the groups that this Principal derives from must be active
            Status? groupStatus = Null;
            for (Int groupId : groupIds) {
                if (Group group := realm.readGroup(groupId)) {
                    Status curStatus = group.calcStatus(realm, at);
                    if (curStatus == Active) {
                        return Active;
                    }
                    groupStatus = maxOf(groupStatus?, curStatus) : curStatus;
                }
            }
            return groupStatus ?: NotYet;   // NotYet is reported if we failed every read
        }

        return Active;
    }

    @Override
    Boolean permitted(Realm realm, Permission permission, Time? at = Null) {
        switch (covers(permission)) {
        case True:
            // this Entity grants the permission; just verify that this Entity is Active
            return calcStatus(realm, at) == Active;
        case False:
            // this Entity revokes the permission
            return False;
        }

        // this Entity neither explicitly grants nor revokes the permission; regardless of all else,
        // if this Entity is itself not Active, then nothing is permitted
        Status status = calcStatus(Null, at);
        if (status != Active) {
            return False;
        }

        // at least one Group must permit the requested permission
        for (Int groupId : groupIds) {
            if (Group group := realm.readGroup(groupId), group.permitted(realm, permission, at)) {
                return True;
            }
        }

        return False;
    }
}
