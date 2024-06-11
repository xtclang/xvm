import sec.Credential;
import sec.Group;
import sec.Permission;
import sec.PlainTextCredential;

import web.security.DigestCredential;

/**
 * A FixedRealm is a realm implementation with a fixed number of named users, each with a fixed
 * password.
 */
const FixedRealm(String name, Principal[] principals, Group[] groups = [],
                 Entitlement[] entitlements = [])
        implements Realm {

    /**
     * Construct a FixedRealm for a single user/password. The specified user is granted all
     * permissions.
     */
    construct(String realmName, String userName, String password) {
        name       = realmName;
        principals = [new Principal(0, userName,
                        permissions=[AllowAll],
                        credentials=[new PlainTextCredential(userName, password),
                                     new DigestCredential(name, userName, password)],
                        )];
    }

    // ----- properties ----------------------------------------------------------------------------

    @Override
    @RO Boolean readOnly.get() = True;

    /**
     * An index from scheme/locator to Principal.
     */
    @Lazy Map<String, Principal> principalIndex.calc() {
        HashMap<String, Principal> index = new HashMap();
        for (Principal principal : principals) {
            for (Credential credential : principal.credentials) {
                String scheme = credential.scheme;
                for (String locator : credential.locators) {
                    index[$"{scheme}:{locator}"] = principal;
                }
            }
        }
        return index.freeze(inPlace=True);
    }

    /**
     * An index from scheme/locator to Entitlement.
     */
    @Lazy Map<String, Entitlement> entitlementIndex.calc() {
        HashMap<String, Entitlement> index = new HashMap();
        for (Entitlement entitlement : entitlements) {
            for (Credential credential : entitlement.credentials) {
                String scheme = credential.scheme;
                for (String locator : credential.locators) {
                    index[$"{scheme}:{locator}"] = entitlement;
                }
            }
        }
        return index.freeze(inPlace=True);
    }

    // ----- operations: Principals ----------------------------------------------------------------

    @Override
    Iterator<Principal> findPrincipals(function Boolean(Principal) match) {
        return principals.filter(match).iterator();
    }

    @Override
    conditional Principal findPrincipal(String scheme, String locator) {
        return principalIndex.get($"{scheme}:{locator}");
    }

    @Override
    Principal createPrincipal(Principal principal) = throw new ReadOnly();

    @Override
    conditional Principal readPrincipal(Int id) {
        if (0 <= id < principals.size) {
            return True, principals[id];
        }
        return False;
    }

    @Override
    Principal updatePrincipal(Principal principal) = throw new ReadOnly();

    @Override
    Boolean deletePrincipal(Int|Principal principal) = throw new ReadOnly();

    // ----- operations: Groups ----------------------------------------------------------------

    @Override
    Iterator<Group> findGroups(function Boolean(Group) match) {
        return groups.filter(match).iterator();
    }

    @Override
    Group createGroup(Group group) = throw new ReadOnly();

    @Override
    conditional Group readGroup(Int id) {
        if (0 <= id < groups.size) {
            return True, groups[id];
        }
        return False;
    }

    @Override
    Group updateGroup(Group group) = throw new ReadOnly();

    @Override
    Boolean deleteGroup(Int|Group group) = throw new ReadOnly();

    // ----- operations: Entitlements --------------------------------------------------------------

    @Override
    Iterator<Entitlement> findEntitlements(function Boolean(Entitlement) match) {
        return entitlements.filter(match).iterator();
    }


    @Override
    conditional Entitlement findEntitlement(String scheme, String locator) {
        return entitlementIndex.get($"{scheme}:{locator}");
    }

    @Override
    Entitlement createEntitlement(Entitlement entitlement) = throw new ReadOnly();

    @Override
    conditional Entitlement readEntitlement(Int id) {
        if (0 <= id < entitlements.size) {
            return True, entitlements[id];
        }
        return False;
    }

    @Override
    Entitlement updateEntitlement(Entitlement entitlement) = throw new ReadOnly();

    @Override
    Boolean deleteEntitlement(Int|Entitlement entitlement) = throw new ReadOnly();
}