/**
 * A `Principal` represents a named user for authentication and authorization purposes.
 */
const Principal
        extends Entity {

    // ----- construction --------------------------------------------------------------------------

    /**
     * Construct a `Principal`, which represents a named user.
     *
     * @param principalId  the identity of the [Principal]
     * @param name         a human-readable name or description of the `Principal`
     * @param permissions  explicit permissions (including revocations) for this `Principal`, in
     *                     order of precedence
     * @param validFrom    the point in time before which the `Principal` does not exist; defaults
     *                     to the current time
     * @param validUntil   the point in time after which the `Principal` is expired
     * @param status       the explicit `Suspended` or `Revoked` status, or `Null`
     * @param groupIds     the list of [Group] ids that this `Principal` belongs to
     * @param credentials  the credentials for authenticating the `Principal`
     */
    construct(
            Int          principalId,
            String       name,
            Permission[] permissions = [],
            Time?        validFrom   = Null,
            Time?        validUntil  = Null,
            Status?      status      = Null,
            Int[]        groupIds    = [],
            Credential[] credentials = [],
            ) {
        construct Entity(principalId, name, permissions, validFrom, validUntil, status, groupIds);
        this.credentials = credentials;
    }

    /**
     * Create a new `Principal` that is a clone of this `Principal`, but with the specific changes
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
     * @param principalId  the new `principalId` value, or `Null` to leave unchanged; this must be
     *                     the same value as the subjectId value if both are non-`Null`
     *
     * @return the new `Principal` copied from this `Principal` but with the specified changes
     */
    @Override
    Principal with(
            Int?          subjectId   = Null,
            String?       name        = Null,
            Permission[]? permissions = Null,
            Time?         validFrom   = Null,
            Time?         validUntil  = Null,
            Status?       status      = Null,
            Credential[]? credentials = Null,
            Int[]?        groupIds    = Null,
            Int?          principalId = Null,
            ) {
        assert subjectId? == principalId?;
        return new Principal(
                principalId    = principalId ?: subjectId ?: this.principalId,
                name           = name                     ?: this.name,
                permissions    = permissions              ?: this.permissions,
                validFrom      = validFrom                ?: this.validFrom,
                validUntil     = validUntil               ?: this.validUntil,
                status         = status                   ?: this.status,
                groupIds       = groupIds                 ?: this.groupIds,
                credentials    = credentials              ?: this.credentials,
        );
    }

    // ----- properties ----------------------------------------------------------------------------

    /**
     * The identity of the `Principal`.
     */
    @RO Int principalId.get() = entityId;

    /**
     * The credentials that can be used to authenticate this `Principal`.
     */
    @Override
    Credential[] credentials;
}
