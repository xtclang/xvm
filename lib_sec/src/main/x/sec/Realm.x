/**
 * A `Realm` represents persistent information about security [Subjects](Subject) and
 * [Entities](Entity), and in particular, about [Principals](Principal), [Groups](Group), and
 * [Entitlements](Entitlement).
 *
 * The operations of this interface are modeled as "CRUD" (Create/Read/Update/Delete) operations.
 * Being designed for persistence, the [Principal], [Group], and [Entitlement] types each carry a
 * unique id as part of their respective data structures, which simplifies the CRUD data model
 * substantially. Additionally, it is necessary to be able to locate `Principal` and `Entitlement`
 * by [Credential] information, which is supported by `Credential`s exposing zero or more two-part
 * foreign keys, each consisting of a "scheme" (e.g. plain text) and a "locator" (e.g. user name).
 *
 * As a CRUD interface, mutating operations can only modify (i.e. Create/Update/Delete) one `Entity`
 * at a time, and thus the updates are (from the caller's point of view) non-transactional. As a
 * result, a series of operations against the `Realm` that modify more than one `Entity` need to be
 * ordered carefully if they modify related data, since the `Realm` (or the data management system
 * underneath the `Realm`) may enforce referential integrity constraints. In other words: Write the
 * "depended-upon" things first, and delete the "depended-upon" things last.
 *
 * The operations API is designed to communicate all domain-level failures using the [Error] class.
 * For well known possible failures, specific sub-classes of Error are defined here, and documented
 * as possible return values, or in some cases, are reported via an optional callback. Because this
  interface abstracts away details such as I/O, networking,
 * persistent formats, etc., and because it is not designed to explicitly communicate each/all of
 * the specific lower level errors that can occur, a caller should assume the possibility of
 * exceptions being raised for non-domain (i.e. lower level) errors, as well as for obvious
 * fail-fast API assertions.
 */
interface Realm {
    /**
     * A `Realm` usually has a human-readable name that can be communicated to a client as part of
     * an authentication challenge.
     */
    @RO String name;

    /**
     * A `Realm` implementation may not support updates via the `Realm` interface, in which case it
     * should report that it is `readOnly=True`; such a `Realm` should return [ReadOnly] from any
     * mutating operation.
     */
    @RO Boolean readOnly.get() = False;

    // ----- domain errors -------------------------------------------------------------------------

    /**
     * `RealmException` is the base class for domain errors raised by the `Realm`.
     */
    static const RealmException(String? text = Null, Exception? cause = Null)
            extends Exception(text, cause);

    /**
     * Indicates that an operation attempted to write a [Subject] to the `Realm`, but the `Subject`
     * contained [Credential] information that was a duplicate of another `Subject`'s `Credential`
     * information.
     */
    static const DuplicateCredential(String scheme, String locator, Exception? cause = Null)
            extends RealmException($"scheme={scheme.quoted()}, locator={locator.quoted()}", cause);

    /**
     * Indicates that an operation attempted to write a [Subject] to the `Realm`, but the
     * [name](Subject.name) violated the Realm's naming rules. One example is that a [Group] is
     * expected to have a non-empty name.
     */
    static const InvalidName(String name, Exception? cause = Null)
            extends RealmException($"name={name.quoted()}", cause);

    /**
     * Indicates that an operation attempted to write a [Subject] to the `Realm`, but the
     * [name](Subject.name) was required to be unique, and it was not. One example is that each
     * [Group] is expected to have a unique name vis-a-vis other groups.
     */
    static const DuplicateName(String name, Exception? cause = Null)
            extends RealmException($"name={name.quoted()}", cause);

    /**
     * Indicates that an operation attempted detected an infinite loop in the specified `Group`.
     */
    static const GroupLoop(Int groupId, Exception? cause = Null)
            extends RealmException($"groupId={groupId}", cause);

    /**
     * Indicates that an operation attempted to operate on a [Subject] in the `Realm`, but it
     * depended on a `Principal` that does not exist in the `Realm`.
     */
    static const MissingPrincipal(Int principalId, Exception? cause = Null)
            extends RealmException($"principalId={principalId}", cause);

    /**
     * Indicates that an operation attempted to operate on a [Subject] in the `Realm`, but it
     * depended on a `Group` that does not exist in the `Realm`.
     */
    static const MissingGroup(Int groupId, Exception? cause = Null)
            extends RealmException($"groupId={groupId}", cause);

