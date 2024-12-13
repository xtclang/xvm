/**
 * A `Group` represents a named group in a hierarchy of groups of [Principal]s, for organizational
 * and authorization purposes.
 */
const Group
        extends Entity {

    // ----- construction --------------------------------------------------------------------------

    /**
     * Construct a `Group`, which represents a named user.
     *
     * @param groupId      the identity of the [Group]
     * @param name         a human-readable name or description of the `Group`
     * @param permissions  explicit permissions (including revocations) for this `Group`, in
     *                     order of precedence
     * @param validFrom    the point in time before which the `Group` does not exist; defaults to
     *                     the current time
     * @param validUntil   the point in time after which the `Group` is expired
     * @param status       the explicit `Suspended` or `Revoked` status, or `Null`
     * @param groupIds     the list of [Group] ids that this `Group` belongs to
     */
    construct(
            Int          groupId,
            String       name,
            Permission[] permissions = [],
            Time?        validFrom   = Null,
            Time?        validUntil  = Null,
            Status?      status      = Null,
            Int[]        groupIds    = [],
            ) {
        construct Entity(groupId, name, permissions, validFrom, validUntil, status, groupIds);
    }

    assert() {
        assert:arg !name.empty;
    }

    /**
     * Create a new `Group` that is a clone of this `Group`, but with the specific changes
     * specified.
     *
     * @param subjectId    the new `subjectId` value, or `Null` to leave unchanged
     * @param name         the new `name` value, or `Null` to leave unchanged
     * @param permissions  the new permissions to use, or `Null` to leave unchanged
     * @param validFrom    the new `validFrom` value to use, or `Null` to leave unchanged
     * @param validUntil   the new `validUntil` value to use, or `Null` to leave unchanged
     * @param status       the new `status` value to use, or `Null` to leave unchanged; the only
     *                     legal [Status] values to pass are `Active`, `Suspended`, and
     *                     `Revoked`; passing `Active` will result in the [status] of `Null`
     * @param credentials  the new credentials to use, or `Null` to leave unchanged
     * @param groupIds     the new `groupIds` value, or `Null` to leave unchanged
     * @param groupId      the new `groupId` value, or `Null` to leave unchanged; this must be the
     *                     same value as the subjectId value if both are non-`Null`
     *
     * @return the new `Group` copied from this `Group` but with the specified changes
     */
    @Override
    Group with(
            Int?          subjectId   = Null,
            String?       name        = Null,
            Permission[]? permissions = Null,
            Time?         validFrom   = Null,
            Time?         validUntil  = Null,
            Status?       status      = Null,
            Credential[]? credentials = Null,
            Int[]?        groupIds    = Null,
            Int?          groupId     = Null,
            ) {
        assert:arg subjectId? == groupId?;
        assert:arg credentials?.empty;
        return new Group(
                groupId     = groupId ?: subjectId ?: this.groupId,
                name        = name                 ?: this.name,
                permissions = permissions          ?: this.permissions,
                validFrom   = validFrom            ?: this.validFrom,
                validUntil  = validUntil           ?: this.validUntil,
                status      = status               ?: this.status,
                groupIds    = groupIds             ?: this.groupIds,
        );
    }

    // ----- properties ----------------------------------------------------------------------------

    /**
     * The identity of the `Group`.
     */
    @RO Int groupId.get() = entityId;

    @Override
    @RO Credential[] credentials.get() = [];

    // ----- API -----------------------------------------------------------------------------------

    /**
     * Check for any infinite loops in the group hierarchy, starting from this group.
     *
     * @param realm  the [Realm] to obtain `Group`s from
     *
     * @return True iff an infinite loop is detected
     * @return (conditional) the `Group` id to blame for the infinite loop (which may not be this
     *         `Group`'s id)
     */
    conditional Int circularDependency(Realm realm) {
        return groupIds.empty ? False : detectLoop(realm, new HashMap<Int, Boolean>());
    }

    // ----- internal ------------------------------------------------------------------------------

    /**
     * Detect an infinite loop in the group hierarchy, starting from this group.
     *
     * @param realm       the [Realm] to obtain `Group`s from
     * @param inProgress  contains the `Group`s currently being visited (value==`True`) and the
     *                    `Group`s already verified to be non-looping (value==`False`)
     *
     * @return True iff an infinite loop is detected
     * @return (conditional) the `Group` id to blame for the infinite loop
     */
    private conditional Int detectLoop(Realm realm, HashMap<Int, Boolean> inProgress) {
        // can't have a loop if this group is not part of any other group
        if (groupIds.empty) {
            return False;
        }

        // add this group to the inProgress list
        inProgress.put(groupId, True);

        for (Int parentId : groupIds) {
            // check if we've already tested this group
            if (Boolean looping := inProgress.get(parentId)) {
                if (looping) {
                    return True, parentId;
                }
                // else it was already checked and verified to be non-looping
            } else {
                // the parent group has not yet been checked
                if (    Group parent  := realm.readGroup(parentId),
                        Int   blameId := parent.detectLoop(realm, inProgress)) {
                    return True, blameId;
                }
            }
        }

        // cache the result of checking this group
        inProgress.put(groupId, False);

        return False;
    }
}
