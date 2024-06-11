/**
 * An `Entitlement` represents a set of permissions granted to a particular `Principal`.
 */
const Entitlement
        extends Entity {

    // ----- construction --------------------------------------------------------------------------

    /**
     * Construct an `Entitlement`, which represents a grant of a group of permissions on behalf of
     * a `Principal`.
     *
     * @param entitlementId   the identity of the [Entitlement]
     * @param principalId     the identity of the [Principal]
     * @param permissions     explicit permissions (including revocations) for this `Entitlement`,
     *                        in order of precedence
     * @param validFrom       the point in time before which the `Entitlement` does not exist
     * @param validUntil      the point in time after which the `Entitlement` is expired
     * @param status          the explicit `Suspended` or `Revoked` status, or `Null`
     * @param credentials     the credentials for authenticating the `Entitlement`
     * @param conferIdentity  True indicates that the `Entitlement` is used as a means of
     *                        authentication, by explicitly conferring the `Principal`'s identity
     */
    construct(
            Int          entitlementId,
            Int          principalId,
            Permission[] permissions,
            Time         validFrom,
            Time?        validUntil     = Null,
            Status?      status         = Null,
            Credential[] credentials    = [],
            Boolean      conferIdentity = False,
            ) {
        construct Entity(principalId, permissions, validFrom, validUntil, status, credentials);
        this.entitlementId  = entitlementId;
        this.conferIdentity = conferIdentity;
    }

    /**
     * Create a new `Entitlement` that is a clone of this `Entitlement`, but with the specific
     * changes specified.
     *
     * @param principalId     the new `principalId` value, or `Null` to leave unchanged
     * @param permissions     the new permissions to use, or `Null` to leave unchanged
     * @param validFrom       the new `validFrom` value to use, or `Null` to leave unchanged
     * @param validUntil      the new `validUntil` value to use, or `Null` to leave unchanged
     * @param status          the new `status` value to use, or `Null` to leave unchanged; the only
     *                        legal [Status] values to pass are `Active`, `Suspended`, and
     *                        `Revoked`; passing `Active` will result in the [status] of `Null`
     * @param credentials     the new credentials to use, or `Null` to leave unchanged
     * @param entitlementId   the new `entitlementId` value, or `Null` to leave unchanged
     * @param conferIdentity  the new `conferIdentity` value, or `Null` to leave unchanged
     *
     * @return the new `Entitlement` copied from this `Entitlement` but with the specified changes
     */
    @Override
    Entitlement with(
            Int?          principalId    = Null,
            Permission[]? permissions    = Null,
            Time?         validFrom      = Null,
            Time?         validUntil     = Null,
            Status?       status         = Null,
            Credential[]? credentials    = Null,
            Int?          entitlementId  = Null,
            Boolean?      conferIdentity = Null,
            ) {
        return new Entitlement(
                entitlementId  = entitlementId  ?: this.entitlementId,
                principalId    = principalId    ?: this.principalId,
                permissions    = permissions    ?: this.permissions,
                validFrom      = validFrom      ?: this.validFrom,
                validUntil     = validUntil     ?: this.validUntil,
                status         = status         ?: this.status,
                credentials    = credentials    ?: this.credentials,
                conferIdentity = conferIdentity ?: this.conferIdentity,
        );
    }

    // ----- properties ----------------------------------------------------------------------------

    /**
     * The unique identity of the [Entitlement].
     */
    Int entitlementId;

    /**
     * True iff the `Entitlement` can be used as a `Principal` identity for means of authentication.
     */
    Boolean conferIdentity;

    // ----- API -----------------------------------------------------------------------------------

    @Override
    Status calcStatus(Realm? realm, Time? at = Null) {
        Status entitlementStatus = super(Null, at);
        Principal? principal = realm?.loadPrincipal(principalId)? : Null;
        return minOf(entitlementStatus, principal?.calcStatus(realm, at) : NotYet);
    }

    @Override
    Boolean permitted(Realm realm, Permission permission, Time? at = Null) {
        // verify Entitlement status and that the permission is allowed as part of the Entitlement
        if (!super(realm, permission, at)) {
            return False;
        }

        // in theory, the Principal could have lost the permission since the Entitlement was
        // created, so verify that the Principal still has the required permission
        if (Principal principal := realm.loadPrincipal(principalId)) {
            return principal.permitted(realm, permission, at);
        } else {
            return False;
        }
    }
}
