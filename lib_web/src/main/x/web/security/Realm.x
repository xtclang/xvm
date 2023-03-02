/**
 * A Realm is a named security domain, and an implementation of this interface is responsible
 * for the validation of user credentials, specifically the user name and password.
 */
interface Realm
    {
    /**
     * The name of the Realm is intended to be human-readable and short-yet-descriptive.
     */
    @RO String name;

    /**
     * Validate the passed user name and password.
     *
     * @param user      the user's identity, as provided by the client
     * @param password  the user's password, as provided by the client
     *
     * @return True iff the user identity is verified to exist, and the provided password is
     *         correct for that user
     */
    Boolean validate(String user, String password);
    }
