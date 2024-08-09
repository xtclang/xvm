/**
 * A `Realm` represents persistent and most-immutable information, that can be created and modified
 * via TODO
 */
interface Realm {
    /**
     * A realm usually has a human-readable name, and sometimes this name is communicated to a
     * client as part of an authentication challenge.
     */
    @RO String name;

    // enumerate users
    // check validity of user

    conditional Principal loadPrincipal(Int id);
    void storePrincipal(Principal principal);

//    createGroup(String groupName, );
//    createUser(String  name,
//               Time?   validFrom  = Null,
//               Time?   validUntil = Null,
//               Boolean suspended  = False,
//              );

// do these belong?
//    conditional User findUser(String username, Date asOfDate);
//    conditional User disableUser(User user);
//    conditional User enableUser(User user);
//    conditional User expireUser(User user, Time? expires=Null);
}