    /**
     * Indicates that an operation attempted to operate on a [Subject] in the `Realm`, but it
     * depended on an `Entitlement` that does not exist in the `Realm`.
     */
    static const MissingEntitlement(Int entitlementId, Exception? cause = Null)
            extends RealmException($"entitlementId={entitlementId}", cause);

    // ----- operations: Principals ----------------------------------------------------------------

    /**
     * Provide an `Iterator` over all [Principal] objects in the `Realm` that match the specified
     * filter.
     *
     * A caller that does not always exhaust the `Iterator` _should_ call [close()](Iterator.close)
     * (or use a `using` statement) to ensure that the `Iterator`'s resources are released.
     *
     * @param match  a function that evaluates each [Principal] for inclusion, and returns `True`
     *               for each `Principal` to include
     *
     * @return an [Iterator] of matching [Principal] objects
     */
    Iterator<Principal> findPrincipals(function Boolean(Principal) match);

    /**
     * Attempt to locate a [Principal] using a scheme-specific locator `String`.
     *
     * It is assumed that when a `Principal` is stored or modified, its active Credentials
     * automatically have their locator strings registered under their scheme name, to support
     * subsequent fast lookup using that information.
     *
     * @param scheme   the scheme name; see [Credential.scheme]
     * @param locator  a locator `String`; see [Credential.locators]
     *
     * @return `True` if the scheme+locator identifies a [Principal] in the `Realm`
     * @return (conditional) the [Principal] for the specified scheme+locator
     */
    conditional Principal findPrincipal(String scheme, String locator) {
        Principal[] principals = findPrincipals(p -> p.credentials.any(
                c -> c.scheme == scheme && c.locators.contains(locator))).toArray();
        switch (principals.size) {
        case 0:
            return False;
        case 1:
            return True, principals[0];
        default:
            Principal bestPrincipal = principals[0];
            Int       bestScore     = 0;
            for (Principal principal : principals) {
                Int score = 0;
                if (principal.calcStatus(this) == Active) {
                    score += 2;
                }

                for (Credential credential : principal.credentials) {
                    if (credential.scheme == scheme && credential.active
                            && credential.locators.contains(locator)) {
                        score += 1;
                        break;
                    }
                }

                if (score == 3) {
                    return True, principal;
                }

                if (score > bestScore) {
                    bestPrincipal = principal;
                    bestScore     = score;
                }
            }
            return True, bestPrincipal;
        }
    }

    /**
     * Create a new [Principal] in the `Realm`.
     *
     * @param principal  the [Principal] data to use to create a new `Principal` in the `Realm`; the
     *                  [Principal.principalId] of this value is ignored
     *
     * @return the newly created [Principal], with an assigned `principalId`
     *
     * @throws ReadOnly             if the `Realm` is [readOnly]
     * @throws DuplicateCredential  if the [Principal] has a non-unique [Credential] locator
     * @throws InvalidName          if the [Principal] name violates the `Realm`'s naming rules
     * @throws DuplicateName        if the [Principal] name is required to be unique, and is not
     * @throws GroupLoop            if a loop is detected in the [Group] hierarchy
     * @throws MissingGroup         if a [Group] identity is referenced, but does not exist
     */
    Principal createPrincipal(Principal principal);

    /**
     * Attempt to locate a [Principal] using its identity.
     *
     * @param id  the [Principal] identity
     *
     * @return `True` if a [Principal] with the specified identity exists in the `Realm`
     * @return (conditional) the [Principal] for the specified identity
     */
    conditional Principal readPrincipal(Int id);

    /**
     * Store the provided [Principal].
     *
     * @param principal  the [Principal] to store
     *
     * @return the [Principal] as it now exists in the `Realm`; in theory the returned `Principal`
     *         could differ from the passed in `Principal`
     *
     * @throws ReadOnly             if the `Realm` is [readOnly]
     * @throws DuplicateCredential  if the [Principal] has a non-unique [Credential] locator
     * @throws InvalidName          if the [Principal] name violates the `Realm`'s naming rules
     * @throws DuplicateName        if the [Principal] name is required to be unique, and is not
     * @throws GroupLoop            if a loop is detected in the [Group] hierarchy
     * @throws MissingGroup         if a [Group] identity is referenced, but does not exist
     * @throws MissingPrincipal     if the [Principal] to update does not exist in the `Realm`
     */
    Principal updatePrincipal(Principal principal);

