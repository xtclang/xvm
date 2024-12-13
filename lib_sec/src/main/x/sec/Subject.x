import ecstasy.collections.VirtualHasher;

import ecstasy.maps.HasherMap;

/**
 * A `Subject` represents a source of security authorization capability. The four primary use cases
 * are: (1) Security Principals, (2) Security Groups, (3) Entitlements, and (4) API keys.
 */
@Abstract const Subject {

    // ----- construction --------------------------------------------------------------------------

    /**
     * Construct an `Subject` from an identity, a set of permissions, and a lifetime.
     *
     * @param subjectId    the identity of the `Subject`
     * @param name         a human-readable name or description for the `Subject`
     * @param permissions  explicit permissions (including revocations) for this `Subject`, in order
     *                     of precedence
     * @param validFrom    the point in time before which the `Subject` does not exist; defaults to
     *                     the current time
     * @param validUntil   the point in time after which the `Subject` is expired
     * @param status       the explicit `Suspended` or `Revoked` status, or `Null`
     */
    construct(
            Int          subjectId,
            String       name,
            Permission[] permissions = [],
            Time?        validFrom   = Null,
            Time?        validUntil  = Null,
            Status?      status      = Null,
            ) {
        this.subjectId   = subjectId;
        this.name        = name;
        this.permissions = permissions;
        this.validFrom   = validFrom ?: {@Inject Clock clock; return clock.now;};
        this.validUntil  = validUntil;
        this.status      = status == Active ? Null : status;
    }

    assert() {
        assert status == Null || status == Suspended || status == Revoked;
        assert validUntil? >= validFrom;
    }

    /**
     * Create a new `Subject` that is a clone of this `Subject`, but with the specific changes
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
     *
     * @return the new `Subject` copied from this `Subject` but with the specified changes
     */
    @Abstract Subject with(
            Int?          subjectId   = Null,
            String?       name        = Null,
            Permission[]? permissions = Null,
            Time?         validFrom   = Null,
            Time?         validUntil  = Null,
            Status?       status      = Null,
            Credential[]? credentials = Null,
            );

    // ----- properties ----------------------------------------------------------------------------

    /**
     * The identity of the `Subject`. For persistence purposes, this is considered the primary key.
     */
    Int subjectId;

    /**
     * The human-readable name or description provided for the `Subject`, which is useful for
     * auditing, debugging, etc. This value is not used as a "login name" or any other form of
     * credential. Every [Group] name must be non-blank and unique within its `Realm`.
     */
    String name;

    /**
     * Explicit permissions (including revocations) for this `Subject`, in order of precedence.
     */
    Permission[] permissions;

    /**
     * The point in time before which the `Subject` is considered to not yet exist and thus is
     * not valid. Entities must always have an explicit point before which they are not valid.
     */
    Time validFrom;

    /**
     * The optional point in time after which the `Subject` is considered to have expired and is no
     * longer valid.
     */
    Time? validUntil;

    /**
     * Null iff the `Subject` has not been explicitly suspended or revoked; otherwise this indicates
     * the explicit `Suspended` or `Revoked` status.
     */
    Status? status;

    /**
     * The [Credential]s that can be used to authenticate the `Subject`. [Group]s never have
     * `Credential`s.
     */
    @RO Credential[] credentials;

    // ----- API -----------------------------------------------------------------------------------

    /**
     * Subject statuses, roughly in order from worst to best:
     *
     * * `Revoked` - the `Subject` has been explicitly revoked
     * * `Suspended` - the `Subject` is explicitly suspended
     * * `NotYet` - the [validFrom] time for the `Subject` is in the future
     * * `Expired` - the [validUntil] time for the `Subject` is in the past
     * * `Active` - the `Subject` is valid and active
     */
    enum Status {Revoked, Suspended, NotYet, Expired, Active}

    /**
     * Determine if `this` Subject is valid at some specific time.
     *
     * Note that this does not incorporate any revocations that occurred after this Subject
     * object was instantiated.
     *
     * @param realm  the [Realm] that provided this `Subject`; passing `Null` will determine the
     *               result solely from this `Subject`, ignoring the status of any entities that this
     *               `Subject` delegates to
     * @param at     (optional) the time at which to determine if the `Subject` is valid; if
     *               nothing is specified, then the current time is used
     *
     * @return True iff `this` Subject is valid at the specified time
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
     * Determine if the specified [Permission] is allowed for this `Subject`.
     *
     * @param realm       the [Realm] that provided this `Subject`
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

        // this base `Subject` implementation is not aware of any other entities to delegate to;
        // sub-classes with entities to delegate to must override this method
        return covers(permission) == True;
    }

    /**
     * Determine if `this` Subject covers `that` Permission.
     *
     * @param that  a required Permission
     *
     * @return `True` if this `Subject`'s permissions explicitly allow the specified permission;
     *         `False` if this `Subject`'s permissions explicitly **dis**allow the specified
     *         permission; or `Null` if this `Subject`'s permissions do not account for the specified
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
     * Suspend this Subject.
     *
     * @return a copy of this Subject, but with a [status] of [Suspended]
     */
    Subject suspend() {
        return status == Suspended ? this : this.with(status=Suspended);
    }

    /**
     * If this Subject is suspended, then undo the suspension.
     *
     * @return a copy of this Subject, but without the [status] of [Suspended]
     */
    Subject unsuspend() {
        return status == Suspended ? this.with(status=Active) : this;
    }

    /**
     * Revoke this Subject.
     *
     * @return a copy of this Subject, but with a [status] of [Revoked]
     */
    Subject revoke() {
        return status == Revoked ? this : this.with(status=Revoked);
    }

    /**
     * Revoke a [Credential] from this `Subject`.
     *
     * @param credential  the [Credential] to revoke
     *
     * @return `True` iff the specified [Credential] was present on this `Subject`, and was revoked
     * @return (conditional) the resulting `Subject`
     */
    conditional Subject revokeCredential(Credential credential) {
        // replace any instances of that credential with a revoked credential
        Subject  subject  = this;
        Boolean  modified = False;
        String[] locators = credential.locators;
        Each: for (Credential each : credentials) {
            if (each.status != Revoked
                    && each.scheme == credential.scheme
                    && each.locators == locators) {
                subject  = subject.with(credentials=subject.credentials.replace(Each.count, credential.revoke()));
                modified = True;
            }
        }
        return modified, subject;
    }

    /**
     * Determine what [Credential]s have been added and removed comparing an older version of this
     * `Subject` to this `Subject`.
     *
     * @param that  an older version of this `Subject`
     *
     * @return added    an array of added [Credential]s
     * @return removed  an array of removed [Credential]s
     */
    (Credential[] added, Credential[] removed) credentialDiff(Subject that) {
        Credential[] newCreds = this.credentials;
        Credential[] oldCreds = that.credentials;
        if (newCreds.empty || oldCreds.empty) {
            return newCreds, oldCreds;
        }

        Credential[] added   = new Credential[];
        Credential[] removed = new Credential[];

        static VirtualHasher<Credential> hasher  = new VirtualHasher();
        HasherMap<Credential, Boolean>   overlap = new HasherMap(hasher, oldCreds.size);
        for (Credential credential : oldCreds) {
            overlap.put(credential, False);
        }
        for (Credential credential : newCreds) {
            if (overlap.contains(credential)) {
                overlap.put(credential, True);
            } else {
                added += credential;
            }
        }
        for (Credential credential : oldCreds) {
            if (overlap[credential] == True) {
                removed += credential;
            }
        }

        return added, removed;
    }
}
