/**
 * An injectable Configuration for the db-based web authentication functionality.
 *
 * @param initUserPass   an *initial* mapping of user names to passwords; it is expected that this
 *                       will include only an administrator login, and the password will be changed
 *                       immediately after the database is configured
 * @param configured     (optional) pass True to indicate that the Configuration has already been
 *                       successfully applied to the database
 */
const Configuration(Map<String,String>   initUserPass,
                    // TODO other config for 2FA, email verification, password requirements, etc.
                    Boolean              configured    = True,
                   )
        default(new Configuration([], configured=False)) {

    /**
     * Create a copy of this Configuration, with the specified properties modified.
     *
     * @param initUserPass   (optional) an initial mapping of user names to passwords
     * @param configured     (optional) pass True to indicate that the Configuration has been
     *                       successfully applied to the database
     *
     * @return a new Configuration with the specified changes
     */
    Configuration with(Map<String,String>?   initUserPass  = Null,
                       Boolean?              configured    = Null,
                      ) {
        return new Configuration(initUserPass  = initUserPass  ?: this.initUserPass,
                                 configured    = configured    ?: this.configured,
                                );
    }
}