    /**
     * Delete the specified [Principal] from the `Realm`. The Realm is responsible for deleting any
     * [Entitlement]s related to the `Principal`. This operation should be used with care, as it can
     * (in theory) create dangling references if any other `Subject`s or other data structures
     * reference the specified `Principal`; it is generally preferred to _revoke_ `Principal`s, and
     * leave them in the `Realm` as historical records.
     *
     * @param principal  the [Principal] (or identity thereof) to delete
     *
     * @return `True` if the [Principal] existed and has been deleted; `False` if the specified
     *         `Principal` did not exist in the `Realm`
     *
     * @throws ReadOnly   if the `Realm` is [readOnly]
     */
    Boolean deletePrincipal(Int|Principal principal);

    // ----- operations: Groups ----------------------------------------------------------------

    /**
     * Provide an `Iterator` over all [Group] objects in the `Realm` that match the specified
     * filter.
     *
     * A caller that does not always exhaust the `Iterator` _should_ call [close()](Iterator.close)
     * (or use a `using` statement) to ensure that the `Iterator`'s resources are released.
     *
     * @param match  a function that evaluates each [Group] for inclusion, and returns `True`
     *               for each `Group` to include
     *
     * @return an [Iterator] of matching [Group] objects
     */
    Iterator<Group> findGroups(function Boolean(Group) match);

    /**
     * Create a new [Group] in the `Realm`.
     *
     * @param group  the [Group] data to use to create a new `Group` in the `Realm`; the
     *                  [Group.groupId] of this value is ignored
     *
     * @return the newly created [Group], with an assigned `groupId`
     *
     * @throws ReadOnly             if the `Realm` is [readOnly]
     * @throws DuplicateCredential  if the [Group] has a non-unique [Credential] locator
     * @throws InvalidName          if the [Group] name violates the `Realm`'s naming rules
     * @throws DuplicateName        if the [Group] name is not unique
     * @throws GroupLoop            if a loop is detected in the [Group] hierarchy
     * @throws MissingSubject       if a [Subject] identity is referenced, but does not exist
     */
    Group createGroup(Group group);

    /**
     * Attempt to locate a [Group] using its identity.
     *
     * @param id  the [Group] identity
     *
     * @return `True` if a [Group] with the specified identity exists in the `Realm`
     * @return (conditional) the [Group] for the specified identity
     */
    conditional Group readGroup(Int id);

    /**
     * Store the provided [Group].
     *
     * @param group  the [Group] to store
     *
     * @return the [Group] as it now exists in the `Realm`; in theory the returned `Group`
     *         could differ from the passed in `Group`
     *
     * @throws ReadOnly             if the `Realm` is [readOnly]
     * @throws DuplicateCredential  if the [Group] has a non-unique [Credential] locator
     * @throws InvalidName          if the [Group] name violates the `Realm`'s naming rules
     * @throws DuplicateName        if the [Group] name is required to be unique, and is not
     * @throws GroupLoop            if a loop is detected in the [Group] hierarchy
     * @throws MissingSubject       if a [Subject] identity is referenced, but does not exist, or if
     *                              the passed [Group] to update does not exist
     */
    Group updateGroup(Group group);

    /**
     * Delete the specified [Group] from the `Realm`. This operation should be used with care,
     * as it can (in theory) create dangling references if any other `Subject`s or other data
     * structures reference the specified `Group`; it is generally preferred to _revoke_
     * `Group`s, and leave them in the `Realm` as historical records.
     *
     * @param group  the [Group] (or identity thereof) to delete
     *
     * @return `True` if the [Group] existed and has been deleted; `False` if the specified
     *         `Group` did not exist in the `Realm`
     *
     * @throws ReadOnly        if the `Realm` is [readOnly]
     * @throws MissingSubject  if the [Group] cannot be deleted because it is still referenced
     *                         by some other [Subject] in the `Realm`; a `Realm` implementation may
     *                         choose to delete all references to the `Group` instead of throwing
     */
    Boolean deleteGroup(Int|Group group);

    // ----- operations: Entitlements --------------------------------------------------------------

    /**
     * Provide an `Iterator` over all [Entitlement] objects in the `Realm` that match the specified
     * filter.
     *
     * A caller that does not always exhaust the `Iterator` _should_ call [close()](Iterator.close)
     * (or use a `using` statement) to ensure that the `Iterator`'s resources are released.
     *
     * @param match  a function that evaluates each [Entitlement] for inclusion, and returns `True`
     *               for each `Entitlement` to include
     *
     * @return an [Iterator] of matching [Entitlement] objects
     */
    Iterator<Entitlement> findEntitlements(function Boolean(Entitlement) match);

