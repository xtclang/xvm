/**
 * An injectable Configuration for the db-based web authentication functionality.
 *
 * @param initUserPass   an *initial* mapping of user names to passwords; it is expected that this
 *                       will include only an administrator login, and the password will be changed
 *                       immediately after the database is configured
 * @param initRoleUsers  an *initial* mapping of role names to an array of user names
 * @param useMD5         allow the use of MD5 hashing
 * @param useSHA256      allow the use of SHA-256 hashing
 * @param useSHA512_256  allow the use of SHA-512-256 hashing
 * @param configured     (optional) pass True to indicate that the Configuration has already been
 *                       successfully applied to the database
 */
const Configuration
        (
        Map<String,String>   initUserPass,
        Map<String,String[]> initRoleUsers = [],
        Boolean              useMD5        = True,
        Boolean              useSHA256     = True,
        Boolean              useSHA512_256 = True,
        // TODO other config for 2FA, email verification, password requirements, etc.
        Boolean              configured    = True,
        )
        default(new Configuration([], configured=False)) {

    assert() {
        assert useMD5 | useSHA256 | useSHA512_256 as "At least one hashing algorithm is required";
    }

    /**
     * Create a copy of this Configuration, with the specified properties modified.
     *
     * @param initUserPass   (optional) an initial mapping of user names to passwords
     * @param useMD5         (optional) allow the use of MD5 hashing
     * @param useSHA256      (optional) allow the use of SHA-256 hashing
     * @param useSHA512_256  (optional) allow the use of SHA-512-256 hashing
     * @param configured     (optional) pass True to indicate that the Configuration has been
     *                       successfully applied to the database
     *
     * @return a new Configuration with the specified changes
     */
    Configuration with
            (
            Map<String,String>?   initUserPass  = Null,
            Map<String,String[]>? initRoleUsers = Null,
            Boolean?              useMD5        = Null,
            Boolean?              useSHA256     = Null,
            Boolean?              useSHA512_256 = Null,
            Boolean?              configured    = Null,
            ) {
        return new Configuration(initUserPass  = initUserPass  ?: this.initUserPass,
                                 initRoleUsers = initRoleUsers ?: this.initRoleUsers,
                                 useMD5        = useMD5        ?: this.useMD5,
                                 useSHA256     = useSHA256     ?: this.useSHA256,
                                 useSHA512_256 = useSHA512_256 ?: this.useSHA512_256,
                                 configured    = configured    ?: this.configured,
                                );
    }
}