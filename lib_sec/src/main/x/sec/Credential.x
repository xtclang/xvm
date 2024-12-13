/**
 * A `Credential` represents a means of authentication.
 */
@Abstract const Credential {

    typedef Subject.Status as Status;

    // ----- construction --------------------------------------------------------------------------

    /**
     * Construct a `Credential`.
     *
     * @param scheme      uniquely identifies the form of authentication (and potentially additional
     *                    specifics) that this credential is intended to be used with; for example,
     *                    "password" might indicate a plain text password, while "digest:SHA-256"
     *                    might indicate a digest of a password using a specific hash function
     * @param validFrom   the point in time before which the `Credential` does not exist; defaults
     *                    to the current time
     * @param validUntil  the point in time after which the `Credential` is automatically expired
     * @param status      the explicit `Suspended` or `Revoked` status, or `Null`
     */
    construct(
            String  scheme,
            Time?   validFrom   = Null,
            Time?   validUntil  = Null,
            Status? status      = Null,
            ) {
        this.scheme     = scheme;
        this.validFrom  = validFrom ?: {@Inject Clock clock; return clock.now;};
        this.validUntil = validUntil;
        this.status     = status == Active ? Null : status;
    }

    assert() {
        assert status == Null || status == Suspended || status == Revoked;
        assert validUntil? >= validFrom;
    }

    /**
     * Create a copy of this `Credential`, but with specific attributes modified.
     *
     * @param scheme      the new `scheme` name, or pass `Null` to leave unchanged
     * @param validFrom   the new `validFrom` value to use, or `Null` to leave unchanged
     * @param validUntil  the new `validUntil` value to use, or `Null` to leave unchanged
     * @param status      the new `status` value to use, or `Null` to leave unchanged; the only
     *                    legal [Status] values to pass are `Active`, `Suspended`, and `Revoked`;
     *                    passing `Active` will result in the [status] of `Null`
     */
    @Abstract Credential with(
            String? scheme     = Null,
            Time?   validFrom  = Null,
            Time?   validUntil = Null,
            Status? status     = Null,
            );

    /**
     * The form of authentication that this credential is intended to be used with, which may
     * contain specific parameters of that authentication mechanism. For example, "pt" may indicate
     * a plain text password, while "ak" may indicate an API key.
     */
    String scheme;

    /**
     * A `Credential` produces one or more locator strings that are specific to the scheme of the
     * `Credential`, and are enforced as a unique domain composed of the combination of [scheme]
     * name and locator string. Each locator will be usable within a [Realm] to perform a fast find
     * on an `Entity` that has an active (not [revoked]) `Credential` which produced that locator
     * string.
     */
    @RO String[] locators;

    /**
     * The point in time before which the `Credential` is considered to not yet exist and thus is
     * not valid. Credentials must always have an explicit point before which they are not valid.
     */
    Time validFrom;

    /**
     * The optional point in time after which the `Credential` is considered to have expired and is
     * no longer valid.
     */
    Time? validUntil;

    /**
     * Null iff the `Credential` has not been explicitly suspended or revoked; otherwise this
     * indicates the explicit `Suspended` or `Revoked` status.
     */
    Status? status;

    /**
     * Virtual property: Is the `Credential` currently `Active`?
     */
    @RO Boolean active.get() = calcStatus() == Active;

    /**
     * Determine if this `Credential` is valid at some specific time.
     *
     * @param at  (optional) the time at which to determine if the `Credential` is valid; if nothing
     *            is specified, then the current time is used
     *
     * @return the [Status] of `NotYet`, `Expired`, or `Active` for this `Credential`
     */
    Status calcStatus(Time? at = Null) {
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
     * Suspend this `Credential`.
     *
     * @return a copy of this Subject, but with a [status] of [Suspended]
     */
    Credential suspend() {
        return status == Suspended ? this : this.with(status=Suspended);
    }

    /**
     * If this `Credential` is suspended, then undo the suspension.
     *
     * @return a copy of this Subject, but without the [status] of [Suspended]
     */
    Credential unsuspend() {
        return status == Suspended ? this.with(status=Active) : this;
    }

    /**
     * Revoke this `Credential`.
     *
     * @return a copy of this Subject, but with a [status] of [Revoked]
     */
    Credential revoke() {
        return status == Revoked ? this : this.with(status=Revoked);
    }
}