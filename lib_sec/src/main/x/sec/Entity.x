/**
 * An `Entity` represents someone or something that has a set of permissions and a time-bounded
 * lifecycle. The four primary use cases are: (1) Security Principals, (2) Security Groups,
 * (3) Entitlements, and (4) API keys.
 */
@Abstract const Entity {

    // ----- construction --------------------------------------------------------------------------

    /**
     * Construct an `Entity` from a principal identity, a set of permissions, and a lifetime.
     *
     * @param principalId  the identity of the [Principal] on whose behalf this `Entity` exists
     * @param permissions  explicit permissions (including revocations) for this `Entity`, in order
     *                     of precedence
     * @param validFrom    the point in time before which the `Entity` does not exist
     * @param validUntil   the point in time after which the `Entity` is expired
     * @param status       the explicit `Suspended` or `Revoked` status, or `Null`
     * @param credentials  the credentials for authenticating the `Entity`
     */
    construct(
            Int          principalId,
            Permission[] permissions,
            Time         validFrom,
            Time?        validUntil  = Null,
            Status?      status      = Null,
            Credential[] credentials = [],
            ) {
        this.principalId = principalId;
        this.permissions = permissions;
        this.validFrom   = validFrom;
        this.validUntil  = validUntil;
        this.status      = status == Active ? Null : status;
        this.credentials = credentials;
    }

    assert() {
        assert status == Null || status == Suspended || status == Revoked;
        assert validUntil? >= validFrom;
    }

    /**
     * Create a new `Entity` that is a clone of this `Entity`, but with the specific changes
     * specified.
     *
     * @param principalId  the new `principalId` value, or `Null` to leave unchanged
     * @param permissions  the new permissions to use, or `Null` to leave unchanged
     * @param validFrom    the new `validFrom` value to use, or `Null` to leave unchanged
     * @param validUntil   the new `validUntil` value to use, or `Null` to leave unchanged
     * @param status       the new `status` value to use, or `Null` to leave unchanged; the only
     *                     legal [Status] values to pass are `Active`, `Suspended`, and `Revoked`;
     *                     passing `Active` will result in the [status] of `Null`
     * @param credentials  the new credentials to use, or `Null` to leave unchanged
     *
     * @return the new `Entity` copied from this `Entity` but with the specified changes
     */
    @Abstract Entity with(
            Int?          principalId = Null,
            Permission[]? permissions = Null,
            Time?         validFrom   = Null,
            Time?         validUntil  = Null,
            Status?       status      = Null,
            Credential[]? credentials = Null,
            );

    // ----- properties ----------------------------------------------------------------------------

    /**
     * The identity of the [Principal] on whose behalf this `Entity` was created. For a `Principal`,
     * this is the `Principal`'s own id.
     */
    Int principalId;

    /**
     * Explicit permissions (including revocations) for this `Entity`, in order of precedence.
     */
    Permission[] permissions;

    /**
     * The point in time before which the `Entity` is considered to not yet exist and thus is
     * not valid. Entities must always have an explicit point before which they are not valid.
     */
    Time validFrom;

    /**
     * The optional point in time after which the `Entity` is considered to have expired and is no
     * longer valid.
     */
    Time? validUntil;

    /**
     * Null iff the `Entity` has not been explicitly suspended or revoked; otherwise this indicates
     * the explicit `Suspended` or `Revoked` status.
     */
    Status? status;

    /**
     * The credentials that can be used to authenticate this `Entity`.
     */
    Credential[] credentials;

    // ----- API -----------------------------------------------------------------------------------

    /**
     * * `NotYet` - the [validFrom] time for the `Entity` is in the future
     * * `Active` - the `Entity` is valid and active
     * * `Suspended` - the `Entity` is explicitly suspended
     * * `Revoked` - the `Entity` has been explicitly revoked
     * * `Expired` - the [validUntil] time for the `Entity` is in the past
     */
    enum Status {NotYet, Active, Suspended, Revoked, Expired}

    /**
     * Determine if `this` Entity is valid at some specific time.
     *
     * Note that this does not incorporate any revocations that occurred after this Entity
     * object was instantiated.
     *
     * @param realm  the [Realm] that provided this `Entity`; passing `Null` will determine the
     *               result solely from this `Entity`, ignoring the status of any entities that this
     *               `Entity` delegates to
     * @param at     (optional) the time at which to determine if the `Entity` is valid; if
     *               nothing is specified, then the current time is used
     *
     * @return True iff `this` Entity is valid at the specified time
     */
    Status calcStatus(Realm? realm, Time? at = Null) {
        Status? status = this.status;
        if (status == Suspended || status == Revoked) {
            return status;
        }

        if (at == Null) {
            @Inject Clock clock;
            at = clock.now;
        }

        if (at < validFrom) {
            return NotYet;
        }

        if (at > validUntil?) {
            return Expired;
        }

        return Active;
    }

    /**
     * Determine if the specified [Permission] is allowed for this `Entity`.
     *
     * @param realm       the [Realm] that provided this `Entity`
     * @param permission  the required Permission
     * @param at          (optional) the time at which the permission evaluation should apply to;
     *                    defaults to the current time
     *
     * @return True iff the specified `Permission` is allowed at the specified time
     */
    Boolean permitted(Realm realm, Permission permission, Time? at = Null) {
        if (calcStatus(realm, at) != Active) {
            return False;
        }

        // this base `Entity` implementation is not aware of any other entities to delegate to;
        // sub-classes with entities to delegate to must override this method
        return covers(permission) == True;
    }

    /**
     * Determine if `this` Entity covers `that` Permission.
     *
     * @param that  a required Permission
     *
     * @return `True` if this `Entity`'s permissions explicitly allow the specified permission;
     *         `False` if this `Entity`'s permissions explicitly **dis**allow the specified
     *         permission; or `Null` if this `Entity`'s permissions do not account for the specified
     *         permission
     */
    Boolean? covers(Permission that) {
        for (Permission permission : this.permissions) {
            if (permission.covers(that)) {
                return !permission.revoke;
            }
        }
        return Null;
    }

    /**
     * Suspend this Entity.
     *
     * @return a copy of this Entity, but with a [status] of [Suspended]
     */
    Entity suspend() {
        return status == Suspended ? this : this.with(status=Suspended);
    }

    /**
     * If this Entity is suspended, then undo the suspension.
     *
     * @return a copy of this Entity, but without the [status] of [Suspended]
     */
    Entity unsuspend() {
        return status == Suspended ? this.with(status=Active) : this;
    }

    /**
     * Revoke this Entity.
     *
     * @return a copy of this Entity, but with a [status] of [Revoked]
     */
    Entity revoke() {
        return status == Revoked ? this : this.with(status=Revoked);
    }
}
