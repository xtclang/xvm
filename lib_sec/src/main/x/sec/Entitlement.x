/**
 * An `Entitlement` represents a set of permissions granted to a particular `Principal`.
 */
const Entitlement
        extends Subject {

    // ----- construction --------------------------------------------------------------------------

    /**
     * Construct an `Entitlement`, which represents a grant of a group of permissions on behalf of
     * a `Principal`.
     *
     * @param entitlementId   the identity of the `Entitlement`
     * @param name            a human-readable name or description for the `Entitlement`
     * @param principalId     the identity of the [Principal] authorizing the `Entitlement`
     * @param permissions     explicit permissions (including revocations) for this `Entitlement`,
     *                        in order of precedence
     * @param validFrom       the point in time before which the `Entitlement` does not exist;
     *                        defaults to the current time
     * @param validUntil      the point in time after which the `Entitlement` is expired
     * @param status          the explicit `Suspended` or `Revoked` status, or `Null`
     * @param credentials     the credentials for authenticating the `Entitlement`
     * @param conferIdentity  True indicates that the `Entitlement` is used as a means of
     *                        authentication, by explicitly conferring the `Principal`'s identity
     */
    construct(
            Int          entitlementId,
            String       name,
            Int          principalId,
            Permission[] permissions    = [],
            Time?        validFrom      = Null,
            Time?        validUntil     = Null,
            Status?      status         = Null,
            Credential[] credentials    = [],
            Boolean      conferIdentity = False,
            ) {
        construct Subject(entitlementId, name, permissions, validFrom, validUntil, status);
        this.credentials    = credentials;
        this.principalId    = principalId;
        this.conferIdentity = conferIdentity;
    }

    /**
     * Create a new `Entitlement` that is a clone of this `Entitlement`, but with the specific
     * changes specified.
     *
     * @param subjectId       the new `subjectId` value, or `Null` to leave unchanged
     * @param name            the new `name` value, or `Null` to leave unchanged
     * @param permissions     the new permissions to use, or `Null` to leave unchanged
     * @param validFrom       the new `validFrom` value to use, or `Null` to leave unchanged
     * @param validUntil      the new `validUntil` value to use, or `Null` to leave unchanged
     * @param status          the new `status` value to use, or `Null` to leave unchanged; the only
     *                        legal [Status] values to pass are `Active`, `Suspended`, and
     *                        `Revoked`; passing `Active` will result in the [status] of `Null`
     * @param credentials     the new credentials to use, or `Null` to leave unchanged
     * @param entitlementId   the new `entitlementId` value, or `Null` to leave unchanged; this must
     *                        the same value as the subjectId value if both are non-`Null`
     * @param principalId     the new `principalId` value, or `Null` to leave unchanged
     * @param conferIdentity  the new `conferIdentity` value, or `Null` to leave unchanged
     *
     * @return the new `Entitlement` copied from this `Entitlement` but with the specified changes
     */
    @Override
    Entitlement with(
            Int?          subjectId      = Null,
            String?       name           = Null,
            Permission[]? permissions    = Null,
            Time?         validFrom      = Null,
            Time?         validUntil     = Null,
            Status?       status         = Null,
            Credential[]? credentials    = Null,
            Int?          entitlementId  = Null,
            Int?          principalId    = Null,
            Boolean?      conferIdentity = Null,
            ) {
        assert subjectId? == entitlementId?;
        return new Entitlement(
                entitlementId  = entitlementId ?: subjectId ?: this.entitlementId,
                name           = name                      ?: this.name,
                principalId    = principalId               ?: this.principalId,
                permissions    = permissions               ?: this.permissions,
                validFrom      = validFrom                 ?: this.validFrom,
                validUntil     = validUntil                ?: this.validUntil,
                status         = status                    ?: this.status,
                credentials    = credentials               ?: this.credentials,
                conferIdentity = conferIdentity            ?: this.conferIdentity,
        );
    }

    // ----- properties ----------------------------------------------------------------------------

    /**
     * The unique identity of the `Entitlement`.
     */
    Int entitlementId.get() = subjectId;

    /**
     * The credentials that can be used to authenticate this `Entitlement`.
     */
    @Override
    Credential[] credentials;

    /**
     * The `Principal` that created the `Entitlement`.
     */
    Int principalId;

    /**
     * True iff the `Entitlement` can be used as a [Principal] identity for means of authentication.
     */
    Boolean conferIdentity;

    // ----- API -----------------------------------------------------------------------------------

    @Override
    Status calcStatus(Realm? realm, Time? at = Null) {
        Status entitlementStatus = super(Null, at);
        if (realm == Null) {
            return entitlementStatus;
        }

        Status principalStatus = realm.readPrincipal(principalId)?.calcStatus(realm, at) : NotYet;
        return minOf(entitlementStatus, principalStatus);
    }

    @Override
    Boolean permitted(Realm realm, Permission permission, Time? at = Null) {
        // verify Entitlement status and that the permission is allowed as part of the Entitlement
        if (!super(realm, permission, at)) {
            return False;
        }

        // in theory, the Principal could have lost the permission since the Entitlement was
        // created, so verify that the Principal still has the required permission
        if (Principal principal := realm.readPrincipal(principalId)) {
            return principal.permitted(realm, permission, at);
        } else {
            return False;
        }
    }
}