    /**
     * Attempt to locate a [Entitlement] using a scheme-specific locator `String`.
     *
     * It is assumed that when a `Entitlement` is stored or modified, its active Credentials
     * automatically have their locator strings registered under their scheme name, to support
     * subsequent fast lookup using that information.
     *
     * @param scheme   the scheme name; see [Credential.scheme]
     * @param locator  a locator `String`; see [Credential.locators]
     *
     * @return `True` if the scheme+locator identifies a [Entitlement] in the `Realm`
     * @return (conditional) the [Entitlement] for the specified scheme+locator
     */
    conditional Entitlement findEntitlement(String scheme, String locator) {
        Entitlement[] entitlements = findEntitlements(p -> p.credentials.any(
                c -> c.scheme == scheme && c.locators.contains(locator))).toArray();
        switch (entitlements.size) {
        case 0:
            return False;
        case 1:
            return True, entitlements[0];
        default:
            Entitlement bestEntitlement = entitlements[0];
            Int       bestScore     = 0;
            for (Entitlement entitlement : entitlements) {
                Int score = 0;
                if (entitlement.calcStatus(this) == Active) {
                    score += 2;
                }

                for (Credential credential : entitlement.credentials) {
                    if (credential.scheme == scheme && credential.active
                            && credential.locators.contains(locator)) {
                        score += 1;
                        break;
                    }
                }

                if (score == 3) {
                    return True, entitlement;
                }

                if (score > bestScore) {
                    bestEntitlement = entitlement;
                    bestScore     = score;
                }
            }
            return True, bestEntitlement;
        }
    }

    /**
     * Create a new [Entitlement] in the `Realm`.
     *
     * @param entitlement  the [Entitlement] data to use to create a new `Entitlement` in the `Realm`; the
     *                  [Entitlement.entitlementId] of this value is ignored
     *
     * @return the newly created [Entitlement], with an assigned `entitlementId`
     *
     * @throws ReadOnly             if the `Realm` is [readOnly]
     * @throws DuplicateCredential  if the [Entitlement] has a non-unique [Credential] locator
     * @throws InvalidName          if the [Entitlement] name violates the `Realm`'s naming rules
     * @throws DuplicateName        if the [Entitlement] name is required to be unique, and is not
     * @throws GroupLoop            if a loop is detected in the [Group] hierarchy
     * @throws MissingSubject       if a [Subject] identity is referenced, but does not exist
     */
    Entitlement createEntitlement(Entitlement entitlement);

    /**
     * Attempt to locate a [Entitlement] using its identity.
     *
     * @param id  the [Entitlement] identity
     *
     * @return `True` if a [Entitlement] with the specified identity exists in the `Realm`
     * @return (conditional) the [Entitlement] for the specified identity
     */
    conditional Entitlement readEntitlement(Int id);

    /**
     * Store the provided [Entitlement].
     *
     * @param entitlement  the [Entitlement] to store
     *
     * @return the [Entitlement] as it now exists in the `Realm`; in theory the returned `Entitlement`
     *         could differ from the passed in `Entitlement`
     *
     * @throws ReadOnly             if the `Realm` is [readOnly]
     * @throws DuplicateCredential  if the [Entitlement] has a non-unique [Credential] locator
     * @throws InvalidName          if the [Entitlement] name violates the `Realm`'s naming rules
     * @throws DuplicateName        if the [Entitlement] name is required to be unique, and is not
     * @throws GroupLoop            if a loop is detected in the [Group] hierarchy
     * @throws MissingSubject       if a [Subject] identity is referenced, but does not exist, or if
     *                              the passed [Entitlement] to update does not exist
     */
    Entitlement updateEntitlement(Entitlement entitlement);

    /**
     * Delete the specified [Entitlement] from the `Realm`. This operation should be used with care,
     * as it can (in theory) create dangling references if any other `Subject`s or other data
     * structures reference the specified `Entitlement`; it is generally preferred to _revoke_
     * `Entitlement`s, and leave them in the `Realm` as historical records.
     *
     * @param entitlement  the [Entitlement] (or identity thereof) to delete
     *
     * @return `True` if the [Entitlement] existed and has been deleted; `False` if the specified
     *         `Entitlement` did not exist in the `Realm`
     *
     * @throws ReadOnly        if the `Realm` is [readOnly]
     * @throws MissingSubject  if the [Entitlement] cannot be deleted because it is still referenced
     *                         by some other [Subject] in the `Realm`
     */
    Boolean deleteEntitlement(Int|Entitlement entitlement);
}