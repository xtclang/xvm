/**
 * A FixedRealm is a realm implementation with a fixed number of named users, each with a fixed
 * password.
 *
 * TODO store hashes (a la "A1" in digest spec) in lieu of passwords
 */
const FixedRealm
        implements Realm
    {
    construct(String name, immutable Map<String, String> userPwds)
        {
        this.name     = name;
        this.userPwds = userPwds;
        }

    /**
     * User and password information.
     */
    protected/private immutable Map<String, String> userPwds;

    @Override
    Boolean validate(String user, String password)
        {
        return userPwds[user] == password;
        }
    }
