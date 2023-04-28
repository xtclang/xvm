import oodb.DBCounter;
import oodb.DBMap;
import oodb.DBSchema;
import oodb.DBValue;
import oodb.NoTx;

/**
 * This is a schema that can easily be added to an application schema in order to "drag in" the
 * entire [webauth] database design. The logic in the `webauth` module will search through the
 * database to find this schema, and then will use this schema to store all of the data necessary
 * to implement the web authentication support (which also is part of this module).
 */
interface AuthSchema
        extends DBSchema
    {
    /**
     * The configuration for the authorization mechanism.
     */
    @RO DBValue<Configuration> config;

    /**
     * Internal user id generator.
     */
    @RO @NoTx DBCounter userId;

    /**
     * The users that can be authenticated.
     */
    @RO Users users;

    /**
     * User contact information.
     */
// TODO    @RO Contacts contacts;

    /**
     * The users that can be authenticated.
     */
    @RO UserHistory userHistory;

    /**
     * Internal role id generator.
     */
    @RO @NoTx DBCounter roleId;

    /**
     * The roles that can be associated with a user.
     */
    @RO Roles roles;
    }